package skillbill.ports.workflow.model

import skillbill.workflow.model.WorkflowStatus

enum class GoalChildWorkflowDeletionScope(val deletableStatuses: List<WorkflowStatus>) {
  TERMINAL_ONLY(
    listOf(
      WorkflowStatus.BLOCKED,
      WorkflowStatus.FAILED,
      WorkflowStatus.ABANDONED,
      WorkflowStatus.COMPLETED,
    ),
  ),
  TERMINAL_OR_RESUMABLE(
    listOf(
      WorkflowStatus.BLOCKED,
      WorkflowStatus.FAILED,
      WorkflowStatus.ABANDONED,
      WorkflowStatus.COMPLETED,
      WorkflowStatus.PENDING,
      WorkflowStatus.PAUSED,
    ),
  ),
}
