package skillbill.engine.goalrunner.persist

import skillbill.contracts.JsonCodec
import skillbill.engine.goalrunner.execution.support.maxHistorySequence
import skillbill.engine.goalrunner.execution.support.workflowFamilyFor
import skillbill.goalrunner.GoalObservabilityArtifacts
import skillbill.goalrunner.WORKER_SUBTASK_REQUEST_OUTCOME_LIMIT
import skillbill.goalrunner.backwardEdgeCountsFromLedger
import skillbill.goalrunner.declaredProgressEventFrom
import skillbill.goalrunner.decodeDeclaredGoalProgressEvent
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
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.goalrunner.persistence.model.HistoryArtifactAppend
import skillbill.ports.goalrunner.runner.GoalRunnerAttemptLedgerStore
import skillbill.ports.goalrunner.runner.GoalRunnerWorkflowLedgerWriteStore
import skillbill.ports.goalrunner.runner.GoalRunnerWorkflowProgressStore
import skillbill.ports.goalrunner.runner.model.GoalRunnerAttemptLedgerRecordRequest
import skillbill.ports.goalrunner.runner.model.GoalRunnerLedgerSequenceWatermarks
import skillbill.ports.goalrunner.runner.model.GoalRunnerProgressEventRecordRequest
import skillbill.ports.goalrunner.runner.model.GoalRunnerWorkflowProgress
import skillbill.ports.taskruntime.FeatureTaskRuntimeWireArtifactValidator
import skillbill.ports.taskruntime.validateGoalObservabilityEvent
import skillbill.ports.taskruntime.validateGoalProgressEvent
import skillbill.ports.workflow.WorkflowSnapshotValidator
import skillbill.ports.workflow.get
import skillbill.ports.workflow.list
import skillbill.ports.workflow.model.WorkflowFamily
import skillbill.ports.workflow.save
import skillbill.workflow.engine.WorkflowEngine
import skillbill.workflow.engine.model.DurableWorkflowArtifactFamily
import skillbill.workflow.engine.model.DurableWorkflowArtifacts
import skillbill.workflow.engine.model.WorkflowArtifactPatch
import skillbill.workflow.engine.model.WorkflowUpdateInput
import skillbill.workflow.engine.progressToken
import skillbill.workflow.model.WorkflowStatus
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.model.goalreview.GOAL_PROGRESS_HISTORY_LIMIT
import skillbill.workflow.model.goalreview.GoalProgressEvent
import skillbill.workflow.model.goalreview.appendBoundedHistoryBySequence
import skillbill.workflow.model.goalreview.goalObservabilityLatestEventFromArtifacts
import skillbill.workflow.taskruntime.artifact.phaseRecordsFromWorkflowArtifacts
import skillbill.workflow.taskruntime.model.persistence.task.runtime.goal.goalContinuation

private val PROGRESS_POLL_ARTIFACT_KEYS =
  setOf(
    "progress_event",
    DurableWorkflowArtifactFamily.GOAL_PROGRESS_LATEST_EVENT.label(),
    DurableWorkflowArtifactFamily.GOAL_OBSERVABILITY_LATEST_EVENT.label(),
  )

