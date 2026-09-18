package skillbill.ports.workflow

import skillbill.contracts.workflow.WorkflowContinueSessionSummary
import skillbill.ports.workflow.model.FeatureImplementSessionSummary
import skillbill.ports.workflow.model.FeatureTaskWorkflowCandidate
import skillbill.ports.workflow.model.FeatureVerifySessionSummary
import skillbill.ports.workflow.model.GoalChildWorkflowDeletionScope
import skillbill.ports.workflow.model.WorkflowFamily
import skillbill.ports.workflow.model.WorkflowStateRecord
import skillbill.ports.workflow.model.toContinueSessionSummary
import skillbill.ports.workflow.model.toSnapshot
import skillbill.workflow.engine.model.WorkflowStateSnapshot
import skillbill.workflow.model.FeatureTaskExecutionIdentity
import skillbill.workflow.model.FeatureTaskWorkflowMode

/**
 * Durable workflow-state persistence, split into one capability interface per
 * family so no single interface crosses the detekt `TooManyFunctions`
 * threshold. This remains the single port adapters implement.
 */
interface WorkflowStateRepository :
  FeatureTaskWorkflowStateRepository,
  GoalChildWorkflowStateRepository,
  FeatureTaskRuntimeWorkerRepository,
  FeatureImplementWorkflowStateRepository,
  FeatureVerifyWorkflowStateRepository,
  FeatureTaskRuntimeWorkflowStateRepository

interface FeatureTaskExecutionLookupRepository {
  fun saveFeatureTaskExecutionIdentity(identity: FeatureTaskExecutionIdentity)

  fun getFeatureTaskExecutionIdentity(workflowId: String): FeatureTaskExecutionIdentity?

  fun findStandaloneFeatureTaskCandidates(
    normalizedIssueKey: String,
    repositoryIdentity: String,
  ): List<FeatureTaskWorkflowCandidate>

  fun findGoalChildFeatureTaskCandidates(
    normalizedIssueKey: String,
    repositoryIdentity: String,
  ): List<FeatureTaskWorkflowCandidate>

  fun countGoalChildIdentities(normalizedIssueKey: String): Int

  fun claimFeatureTaskContinuation(workflowId: String, expectedUpdatedAt: String?): Boolean
}

interface FeatureTaskWorkflowStateRepository : FeatureTaskExecutionLookupRepository {
  fun terminalizeLegacyProseFeatureTaskWorkflow(row: WorkflowStateRecord)

  fun saveFeatureTaskWorkflow(row: WorkflowStateRecord, mode: FeatureTaskWorkflowMode)

  fun getFeatureTaskWorkflow(workflowId: String): WorkflowStateRecord?

  fun getFeatureTaskWorkflowAsMode(workflowId: String, mode: FeatureTaskWorkflowMode): WorkflowStateRecord?

  fun listFeatureTaskWorkflows(mode: FeatureTaskWorkflowMode, limit: Int = 20): List<WorkflowStateRecord>

  fun latestFeatureTaskWorkflow(mode: FeatureTaskWorkflowMode): WorkflowStateRecord?
}

interface GoalChildWorkflowStateRepository {
  fun listGoalChildWorkflowIdsByParent(parentWorkflowId: String): List<String>

  fun deleteGoalChildWorkflowsByParent(parentWorkflowId: String): Int

  fun deleteGoalChildWorkflow(
    parentWorkflowId: String,
    subtaskId: Int,
    workflowId: String,
    scope: GoalChildWorkflowDeletionScope = GoalChildWorkflowDeletionScope.TERMINAL_ONLY,
  ): Int
}

interface FeatureImplementWorkflowStateRepository {
  /**
   * Compatibility alias for bill-feature-task mode=prose. Authoritative
   * implementations should store the row in the shared feature-task workflow store.
   */
  fun saveFeatureImplementWorkflow(row: WorkflowStateRecord)

  fun getFeatureImplementWorkflow(workflowId: String): WorkflowStateRecord?

  fun getFeatureImplementWorkflows(workflowIds: Set<String>): Map<String, WorkflowStateRecord> =
    workflowIds.mapNotNull { workflowId -> getFeatureImplementWorkflow(workflowId)?.let { workflowId to it } }.toMap()

  fun listFeatureImplementWorkflows(limit: Int = 20): List<WorkflowStateRecord>

  fun latestFeatureImplementWorkflow(): WorkflowStateRecord?

  fun getFeatureImplementSessionSummary(sessionId: String): FeatureImplementSessionSummary?
}

interface FeatureVerifyWorkflowStateRepository {
  fun saveFeatureVerifyWorkflow(row: WorkflowStateRecord)

  fun getFeatureVerifyWorkflow(workflowId: String): WorkflowStateRecord?

  fun getFeatureVerifyWorkflows(workflowIds: Set<String>): Map<String, WorkflowStateRecord> =
    workflowIds.mapNotNull { workflowId -> getFeatureVerifyWorkflow(workflowId)?.let { workflowId to it } }.toMap()

  fun listFeatureVerifyWorkflows(limit: Int = 20): List<WorkflowStateRecord>

