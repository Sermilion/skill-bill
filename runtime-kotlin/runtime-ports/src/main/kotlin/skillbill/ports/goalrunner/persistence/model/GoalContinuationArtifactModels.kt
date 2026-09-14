package skillbill.ports.goalrunner.persistence.model
import skillbill.goalrunner.model.GoalContinuation
import skillbill.goalrunner.model.GoalRunnerStoredOutcome
import skillbill.goalrunner.model.GoalRunnerSupervisionEvent
import skillbill.ports.workflow.WorkflowStateRepository
import skillbill.ports.workflow.model.WorkflowFamily
import skillbill.workflow.engine.model.WorkflowStateSnapshot

data class GoalSubtaskIdentity(
  val workflowId: String,
  val issueKey: String,
  val subtaskId: Int,
)

data class HistoryArtifactAppend(
  val workflowId: String,
  val latestKey: String?,
  val historyKey: String,
  val retentionLimit: Int,
  val entryMap: Any,
)

data class GoalContinuationCandidate(
  val family: WorkflowFamily,
  val snapshot: WorkflowStateSnapshot,
  val goalContinuation: GoalContinuation,
  val outcome: GoalRunnerStoredOutcome?,
)

data class GoalRunnerBlockWrite(
  val family: WorkflowFamily,
  val record: WorkflowStateSnapshot,
  val blockedReason: String,
  val lastResumableStep: String,
  val workflowStates: WorkflowStateRepository,
  val supervisionEvent: GoalRunnerSupervisionEvent?,
)
