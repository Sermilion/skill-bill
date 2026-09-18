package skillbill.ports.workflow

import skillbill.ports.featuretask.model.FeatureTaskRuntimeCrashReconciliationCandidate
import skillbill.ports.featuretask.model.FeatureTaskRuntimeWorkerOwnership
import skillbill.ports.workflow.model.FeatureImplementSessionSummary
import skillbill.workflow.model.FeatureTaskExecutionIdentity
import skillbill.ports.workflow.model.FeatureTaskWorkflowCandidate
import skillbill.workflow.model.FeatureTaskWorkflowMode
import skillbill.ports.workflow.model.FeatureVerifySessionSummary
import skillbill.ports.workflow.model.GoalChildWorkflowDeletionScope
import skillbill.ports.workflow.model.WorkflowStateRecord

abstract class WorkflowStateRepositoryDefaults : WorkflowStateRepository {
  open override fun saveFeatureTaskExecutionIdentity(identity: FeatureTaskExecutionIdentity) = Unit

  open override fun getFeatureTaskExecutionIdentity(workflowId: String): FeatureTaskExecutionIdentity? = null

  open override fun findStandaloneFeatureTaskCandidates(
    normalizedIssueKey: String,
    repositoryIdentity: String,
  ): List<FeatureTaskWorkflowCandidate> = emptyList()

  open override fun findGoalChildFeatureTaskCandidates(
    normalizedIssueKey: String,
    repositoryIdentity: String,
  ): List<FeatureTaskWorkflowCandidate> = emptyList()

  open override fun countGoalChildIdentities(normalizedIssueKey: String): Int = 0

  open override fun claimFeatureTaskContinuation(workflowId: String, expectedUpdatedAt: String?): Boolean = false

  open override fun terminalizeLegacyProseFeatureTaskWorkflow(row: WorkflowStateRecord) = Unit

  open override fun saveFeatureTaskWorkflow(row: WorkflowStateRecord, mode: FeatureTaskWorkflowMode) = Unit

  open override fun getFeatureTaskWorkflow(workflowId: String): WorkflowStateRecord? = null

  open override fun getFeatureTaskWorkflowAsMode(workflowId: String, mode: FeatureTaskWorkflowMode): WorkflowStateRecord? =
    null

  open override fun listFeatureTaskWorkflows(mode: FeatureTaskWorkflowMode, limit: Int): List<WorkflowStateRecord> =
    emptyList()

  open override fun latestFeatureTaskWorkflow(mode: FeatureTaskWorkflowMode): WorkflowStateRecord? = null

  open override fun listGoalChildWorkflowIdsByParent(parentWorkflowId: String): List<String> = emptyList()

  open override fun deleteGoalChildWorkflowsByParent(parentWorkflowId: String): Int = 0

  open override fun deleteGoalChildWorkflow(
    parentWorkflowId: String,
    subtaskId: Int,
    workflowId: String,
    scope: GoalChildWorkflowDeletionScope,
  ): Int = 0

  open override fun getFeatureTaskRuntimeWorkerOwnership(workflowId: String): FeatureTaskRuntimeWorkerOwnership? = null

  open override fun acquireFeatureTaskRuntimeWorker(
    ownership: FeatureTaskRuntimeWorkerOwnership,
    expectedUpdatedAt: String?,
  ): Boolean = false

  open override fun reserveFeatureTaskRuntimeWorkerTakeover(
    workflowId: String,
    expectedOwnerToken: String,
    expectedGeneration: Long,
  ): Boolean = false

  open override fun transferFeatureTaskRuntimeWorker(
    ownership: FeatureTaskRuntimeWorkerOwnership,
    expectedOwnerToken: String,
    expectedGeneration: Long,
  ): Boolean = false

  open override fun heartbeatFeatureTaskRuntimeWorker(ownership: FeatureTaskRuntimeWorkerOwnership): Boolean = false

  open override fun releaseFeatureTaskRuntimeWorker(workflowId: String, ownerToken: String, generation: Long): Boolean =
    false

  open override fun findFeatureTaskRuntimeCrashReconciliationCandidates(
    nowInstant: String,
  ): List<FeatureTaskRuntimeCrashReconciliationCandidate> = emptyList()

  open override fun reconcileFeatureTaskRuntimeCrashedWorker(
    workflowId: String,
    ownerToken: String,
    generation: Long,
    interruptionReason: String,
    nowInstant: String,
  ): Boolean = false

  open override fun saveFeatureImplementWorkflow(row: WorkflowStateRecord) = Unit

  open override fun getFeatureImplementWorkflow(workflowId: String): WorkflowStateRecord? = null

  open override fun listFeatureImplementWorkflows(limit: Int): List<WorkflowStateRecord> = emptyList()

  open override fun latestFeatureImplementWorkflow(): WorkflowStateRecord? = null

  open override fun getFeatureImplementSessionSummary(sessionId: String): FeatureImplementSessionSummary? = null

  open override fun saveFeatureVerifyWorkflow(row: WorkflowStateRecord) = Unit

  open override fun getFeatureVerifyWorkflow(workflowId: String): WorkflowStateRecord? = null

  open override fun listFeatureVerifyWorkflows(limit: Int): List<WorkflowStateRecord> = emptyList()

  open override fun latestFeatureVerifyWorkflow(): WorkflowStateRecord? = null

  open override fun getFeatureVerifySessionSummary(sessionId: String): FeatureVerifySessionSummary? = null

  open override fun saveFeatureTaskRuntimeWorkflow(row: WorkflowStateRecord) = Unit

  open override fun getFeatureTaskRuntimeWorkflow(workflowId: String): WorkflowStateRecord? = null

  open override fun listFeatureTaskRuntimeWorkflows(limit: Int): List<WorkflowStateRecord> = emptyList()

  open override fun latestFeatureTaskRuntimeWorkflow(): WorkflowStateRecord? = null
}
