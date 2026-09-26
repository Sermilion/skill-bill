package skillbill.ports.workflow.gitops.model

@JvmInline
value class WorkflowGitIndexSnapshot(val encoded: String) {
  companion object {
    val EMPTY = WorkflowGitIndexSnapshot("")
  }
}

sealed interface WorkflowGitIndexSnapshotResult {
  data class Captured(val snapshot: WorkflowGitIndexSnapshot) : WorkflowGitIndexSnapshotResult

  data class Failed(val error: String) : WorkflowGitIndexSnapshotResult
}
