package skillbill.engine.goalrunner.model

import java.nio.file.Path

data class GoalRunnerPurgeRequest(
  val issueKey: String,
  val repoRoot: Path? = null,
) {
  init {
    require(issueKey.isNotBlank()) { "issueKey is required." }
  }
}

data class GoalRunnerPurgeResult(
  val issueKey: String,
  val parentWorkflowId: String?,
  val deletedChildWorkflowIds: List<String>,
  val specRestored: Boolean,
  val refusalReason: String? = null,
)
