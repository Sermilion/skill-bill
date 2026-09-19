package skillbill.infrastructure.sqlite.goalrunner.outcome
import skillbill.contracts.JsonCodec
import skillbill.goalrunner.GoalObservabilityArtifacts
import skillbill.goalrunner.WORKER_SUBTASK_REQUEST_OUTCOMES_ARTIFACT_KEY
import skillbill.goalrunner.WORKER_SUBTASK_REQUEST_OUTCOME_LIMIT
import skillbill.goalrunner.backwardEdgeCountsFromLedger
import skillbill.goalrunner.declaredProgressEventFrom
import skillbill.goalrunner.decodeDeclaredGoalProgressEvent
import skillbill.goalrunner.model.GOAL_ATTEMPT_LEDGER_ARTIFACT_KEY
import skillbill.goalrunner.model.GOAL_ATTEMPT_LEDGER_LIMIT
import skillbill.goalrunner.model.GoalObservabilityRuntimeEventInput
import skillbill.goalrunner.model.GoalRunnerAttemptLedgerSummary
import skillbill.goalrunner.model.GoalRunnerObservabilityRecordRequest
import skillbill.goalrunner.model.GoalRunnerWorkerSubtaskRequestOutcome
import skillbill.goalrunner.progressEventFrom
import skillbill.goalrunner.summarizeAttemptLedgerFromEntries
import skillbill.goalrunner.summary
import skillbill.goalrunner.toPersistenceWire
import skillbill.goalrunner.toProgressEvent
import skillbill.infrastructure.sqlite.decomposition.decodeArtifacts
import skillbill.infrastructure.sqlite.featuretask.artifact.decodePhaseRecords
import skillbill.infrastructure.sqlite.goalrunner.control.goalContinuation
import skillbill.infrastructure.sqlite.goalrunner.control.maxHistorySequence
import skillbill.infrastructure.sqlite.goalrunner.control.workflowFamilyFor
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.goalrunner.persistence.model.HistoryArtifactAppend
import skillbill.ports.goalrunner.runner.GoalRunnerAttemptLedgerStore
import skillbill.ports.goalrunner.runner.GoalRunnerWorkflowLedgerWriteStore
import skillbill.ports.goalrunner.runner.GoalRunnerWorkflowProgressStore
import skillbill.ports.goalrunner.runner.model.GoalRunnerAttemptLedgerRecordRequest
import skillbill.ports.goalrunner.runner.model.GoalRunnerLedgerSequenceWatermarks
import skillbill.ports.goalrunner.runner.model.GoalRunnerProgressEventRecordRequest
import skillbill.ports.goalrunner.runner.model.GoalRunnerWorkflowProgress
import skillbill.ports.workflow.get
import skillbill.ports.workflow.list
import skillbill.ports.workflow.model.WorkflowFamily
import skillbill.ports.workflow.save
import skillbill.workflow.engine.WorkflowEngine
import skillbill.workflow.engine.decodeWorkflowSteps
import skillbill.workflow.engine.model.WorkflowArtifactPatch
import skillbill.workflow.engine.model.WorkflowUpdateInput
import skillbill.workflow.engine.progressToken
import skillbill.workflow.goal.GoalObservabilityEventValidator
import skillbill.workflow.goal.GoalProgressEventValidator
import skillbill.workflow.goal.model.GOAL_OBSERVABILITY_LATEST_EVENT_ARTIFACT_KEY
import skillbill.workflow.goal.model.GOAL_PROGRESS_HISTORY_LIMIT
import skillbill.workflow.goal.model.GOAL_PROGRESS_LATEST_EVENT_ARTIFACT_KEY
import skillbill.workflow.goal.model.GOAL_PROGRESS_RUN_HISTORY_ARTIFACT_KEY
import skillbill.workflow.goal.model.GoalProgressEvent
import skillbill.workflow.goal.model.appendBoundedHistoryBySequence
import skillbill.workflow.goal.model.goalObservabilityLatestEventFromArtifacts
import skillbill.workflow.model.WorkflowStatus
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.taskruntime.artifact.validateGoalProgressEvent
private val PROGRESS_POLL_ARTIFACT_KEYS = setOf(
  "progress_event",
  GOAL_PROGRESS_LATEST_EVENT_ARTIFACT_KEY,
  GOAL_OBSERVABILITY_LATEST_EVENT_ARTIFACT_KEY,
)

