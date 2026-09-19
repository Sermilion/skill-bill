package skillbill.infrastructure.workflow.git.standard
import skillbill.infrastructure.workflow.decomposition.repoRoot
import skillbill.infrastructure.workflow.feature.repoRoot
import skillbill.infrastructure.workflow.featuretask.repoRoot
import skillbill.infrastructure.workflow.git.checkpoint.branch
import skillbill.infrastructure.workflow.git.checkpoint.git
import skillbill.infrastructure.workflow.git.goal.branch
import skillbill.infrastructure.workflow.git.local.git
import skillbill.infrastructure.workflow.git.protected.git
import skillbill.infrastructure.workflow.git.repository.git
import skillbill.infrastructure.workflow.git.repository.repoRoot
import skillbill.infrastructure.workflow.git.scoped.git
import skillbill.infrastructure.workflow.git.suppression.git
import skillbill.infrastructure.workflow.git.workflow.branch
import skillbill.infrastructure.workflow.git.workflow.git
import skillbill.infrastructure.workflow.git.workflow.repoRoot
import skillbill.infrastructure.workflow.review.specialists.system.repoRoot
import skillbill.ports.workflow.gitops.WorkflowGitRemoteOperations
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import java.nio.file.Path

internal object GitStandardWorkflowGitRemoteOperations : WorkflowGitRemoteOperations {
  override fun pushBranch(repoRoot: Path, branch: String): WorkflowGitOperationResult =
    gitPushBranch(repoRoot, branch, withLease = false)

  override fun pushBranchWithLease(repoRoot: Path, branch: String): WorkflowGitOperationResult =
    gitPushBranch(repoRoot, branch, withLease = true)

  override fun refreshRemoteBranch(repoRoot: Path, branch: String): WorkflowGitOperationResult =
    gitFetchRemoteBranch(repoRoot, branch)

  override fun localBranchHasUnpushedCommits(repoRoot: Path, branch: String): WorkflowGitOperationResult =
    gitLocalBranchHasUnpushedCommits(repoRoot, branch)
}
