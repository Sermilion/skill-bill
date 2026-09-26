package skillbill.ports.workflow.gitops

import skillbill.ports.workflow.gitops.model.WorkflowGitIndexSnapshot
import skillbill.ports.workflow.gitops.model.WorkflowGitIndexSnapshotResult
import skillbill.ports.workflow.gitops.model.WorkflowGitNameListResult
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import skillbill.ports.workflow.gitops.model.WorkflowPathContentIdentitiesResult
import java.nio.file.Path

interface ScopedStagingGitOperations {
  fun stagePaths(
    repoRoot: Path,
    paths: List<String>,
  ): WorkflowGitOperationResult

  fun captureIndexState(
    repoRoot: Path,
    paths: List<String>,
  ): WorkflowGitIndexSnapshotResult

  fun restoreIndexState(
    repoRoot: Path,
    paths: List<String>,
    snapshot: WorkflowGitIndexSnapshot,
  ): WorkflowGitOperationResult

  fun stagedPaths(repoRoot: Path): WorkflowGitNameListResult

  fun pathContentIdentities(
    repoRoot: Path,
    paths: List<String>,
  ): WorkflowPathContentIdentitiesResult
}