internal class WorkflowGoalRunnerProgressRecording(
  private val database: DatabaseSessionFactory,
  private val engine: WorkflowEngine,
  private val goalObservabilityEventValidator: GoalObservabilityEventValidator,
  private val goalProgressEventValidator: GoalProgressEventValidator,
) : GoalRunnerWorkflowProgressStore,
  GoalRunnerWorkflowLedgerWriteStore,
  GoalRunnerAttemptLedgerStore {
  override fun progress(workflowId: String): GoalRunnerWorkflowProgress? = database.read { unitOfWork ->
    val family = workflowFamilyFor(unitOfWork.workflowStates, workflowId) ?: return@read null
    val record = family.get(unitOfWork.workflowStates, workflowId) ?: return@read null
    engine.snapshotView(family.definition, record)
    val steps = decodeWorkflowSteps(record.stepsJson)
    val artifacts = sparseArtifactKeys(record.artifactsJson, PROGRESS_POLL_ARTIFACT_KEYS)
    val finishCompleted = steps.any {
        step ->
      step.stepId == "pr" && step.status == WorkflowStepStatus.COMPLETED
    }
    val currentStep = if (record.workflowStatus == WorkflowStatus.COMPLETED || finishCompleted) {
      "pr"
    } else {
      record.currentStepId
    }
    val progressEvent = progressEventFrom(artifacts)
    val declaredProgressEvent = declaredProgressEventFrom(artifacts)
    val observabilityEvent = runCatching {
      goalObservabilityLatestEventFromArtifacts(artifacts, goalObservabilityEventValidator)
    }.getOrNull()
    GoalRunnerWorkflowProgress(
      workflowId = record.workflowId,
      workflowStatus = record.workflowStatus,
      currentStepId = currentStep,
      progressToken = record.progressToken(),
      latestDurableProgressEvent = progressEvent,
      latestGoalObservabilityEvent = observabilityEvent?.toProgressEvent(),
      latestDeclaredProgressEvent = declaredProgressEvent,
      latestLivenessSignal = observabilityEvent?.compactLivenessSummary()
        ?: progressEvent?.summary()
        ?: "workflow_status=${record.workflowStatus}; step=$currentStep",
      lastSnapshotUpdatedAt = record.updatedAt,
    )
  }

  override fun recordObservabilityEvent(request: GoalRunnerObservabilityRecordRequest): Boolean =
    database.transaction { unitOfWork ->
      val family = workflowFamilyFor(unitOfWork.workflowStates, request.workflowId)
        ?: return@transaction false
      val record = family.get(unitOfWork.workflowStates, request.workflowId)
        ?: return@transaction false
      val artifacts = decodeArtifacts(record.artifactsJson)
      val observabilityPatch = GoalObservabilityArtifacts.patchForRuntimeEvent(
        input = GoalObservabilityRuntimeEventInput(
          artifacts = artifacts,
          request = request,
        ),
        validator = goalObservabilityEventValidator,
      )
      val updated = engine.updateRecord(
        family.definition,
        record,
        WorkflowUpdateInput(
          workflowStatus = record.workflowStatus,
          currentStepId = record.currentStepId,
          stepUpdates = null,
          artifactsPatch = JsonCodec.anyToStringAnyMap(observabilityPatch)?.let(WorkflowArtifactPatch::from),
          sessionId = record.sessionId.orEmpty(),
        ),
      )
      family.save(unitOfWork.workflowStates, updated)
      true
    }

  override fun recordProgressEvent(request: GoalRunnerProgressEventRecordRequest): Boolean {
    val entryMap = request.event.toPersistenceWire()
    goalProgressEventValidator.validateGoalProgressEvent(entryMap, GOAL_PROGRESS_LATEST_EVENT_ARTIFACT_KEY)
    return appendHistoryArtifact(
      HistoryArtifactAppend(
        workflowId = request.workflowId,
        latestKey = GOAL_PROGRESS_LATEST_EVENT_ARTIFACT_KEY,
        historyKey = GOAL_PROGRESS_RUN_HISTORY_ARTIFACT_KEY,
        retentionLimit = GOAL_PROGRESS_HISTORY_LIMIT,
        entryMap = entryMap,
      ),
    )
  }

  override fun recordAttemptLedgerEntry(request: GoalRunnerAttemptLedgerRecordRequest): Boolean = appendHistoryArtifact(
    HistoryArtifactAppend(
      workflowId = request.workflowId,
      latestKey = null,
      historyKey = GOAL_ATTEMPT_LEDGER_ARTIFACT_KEY,
      retentionLimit = GOAL_ATTEMPT_LEDGER_LIMIT,
      entryMap = request.entry.toPersistenceWire(),
    ),
  )

  override fun progressEvents(workflowId: String): List<GoalProgressEvent> = database.transaction { unitOfWork ->
    val family = workflowFamilyFor(unitOfWork.workflowStates, workflowId)
      ?: return@transaction emptyList()
    val record = family.get(unitOfWork.workflowStates, workflowId)
      ?: return@transaction emptyList()
    (decodeArtifacts(record.artifactsJson)[GOAL_PROGRESS_RUN_HISTORY_ARTIFACT_KEY] as? List<*>)
      .orEmpty()
      .mapNotNull { item -> item as? Map<*, *> }
      .mapNotNull { item -> JsonCodec.anyToStringAnyMap(item) }
      .map { map -> map.decodeDeclaredGoalProgressEvent(GOAL_PROGRESS_RUN_HISTORY_ARTIFACT_KEY) }
  }

  override fun recordWorkerSubtaskRequestOutcomes(
    workflowId: String,
    outcomes: List<GoalRunnerWorkerSubtaskRequestOutcome>,
  ): Boolean = database.transaction { unitOfWork ->
    val family = workflowFamilyFor(unitOfWork.workflowStates, workflowId)
      ?: return@transaction false
    val record = family.get(unitOfWork.workflowStates, workflowId)
      ?: return@transaction false
    val artifacts = decodeArtifacts(record.artifactsJson)
    val existing = (artifacts[WORKER_SUBTASK_REQUEST_OUTCOMES_ARTIFACT_KEY] as? List<*>)
      .orEmpty()
      .mapNotNull { item -> item as? Map<*, *> }
      .map { item -> JsonCodec.anyToStringAnyMap(item) }
    val updatedOutcomes = (existing + outcomes.map(GoalRunnerWorkerSubtaskRequestOutcome::toPersistenceWire))
      .takeLast(WORKER_SUBTASK_REQUEST_OUTCOME_LIMIT)
    val updated = engine.updateRecord(
      family.definition,
      record,
      WorkflowUpdateInput(
        workflowStatus = record.workflowStatus,
        currentStepId = record.currentStepId,
        stepUpdates = null,
        artifactsPatch = WorkflowArtifactPatch.from(
          mapOf(WORKER_SUBTASK_REQUEST_OUTCOMES_ARTIFACT_KEY to updatedOutcomes),
        ),
        sessionId = record.sessionId.orEmpty(),
      ),
    )
    family.save(unitOfWork.workflowStates, updated)
    true
  }

  override fun ledgerSequenceWatermarks(issueKey: String): GoalRunnerLedgerSequenceWatermarks =
    database.read { unitOfWork ->
      val normalizedIssueKey = issueKey.trim()
      var maxLedger: Int? = null
      var maxProgress: Int? = null
      val backwardEdgeCounts = mutableMapOf<String, Int>()
      listOf(WorkflowFamily.TASK_RUNTIME).forEach { family ->
        family.list(unitOfWork.workflowStates, Int.MAX_VALUE).forEach { snapshot ->
          val artifacts = decodeArtifacts(snapshot.artifactsJson)
          if (goalContinuation(artifacts)?.issueKey != normalizedIssueKey) {
            return@forEach
          }
          maxLedger = maxHistorySequence(artifacts, GOAL_ATTEMPT_LEDGER_ARTIFACT_KEY, maxLedger)
          maxProgress = maxHistorySequence(artifacts, GOAL_PROGRESS_RUN_HISTORY_ARTIFACT_KEY, maxProgress)
          backwardEdgeCountsFromLedger(artifacts).forEach { (key, count) ->
            backwardEdgeCounts.merge(key, count, ::maxOf)
          }
        }
      }
      GoalRunnerLedgerSequenceWatermarks(
        maxLedgerSequence = maxLedger,
        maxProgressSequence = maxProgress,
        backwardEdgeCounts = backwardEdgeCounts,
      )
    }

  override fun childWorkflowLoopIterations(workflowId: String): Map<String, Int> = database.read { unitOfWork ->
    val family = workflowFamilyFor(unitOfWork.workflowStates, workflowId) ?: return@read emptyMap()
    val record = family.get(unitOfWork.workflowStates, workflowId) ?: return@read emptyMap()
    val artifacts = decodeArtifacts(record.artifactsJson)
    val result = mutableMapOf<String, Int>()
    decodePhaseRecords(artifacts).values.forEach { phaseRecord ->
      val loopId = phaseRecord.loopId ?: return@forEach
      val edgeIteration = phaseRecord.edgeIteration ?: return@forEach
      result.merge(loopId, edgeIteration, ::maxOf)
    }
    result
  }

  override fun readAttemptLedgerSummary(issueKey: String): GoalRunnerAttemptLedgerSummary =
    database.read { unitOfWork ->
      val normalizedIssueKey = issueKey.trim()
      val entries = buildList {
        listOf(WorkflowFamily.TASK_RUNTIME).forEach { family ->
          family.list(unitOfWork.workflowStates, Int.MAX_VALUE).forEach { snapshot ->
            val artifacts = decodeArtifacts(snapshot.artifactsJson)
            if (goalContinuation(artifacts)?.issueKey != normalizedIssueKey) return@forEach
            (artifacts[GOAL_ATTEMPT_LEDGER_ARTIFACT_KEY] as? List<*>).orEmpty().forEach { item ->
              (item as? Map<*, *>)?.let(::add)
            }
          }
        }
      }
      summarizeAttemptLedgerFromEntries(entries)
    }

  private fun appendHistoryArtifact(append: HistoryArtifactAppend): Boolean = database.transaction { unitOfWork ->
    val family = workflowFamilyFor(unitOfWork.workflowStates, append.workflowId)
      ?: return@transaction false
    val record = family.get(unitOfWork.workflowStates, append.workflowId)
      ?: return@transaction false
    val artifacts = decodeArtifacts(record.artifactsJson)
    val existing = (artifacts[append.historyKey] as? List<*>)
      .orEmpty()
      .mapNotNull { item -> item as? Map<*, *> }
      .mapNotNull { item -> JsonCodec.anyToStringAnyMap(item) }
    val updatedHistory = appendBoundedHistoryBySequence(existing, append.entryMap, append.retentionLimit)
    val patch = buildMap<String, Any?> {
      put(append.historyKey, updatedHistory)
      append.latestKey?.let { put(it, append.entryMap) }
    }
    val updated = engine.updateRecord(
      family.definition,
      record,
      WorkflowUpdateInput(
        workflowStatus = record.workflowStatus,
        currentStepId = record.currentStepId,
        stepUpdates = null,
        artifactsPatch = WorkflowArtifactPatch.from(patch),
        sessionId = record.sessionId.orEmpty(),
      ),
    )
    family.save(unitOfWork.workflowStates, updated)
    true
  }
}

private fun sparseArtifactKeys(existingArtifactsJson: String, keys: Set<String>): Map<String, Any?> {
  if (keys.isEmpty()) return emptyMap()
  val root = JsonCodec.parseObjectOrNull(existingArtifactsJson) ?: return emptyMap()
  return buildMap {
    keys.forEach { key ->
      val element = root[key] ?: return@forEach
      put(key, JsonCodec.jsonElementToValue(element))
    }
  }
}
