package skillbill.infrastructure.workflow.git.standard
import skillbill.infrastructure.workflow.decomposition.repoRoot
import skillbill.infrastructure.workflow.feature.repoRoot
import skillbill.infrastructure.workflow.feature.request
import skillbill.infrastructure.workflow.featuretask.repoRoot
import skillbill.infrastructure.workflow.featuretask.request
import skillbill.infrastructure.workflow.git.checkpoint.git
import skillbill.infrastructure.workflow.git.local.git
import skillbill.infrastructure.workflow.git.protected.git
import skillbill.infrastructure.workflow.git.repository.git
import skillbill.infrastructure.workflow.git.repository.repoRoot
import skillbill.infrastructure.workflow.git.scoped.git
import skillbill.infrastructure.workflow.git.suppression.git
import skillbill.infrastructure.workflow.git.workflow.GitRepositoryFingerprintOperations
import skillbill.infrastructure.workflow.git.workflow.git
import skillbill.infrastructure.workflow.git.workflow.repoRoot
import skillbill.infrastructure.workflow.git.workflow.selectedDiffHunks
import skillbill.infrastructure.workflow.git.workflow.worktreeActivity
import skillbill.infrastructure.workflow.git.workflow.worktreeNumstat
import skillbill.infrastructure.workflow.process.runGitCommand
import skillbill.infrastructure.workflow.review.broker.request
import skillbill.infrastructure.workflow.review.specialists.system.repoRoot
import skillbill.ports.workflow.gitops.WorkflowGitWorktreeOperations
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import skillbill.ports.workflow.gitops.model.WorkflowSelectedDiffHunksRequest
import skillbill.ports.workflow.gitops.model.WorkflowSelectedDiffHunksResult
import skillbill.ports.workflow.gitops.model.WorkflowWorktreeActivityResult
import skillbill.ports.workflow.gitops.model.WorkflowWorktreeNumstatResult
import java.nio.file.Path

internal object GitStandardWorkflowGitWorktreeOperations : WorkflowGitWorktreeOperations {
  override fun stageAll(repoRoot: Path): WorkflowGitOperationResult = runGitCommand(repoRoot, "add", "-A")

  override fun worktreeStatus(repoRoot: Path): WorkflowGitOperationResult =
    runGitCommand(repoRoot, "status", "--porcelain", "-uall")

  override fun worktreeActivity(repoRoot: Path): WorkflowWorktreeActivityResult =
    GitRepositoryFingerprintOperations.worktreeActivity(repoRoot)

  override fun worktreeNumstat(repoRoot: Path): WorkflowWorktreeNumstatResult =
    GitRepositoryFingerprintOperations.worktreeNumstat(repoRoot)

  override fun selectedDiffHunks(
    repoRoot: Path,
    request: WorkflowSelectedDiffHunksRequest,
  ): WorkflowSelectedDiffHunksResult = GitRepositoryFingerprintOperations.selectedDiffHunks(repoRoot, request)
}
