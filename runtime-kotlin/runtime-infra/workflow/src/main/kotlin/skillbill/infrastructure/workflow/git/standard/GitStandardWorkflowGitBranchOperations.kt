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
import skillbill.infrastructure.workflow.process.runGitCommand
import skillbill.infrastructure.workflow.review.specialists.system.repoRoot
import skillbill.ports.workflow.gitops.WorkflowGitBranchOperations
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import java.nio.file.Path

internal object GitStandardWorkflowGitBranchOperations : WorkflowGitBranchOperations {
  override fun checkoutBranch(repoRoot: Path, branch: String, baseBranch: String?): WorkflowGitOperationResult =
    gitCheckoutBranch(repoRoot, branch, baseBranch)

  override fun branchExists(repoRoot: Path, branch: String): WorkflowGitOperationResult =
    gitBranchExists(repoRoot, branch)

  override fun currentBranch(repoRoot: Path): WorkflowGitOperationResult =
    runGitCommand(repoRoot, "branch", "--show-current")

  override fun validateBranchBase(
    repoRoot: Path,
    branch: String,
    expectedBaseBranch: String,
  ): WorkflowGitOperationResult = gitValidateBranchBase(repoRoot, branch, expectedBaseBranch)
}
