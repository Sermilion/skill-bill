package skillbill.ports.work.model

import skillbill.workflow.model.WorkflowStatus
import java.time.Instant

enum class WorkItemKind(val wireValue: String) {
  FEATURE_TASK_PROSE("feature-task-prose"),
  FEATURE_TASK_RUNTIME("feature-task-runtime"),
  FEATURE_VERIFY("feature-verify"),
  FEATURE_GOAL("feature-goal"),
}

val LEGACY_FEATURE_TASK_PROSE_WORKFLOW_STATUSES: Set<String> =
  setOf(
    WorkflowStatus.PENDING.wireValue,
    WorkflowStatus.RUNNING.wireValue,
    WorkflowStatus.COMPLETED.wireValue,
    WorkflowStatus.FAILED.wireValue,
    WorkflowStatus.ABANDONED.wireValue,
    WorkflowStatus.BLOCKED.wireValue,
    WorkflowStatus.PAUSED.wireValue,
  )

data class WorkItem(
  val issueKey: String?,
  val workflowKind: WorkItemKind,
  val workflowId: String,
  val startedAt: Instant,
  val currentState: String,
  val stateEnteredAt: Instant,
  val stateEnteredAtEstimated: Boolean,
)
