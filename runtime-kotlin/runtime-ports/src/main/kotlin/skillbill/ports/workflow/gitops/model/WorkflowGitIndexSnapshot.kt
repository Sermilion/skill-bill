package skillbill.ports.workflow.gitops.model

/**
 * Index state captured by the git adapter. Callers hand it back to `restoreIndexState` unread;
 * only the adapter that produced it knows how its records are encoded.
 */
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
