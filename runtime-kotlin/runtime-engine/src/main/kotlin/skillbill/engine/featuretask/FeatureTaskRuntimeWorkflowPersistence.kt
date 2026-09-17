package skillbill.engine.featuretask
import me.tatarka.inject.annotations.Inject
import skillbill.application.workflow.model.WorkflowFamily
import skillbill.contracts.issuekey.normalizeIssueKey
import skillbill.engine.featuretask.model.FeatureTaskRuntimePhaseStateRequest
import skillbill.engine.featuretask.model.FeatureTaskRuntimePhaseStepWireUpdate
import skillbill.engine.goalrunner.GOAL_CHILD_REPAIR_EVIDENCE_ARTIFACT_KEY
import skillbill.error.InvalidWorkflowStateSchemaError
import skillbill.error.WorkflowIssueKeyConflictError
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.featuretask.model.FeatureTaskRuntimeWorkerOwnership
import skillbill.ports.workflow.WorkflowStateRepository
import skillbill.ports.workflow.get
import skillbill.ports.workflow.model.FeatureTaskWorkflowMode
import skillbill.ports.workflow.save
import skillbill.ports.workflow.saveRecord
import skillbill.ports.workflow.toRecord
import skillbill.workflow.engine.WorkflowEngine
import skillbill.workflow.engine.WorkflowSnapshotValidator
import skillbill.workflow.engine.model.DurableWorkflowArtifacts
import skillbill.workflow.engine.model.WorkflowArtifactPatch
import skillbill.workflow.engine.model.WorkflowStateSnapshot
import skillbill.workflow.engine.model.WorkflowStepUpdates
import skillbill.workflow.engine.model.WorkflowUpdateInput
import skillbill.workflow.goal.model.GOAL_REVIEW_BASE_RECOVERIES_ARTIFACT_KEY
import skillbill.workflow.goal.model.GOAL_SUBTASK_REVIEW_STATE_ARTIFACT_KEY
import skillbill.workflow.model.WorkflowStatus
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.model.workflowStepStatus
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.asWorkflowArtifactEntry
import skillbill.workflow.taskruntime.model.FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_OUTCOME_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.FEATURE_TASK_RUNTIME_PHASE_STATUS_BLOCKED
import skillbill.workflow.taskruntime.model.FEATURE_TASK_RUNTIME_PHASE_STATUS_PAUSED
import skillbill.workflow.taskruntime.model.FEATURE_TASK_RUNTIME_PHASE_STATUS_PENDING
import skillbill.workflow.taskruntime.model.FEATURE_TASK_RUNTIME_RUN_INVARIANTS_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeImplementationAttemptStatus
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseRecord
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRunInvariants
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

internal data class WorkflowRowAdvance(
  val currentStepId: String,
  val workflowStatus: String,
  val stepUpdates: List<FeatureTaskRuntimePhaseStepWireUpdate>? = null,
) {
  companion object {
    fun keepFrom(record: WorkflowStateSnapshot): WorkflowRowAdvance =
      WorkflowRowAdvance(currentStepId = record.currentStepId, workflowStatus = record.workflowStatus.wireValue)
  }
}

class FeatureTaskRuntimeWorkflowPersistence @Inject constructor(
  private val database: DatabaseSessionFactory,
  workflowSnapshotValidator: WorkflowSnapshotValidator,
) {
  private val engine: WorkflowEngine = WorkflowEngine(workflowSnapshotValidator)

  fun existingWorkflowMode(workflowId: String): FeatureTaskWorkflowMode? = database.read { unitOfWork ->
    unitOfWork.workflowStates.getFeatureTaskWorkflow(workflowId)?.mode
  }

  fun workerOwnership(workflowId: String): FeatureTaskRuntimeWorkerOwnership? = database.read { unitOfWork ->
    unitOfWork.workflowStates.getFeatureTaskRuntimeWorkerOwnership(workflowId)
  }

  fun ensureWorkflowOpen(workflowId: String, sessionId: String, issueKey: String? = null): Boolean =
    database.transaction { unitOfWork ->
      val normalizedIssueKey = normalizeIssueKey(issueKey)
      val existing = unitOfWork.workflowStates.getFeatureTaskRuntimeWorkflow(workflowId)
      if (existing != null) {
        val persistedIssueKey = existing.issueKey
          ?.trim()
          ?.takeIf(String::isNotEmpty)
          ?.let(::normalizeIssueKey)
        if (
          persistedIssueKey != null &&
          normalizedIssueKey != null &&
          persistedIssueKey != normalizedIssueKey
        ) {
          throw WorkflowIssueKeyConflictError(workflowId, persistedIssueKey, normalizedIssueKey)
        }
        if (persistedIssueKey == null && normalizedIssueKey != null) {
          unitOfWork.workflowStates.saveFeatureTaskRuntimeWorkflow(
            existing.copy(issueKey = normalizedIssueKey, sessionId = existing.sessionId.ifBlank { sessionId }),
          )
        } else if (existing.sessionId.isBlank()) {
          unitOfWork.workflowStates.saveFeatureTaskRuntimeWorkflow(existing.copy(sessionId = sessionId))
        }
        return@transaction true
      }
      val opened = engine.openRecord(
        WorkflowFamily.TASK_RUNTIME.definition,
        workflowId,
        sessionId,
        WorkflowFamily.TASK_RUNTIME.definition.defaultInitialStepId,
      )
      WorkflowFamily.TASK_RUNTIME.saveRecord(
        unitOfWork.workflowStates,
        opened.toRecord().copy(issueKey = normalizedIssueKey),
      )
      true
    }

  fun readArtifacts(workflowId: String): DurableWorkflowArtifacts? = database.read { unitOfWork ->
    val record = WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId) ?: return@read null
    artifactsFrom(record)
  }

  internal fun persistArtifactsPatch(
    workflowStates: WorkflowStateRepository,
    record: WorkflowStateSnapshot,
    patch: Map<String, Any?>,
    advance: WorkflowRowAdvance = WorkflowRowAdvance.keepFrom(record),
  ) {
    val updated = engine.updateRecord(
      WorkflowFamily.TASK_RUNTIME.definition,
      record,
      WorkflowUpdateInput(
        workflowStatus = WorkflowStatus.fromWire(advance.workflowStatus)
          ?: throw InvalidWorkflowStateSchemaError(
            "Workflow update workflow_status has unsupported value '${advance.workflowStatus}'.",
          ),
        currentStepId = advance.currentStepId,
        stepUpdates = WorkflowStepUpdates.from(
          advance.stepUpdates?.map(FeatureTaskRuntimePhaseStepWireUpdate::toWireMap),
        ),
        artifactsPatch = WorkflowArtifactPatch.from(patch),
        sessionId = record.sessionId.orEmpty(),
      ),
    )
    WorkflowFamily.TASK_RUNTIME.save(workflowStates, updated)
  }

  internal fun persistRunInvariantsPatch(
    workflowStates: WorkflowStateRepository,
    record: WorkflowStateSnapshot,
    runInvariants: FeatureTaskRuntimeRunInvariants,
  ) {
    persistArtifactsPatch(
      workflowStates,
      record,
      mapOf(FEATURE_TASK_RUNTIME_RUN_INVARIANTS_ARTIFACT_KEY to runInvariants.asWorkflowArtifactEntry()),
    )
  }

  companion object {
    fun artifactsFrom(record: WorkflowStateSnapshot): DurableWorkflowArtifacts =
      DurableWorkflowArtifacts.fromJson(record.artifactsJson)

    fun artifactsFromJson(artifactsJson: String?): DurableWorkflowArtifacts? =
      artifactsJson?.takeIf(String::isNotBlank)?.let(DurableWorkflowArtifacts::fromJson)
  }
}