  fun latestFeatureVerifyWorkflow(): WorkflowStateRecord?

  fun getFeatureVerifySessionSummary(sessionId: String): FeatureVerifySessionSummary?
}

/**
 * Persistence for the experimental feature-task-runtime pipeline. Per-phase
 * records and the append-only phase ledger ride inside the [WorkflowStateRecord]
 * artifacts envelope; there is intentionally no session-summary method.
 */
interface FeatureTaskRuntimeWorkflowStateRepository {
  /**
   * Compatibility alias for bill-feature-task mode=runtime. Authoritative
   * implementations should store the row in the shared feature-task workflow store.
   */
  fun saveFeatureTaskRuntimeWorkflow(row: WorkflowStateRecord)

  fun getFeatureTaskRuntimeWorkflow(workflowId: String): WorkflowStateRecord?

  fun getFeatureTaskRuntimeWorkflows(workflowIds: Set<String>): Map<String, WorkflowStateRecord> =
    workflowIds.mapNotNull { workflowId -> getFeatureTaskRuntimeWorkflow(workflowId)?.let { workflowId to it } }.toMap()

  fun listFeatureTaskRuntimeWorkflows(limit: Int = 20): List<WorkflowStateRecord>

  fun latestFeatureTaskRuntimeWorkflow(): WorkflowStateRecord?
}

fun WorkflowStateSnapshot.toRecord(): WorkflowStateRecord = WorkflowStateRecord(
  workflowId = workflowId,
  sessionId = sessionId,
  workflowName = workflowName,
  contractVersion = contractVersion,
  workflowStatus = workflowStatus.wireValue,
  currentStepId = currentStepId,
  stepsJson = stepsJson,
  artifactsJson = artifactsJson,
  startedAt = startedAt,
  updatedAt = updatedAt,
  finishedAt = finishedAt,
  mode = mode?.let(FeatureTaskWorkflowMode::fromWireValue),
)

fun WorkflowFamily.save(repository: WorkflowStateRepository, record: WorkflowStateSnapshot) {
  saveRecord(repository, record.toRecord())
}

fun WorkflowFamily.saveRecord(repository: WorkflowStateRepository, record: WorkflowStateRecord) {
  when (this) {
    WorkflowFamily.VERIFY -> repository.saveFeatureVerifyWorkflow(record)
    WorkflowFamily.TASK_RUNTIME -> repository.saveFeatureTaskWorkflow(record, FeatureTaskWorkflowMode.RUNTIME)
  }
}

fun WorkflowFamily.get(repository: WorkflowStateRepository, workflowId: String): WorkflowStateSnapshot? = when (this) {
  WorkflowFamily.VERIFY -> repository.getFeatureVerifyWorkflow(workflowId)
  WorkflowFamily.TASK_RUNTIME ->
    repository.getFeatureTaskWorkflowAsMode(workflowId, FeatureTaskWorkflowMode.RUNTIME)
}?.toSnapshot()

fun WorkflowFamily.getAll(
  repository: WorkflowStateRepository,
  workflowIds: Set<String>,
): Map<String, WorkflowStateSnapshot> = buildMap {
  workflowIds.chunked(WORKFLOW_SNAPSHOT_BATCH_SIZE).forEach { batch ->
    val records = when (this@getAll) {
      WorkflowFamily.VERIFY -> repository.getFeatureVerifyWorkflows(batch.toSet())
      WorkflowFamily.TASK_RUNTIME -> repository.getFeatureTaskRuntimeWorkflows(batch.toSet())
    }
    records.forEach { (workflowId, record) -> put(workflowId, record.toSnapshot()) }
  }
}

fun WorkflowFamily.list(repository: WorkflowStateRepository, limit: Int): List<WorkflowStateSnapshot> = when (this) {
  WorkflowFamily.VERIFY -> repository.listFeatureVerifyWorkflows(limit)
  WorkflowFamily.TASK_RUNTIME -> repository.listFeatureTaskWorkflows(FeatureTaskWorkflowMode.RUNTIME, limit)
}.map(WorkflowStateRecord::toSnapshot)

fun WorkflowFamily.latest(repository: WorkflowStateRepository): WorkflowStateSnapshot? = when (this) {
  WorkflowFamily.VERIFY -> repository.latestFeatureVerifyWorkflow()
  WorkflowFamily.TASK_RUNTIME -> repository.latestFeatureTaskWorkflow(FeatureTaskWorkflowMode.RUNTIME)
}?.toSnapshot()

fun WorkflowFamily.sessionSummary(
  repository: WorkflowStateRepository,
  sessionId: String,
): WorkflowContinueSessionSummary {
  if (sessionId.isBlank()) {
    return WorkflowContinueSessionSummary.EMPTY
  }
  return when (this) {
    WorkflowFamily.VERIFY -> repository.getFeatureVerifySessionSummary(sessionId)?.toContinueSessionSummary()
      ?: WorkflowContinueSessionSummary.EMPTY
    WorkflowFamily.TASK_RUNTIME -> WorkflowContinueSessionSummary.EMPTY
  }
}

const val WORKFLOW_SNAPSHOT_BATCH_SIZE = 900
