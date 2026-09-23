package skillbill.ports.goalrunner.model

data class GoalRunnerResetSubtaskSnapshot(
  val id: Int,
  val status: String,
  val branch: String?,
  val workflowId: String?,
  val commitSha: String?,
  val blockedReason: String?,
  val lastResumableStep: String?,
)