internal class WorkflowGoalRunnerProgressRecording(
  private val database: DatabaseSessionFactory,
  private val engine: WorkflowEngine,
  private val workflowSnapshotValidator: WorkflowSnapshotValidator,
  private val goalObservabilityEventValidator: FeatureTaskRuntimeWireArtifactValidator,
  private val goalProgressEventValidator: FeatureTaskRuntimeWireArtifactValidator,
) : GoalRunnerWorkflowProgressStore,
  GoalRunnerWorkflowLedgerWriteStore,
  GoalRunnerAttemptLedgerStore {
  override fun progress(workflowId: String): GoalRunnerWorkflowProgress? =
    database.read { unitOfWork ->
      val family = workflowFamilyFor(unitOfWork.workflowStates, workflowId) ?: return@read null
      val record = family.get(unitOfWork.workflowStates, workflowId) ?: return@read null
      workflowSnapshotValidator.validate(record, family.definition.workflowName)
      val steps = record.steps
      val artifacts = record.artifacts.filterKeys(PROGRESS_POLL_ARTIFACT_KEYS::contains)
      val finishCompleted =
        steps.any {
            step ->
          step.stepId == "pr" && step.status == WorkflowStepStatus.COMPLETED
        }
      val currentStep =
        if (record.workflowStatus == WorkflowStatus.COMPLETED || finishCompleted) {
          "pr"
        } else {
          record.currentStepId
        }
      val progressEvent = progressEventFrom(artifacts)
      val declaredProgressEvent = declaredProgressEventFrom(artifacts)
      val observabilityEvent =
        runCatching {
          goalObservabilityLatestEventFromArtifacts(artifacts)
        }.getOrNull()
      GoalRunnerWorkflowProgress(
        workflowId = record.workflowId,
        workflowStatus = record.workflowStatus,
        currentStepId = currentStep,
        progressToken = record.progressToken(),
        latestDurableProgressEvent = progressEvent,
        latestGoalObservabilityEvent = observabilityEvent?.toProgressEvent(),
        latestDeclaredProgressEvent = declaredProgressEvent,
        latestLivenessSignal =
          observabilityEvent?.compactLivenessSummary()
            ?: progressEvent?.summary()
            ?: "workflow_status=${record.workflowStatus}; step=$currentStep",
        lastSnapshotUpdatedAt = record.updatedAt?.toString(),
      )
    }

  override fun recordObservabilityEvent(request: GoalRunnerObservabilityRecordRequest): Boolean =
    database.transaction { unitOfWork ->
      val family =
        workflowFamilyFor(unitOfWork.workflowStates, request.workflowId)
          ?: return@transaction false
      val record =
        family.get(unitOfWork.workflowStates, request.workflowId)
          ?: return@transaction false
      val artifacts = record.artifacts
      val observabilityPatch =
        GoalObservabilityArtifacts.patchForRuntimeEvent(
          input =
            GoalObservabilityRuntimeEventInput(
              artifacts = artifacts,
              request = request,
            ),
          validator = goalObservabilityEventValidator::validateGoalObservabilityEvent,
        )
      val updated =
        engine.updateRecord(
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
    goalProgressEventValidator.validateGoalProgressEvent(
      entryMap,
      DurableWorkflowArtifactFamily.GOAL_PROGRESS_LATEST_EVENT.label(),
    )
    return appendHistoryArtifact(
      HistoryArtifactAppend(
        workflowId = request.workflowId,
        latestFamily = DurableWorkflowArtifactFamily.GOAL_PROGRESS_LATEST_EVENT,
        historyFamily = DurableWorkflowArtifactFamily.GOAL_PROGRESS_RUN_HISTORY,
        retentionLimit = GOAL_PROGRESS_HISTORY_LIMIT,
        entryMap = entryMap,
      ),
    )
  }

  override fun recordAttemptLedgerEntry(request: GoalRunnerAttemptLedgerRecordRequest): Boolean =
    appendHistoryArtifact(
      HistoryArtifactAppend(
        workflowId = request.workflowId,
        latestFamily = null,
        historyFamily = DurableWorkflowArtifactFamily.GOAL_ATTEMPT_LEDGER,
        retentionLimit = GOAL_ATTEMPT_LEDGER_LIMIT,
        entryMap = request.entry.toPersistenceWire(),
      ),
    )

  override fun progressEvents(workflowId: String): List<GoalProgressEvent> =
    database.transaction { unitOfWork ->
      val family =
        workflowFamilyFor(unitOfWork.workflowStates, workflowId)
          ?: return@transaction emptyList()
      val record =
        family.get(unitOfWork.workflowStates, workflowId)
          ?: return@transaction emptyList()
      (DurableWorkflowArtifactFamily.GOAL_PROGRESS_RUN_HISTORY.value(record.artifacts) as? List<*>)
        .orEmpty()
        .mapNotNull { item -> item as? Map<*, *> }
        .mapNotNull { item -> JsonCodec.anyToStringAnyMap(item) }
        .map {
            map ->
          map.decodeDeclaredGoalProgressEvent(DurableWorkflowArtifactFamily.GOAL_PROGRESS_RUN_HISTORY.label())
        }
    }

  override fun recordWorkerSubtaskRequestOutcomes(
    workflowId: String,
    outcomes: List<GoalRunnerWorkerSubtaskRequestOutcome>,
  ): Boolean =
    database.transaction { unitOfWork ->
      val family =
        workflowFamilyFor(unitOfWork.workflowStates, workflowId)
          ?: return@transaction false
      val record =
        family.get(unitOfWork.workflowStates, workflowId)
          ?: return@transaction false
      val artifacts = record.artifacts
      val existing =
        (DurableWorkflowArtifactFamily.WORKER_SUBTASK_REQUEST_OUTCOMES.value(artifacts) as? List<*>)
          .orEmpty()
          .mapNotNull { item -> item as? Map<*, *> }
          .map { item -> JsonCodec.anyToStringAnyMap(item) }
      val updatedOutcomes =
        (existing + outcomes.map(GoalRunnerWorkerSubtaskRequestOutcome::toPersistenceWire))
          .takeLast(WORKER_SUBTASK_REQUEST_OUTCOME_LIMIT)
      val updated =
        engine.updateRecord(
          family.definition,
          record,
          WorkflowUpdateInput(
            workflowStatus = record.workflowStatus,
            currentStepId = record.currentStepId,
            stepUpdates = null,
            artifactsPatch =
              WorkflowArtifactPatch.from(
                mapOf(DurableWorkflowArtifactFamily.WORKER_SUBTASK_REQUEST_OUTCOMES.entry(updatedOutcomes)),
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
          val artifacts = snapshot.artifacts
          if (DurableWorkflowArtifacts.fromMap(artifacts).goalContinuation()?.issueKey != normalizedIssueKey) {
            return@forEach
          }
          maxLedger =
            maxHistorySequence(
              artifacts,
              DurableWorkflowArtifactFamily.GOAL_ATTEMPT_LEDGER,
              maxLedger,
            )
          maxProgress =
            maxHistorySequence(
              artifacts,
              DurableWorkflowArtifactFamily.GOAL_PROGRESS_RUN_HISTORY,
              maxProgress,
            )
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

  override fun childWorkflowLoopIterations(workflowId: String): Map<String, Int> =
    database.read { unitOfWork ->
      val family = workflowFamilyFor(unitOfWork.workflowStates, workflowId) ?: return@read emptyMap()
      val record = family.get(unitOfWork.workflowStates, workflowId) ?: return@read emptyMap()
      val artifacts = record.artifacts
      val result = mutableMapOf<String, Int>()
      phaseRecordsFromWorkflowArtifacts(artifacts).values.forEach { phaseRecord ->
        val loopId = phaseRecord.loopId ?: return@forEach
        val edgeIteration = phaseRecord.edgeIteration ?: return@forEach
        result.merge(loopId, edgeIteration, ::maxOf)
      }
      result
    }

  override fun readAttemptLedgerSummary(issueKey: String): GoalRunnerAttemptLedgerSummary =
    database.read { unitOfWork ->
      val normalizedIssueKey = issueKey.trim()
      val entries =
        buildList {
          listOf(WorkflowFamily.TASK_RUNTIME).forEach { family ->
            family.list(unitOfWork.workflowStates, Int.MAX_VALUE).forEach { snapshot ->
              val artifacts = snapshot.artifacts
              if (
                DurableWorkflowArtifacts.fromMap(artifacts).goalContinuation()?.issueKey != normalizedIssueKey
              ) {
                return@forEach
              }
              (DurableWorkflowArtifactFamily.GOAL_ATTEMPT_LEDGER.value(artifacts) as? List<*>)
                .orEmpty()
                .forEach { item ->
                  (item as? Map<*, *>)?.let(::add)
                }
            }
          }
        }
      summarizeAttemptLedgerFromEntries(entries)
    }

  private fun appendHistoryArtifact(append: HistoryArtifactAppend): Boolean =
    database.transaction { unitOfWork ->
      val family =
        workflowFamilyFor(unitOfWork.workflowStates, append.workflowId)
          ?: return@transaction false
      val record =
        family.get(unitOfWork.workflowStates, append.workflowId)
          ?: return@transaction false
      val artifacts = record.artifacts
      val existing =
        (append.historyFamily.value(artifacts) as? List<*>)
          .orEmpty()
          .mapNotNull { item -> item as? Map<*, *> }
          .mapNotNull { item -> JsonCodec.anyToStringAnyMap(item) }
      val updatedHistory = appendBoundedHistoryBySequence(existing, append.entryMap, append.retentionLimit)
      val patch =
        buildMap<String, Any?> {
          append.historyFamily.putInto(this, updatedHistory)
          append.latestFamily?.putInto(this, append.entryMap)
        }
      val updated =
        engine.updateRecord(
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