internal object FeatureTaskRuntimeWorkflowArtifactPatches {
  fun clearGoalContinuationOutcome(): Map<String, Any?> =
    mapOf(FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_OUTCOME_ARTIFACT_KEY to null)

  fun goalContinuationArtifact(entry: Any): Map<String, Any?> =
    mapOf(FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_ARTIFACT_KEY to entry)

  fun goalSubtaskReviewState(entry: Any): Map<String, Any?> = mapOf(GOAL_SUBTASK_REVIEW_STATE_ARTIFACT_KEY to entry)

  fun goalReviewBaseRecoveries(entries: List<Any?>): Map<String, Any?> =
    mapOf(GOAL_REVIEW_BASE_RECOVERIES_ARTIFACT_KEY to entries)

  fun goalChildRepairEvidence(entries: List<Any?>): Map<String, Any?> =
    mapOf(GOAL_CHILD_REPAIR_EVIDENCE_ARTIFACT_KEY to entries)
}

internal fun stepUpdatesFrom(
  records: Map<String, FeatureTaskRuntimePhaseRecord>,
): List<FeatureTaskRuntimePhaseStepWireUpdate> {
  fun stepStatusFor(record: FeatureTaskRuntimePhaseRecord): String = when {
    record.status.workflowStepStatus() == WorkflowStepStatus.BLOCKED -> FEATURE_TASK_RUNTIME_PHASE_STATUS_BLOCKED
    record.status.workflowStepStatus() == WorkflowStepStatus.PAUSED -> FEATURE_TASK_RUNTIME_PHASE_STATUS_PAUSED
    record.status.workflowStepStatus() == WorkflowStepStatus.PENDING -> FEATURE_TASK_RUNTIME_PHASE_STATUS_PENDING
    record.finishedAt != null -> "completed"
    record.status.workflowStepStatus() in setOf(WorkflowStepStatus.RUNNING, WorkflowStepStatus.COMPLETED) ->
      record.status.wireValue
    else -> throw InvalidWorkflowStateSchemaError(
      "Feature-task-runtime phase '${record.phaseId}' has unmappable status '${record.status}' for steps[].",
    )
  }
  return records.values.map { record ->
    FeatureTaskRuntimePhaseStepWireUpdate(
      stepId = record.phaseId,
      status = stepStatusFor(record),
      attemptCount = record.attemptCount,
    )
  }
}

fun workflowStatusFor(request: FeatureTaskRuntimePhaseStateRequest): String = when {
  request.status.workflowStepStatus() == WorkflowStepStatus.PAUSED -> "paused"
  request.status.workflowStepStatus() == WorkflowStepStatus.BLOCKED -> "blocked"
  request.finished && request.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.definition.stepIds.last() ->
    "completed"
  else -> "running"
}

fun attemptStatusFor(request: FeatureTaskRuntimePhaseStateRequest): FeatureTaskRuntimeImplementationAttemptStatus =
  when (request.status.workflowStepStatus()) {
    WorkflowStepStatus.COMPLETED -> FeatureTaskRuntimeImplementationAttemptStatus.COMPLETED
    WorkflowStepStatus.BLOCKED -> FeatureTaskRuntimeImplementationAttemptStatus.BLOCKED
    else -> FeatureTaskRuntimeImplementationAttemptStatus.INCOMPLETE
  }

fun durationMillis(startedAt: String, finishedAt: String): Long =
  Duration.between(Instant.parse(startedAt), Instant.parse(finishedAt)).toMillis().coerceAtLeast(0)

fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
  .digest(value.toByteArray())
  .joinToString("") { "%02x".format(it) }

const val PHASE_RECORDER_STATUS_RUNNING = "running"
