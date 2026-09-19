package skillbill.infrastructure.workflow.git.standard
import skillbill.infrastructure.workflow.decomposition.repoRoot
import skillbill.infrastructure.workflow.feature.error
import skillbill.infrastructure.workflow.feature.repoRoot
import skillbill.infrastructure.workflow.featuretask.error
import skillbill.infrastructure.workflow.featuretask.repoRoot
import skillbill.infrastructure.workflow.git.checkpoint.git
import skillbill.infrastructure.workflow.git.checkpoint.message
import skillbill.infrastructure.workflow.git.goal.error
import skillbill.infrastructure.workflow.git.goal.message
import skillbill.infrastructure.workflow.git.local.git
import skillbill.infrastructure.workflow.git.protected.git
import skillbill.infrastructure.workflow.git.repository.git
import skillbill.infrastructure.workflow.git.repository.repoRoot
import skillbill.infrastructure.workflow.git.scoped.git
import skillbill.infrastructure.workflow.git.suppression.git
import skillbill.infrastructure.workflow.git.workflow.error
import skillbill.infrastructure.workflow.git.workflow.git
import skillbill.infrastructure.workflow.git.workflow.repoRoot
import skillbill.infrastructure.workflow.process.runGitCommand
import skillbill.infrastructure.workflow.review.broker.error
import skillbill.infrastructure.workflow.review.specialists.coordinate.revision
import skillbill.infrastructure.workflow.review.specialists.system.repoRoot
import skillbill.ports.workflow.gitops.WorkflowGitCommitHistoryOperations
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import java.nio.file.Path

internal object GitStandardWorkflowGitCommitHistoryOperations : WorkflowGitCommitHistoryOperations {
  override fun createCommit(repoRoot: Path, message: String): WorkflowGitOperationResult =
    gitCreateCommit(repoRoot, message)

  override fun headCommitSha(repoRoot: Path): WorkflowGitOperationResult = runGitCommand(repoRoot, "rev-parse", "HEAD")

  override fun resetSoftToCommit(repoRoot: Path, commitSha: String): WorkflowGitOperationResult =
    gitResetSoftToCommit(repoRoot, commitSha)

  override fun resetHardToCommit(repoRoot: Path, commitSha: String): WorkflowGitOperationResult =
    gitResetHardToCommit(repoRoot, commitSha)

  override fun isCommitAncestor(
    repoRoot: Path,
    ancestorSha: String,
    descendantSha: String,
  ): WorkflowGitOperationResult = gitIsCommitAncestor(repoRoot, ancestorSha, descendantSha)

  override fun resolveCommit(repoRoot: Path, revision: String): WorkflowGitOperationResult =
    gitResolveCommit(repoRoot, revision)

  override fun readHeadTrackedFile(repoRoot: Path, repoRelativePath: String): WorkflowGitOperationResult {
    val path = repoRelativePath.trim().removePrefix("./")
    if (path.isBlank()) {
      return WorkflowGitOperationResult.Failed(error = "A repository-relative path is required.")
    }
    val tracked = runGitCommand(repoRoot, "ls-files", "--error-unmatch", path)
    if (tracked !is WorkflowGitOperationResult.Ok) {
      return WorkflowGitOperationResult.Failed(
        error = "Path '$path' is not tracked at HEAD (${tracked.error}).",
      )
    }
    return runGitCommand(repoRoot, "show", "HEAD:$path")
  }
}
