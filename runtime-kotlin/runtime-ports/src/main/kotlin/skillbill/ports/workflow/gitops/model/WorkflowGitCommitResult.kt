package skillbill.ports.workflow.gitops.model

sealed interface WorkflowGitCommitResult {
  data class Committed(val commitSha: String) : WorkflowGitCommitResult

  data object NothingToCommit : WorkflowGitCommitResult

  data class Failed(val error: String) : WorkflowGitCommitResult
}
