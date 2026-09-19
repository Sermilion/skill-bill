package skillbill.infrastructure.workflow.git.workflow
import skillbill.infrastructure.workflow.decomposition.repoRoot
import skillbill.infrastructure.workflow.feature.repoRoot
import skillbill.infrastructure.workflow.featuretask.repoRoot
import skillbill.infrastructure.workflow.git.checkpoint.GitCheckpointHistoryOperations
import skillbill.infrastructure.workflow.git.checkpoint.git
import skillbill.infrastructure.workflow.git.goal.GitGoalSubtaskReviewOperations
import skillbill.infrastructure.workflow.git.goal.value
import skillbill.infrastructure.workflow.git.local.git
import skillbill.infrastructure.workflow.git.protected.git
import skillbill.infrastructure.workflow.git.repository.git
import skillbill.infrastructure.workflow.git.repository.repoRoot
import skillbill.infrastructure.workflow.git.scoped.GitScopedStagingOperations
import skillbill.infrastructure.workflow.git.scoped.git
import skillbill.infrastructure.workflow.git.standard.GitStandardWorkflowGitOperations
import skillbill.infrastructure.workflow.git.suppression.git
import skillbill.infrastructure.workflow.process.runGitCommand
import skillbill.infrastructure.workflow.review.specialists.system.repoRoot
import skillbill.ports.workflow.gitops.CheckpointHistoryGitOperations
import skillbill.ports.workflow.gitops.GoalSubtaskReviewGitOperations
import skillbill.ports.workflow.gitops.RepositoryFingerprintGitOperations
import skillbill.ports.workflow.gitops.RepositoryOwnedPathsGitOperations
import skillbill.ports.workflow.gitops.RuntimePhaseFileManifestGitOperations
import skillbill.ports.workflow.gitops.ScopedStagingGitOperations
import skillbill.ports.workflow.gitops.SuppressionEvidenceGitOperations
import skillbill.ports.workflow.gitops.WorkflowGitBranchOperations
import skillbill.ports.workflow.gitops.WorkflowGitCommitHistoryOperations
import skillbill.ports.workflow.gitops.WorkflowGitOperations
import skillbill.ports.workflow.gitops.WorkflowGitRemoteOperations
import skillbill.ports.workflow.gitops.WorkflowGitWorktreeOperations
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import java.nio.file.Path

class GitWorkflowGitOperations :
  WorkflowGitOperations,
  WorkflowGitBranchOperations by GitStandardWorkflowGitOperations,
  WorkflowGitRemoteOperations by GitStandardWorkflowGitOperations,
  WorkflowGitCommitHistoryOperations by GitStandardWorkflowGitOperations,
  WorkflowGitWorktreeOperations by GitStandardWorkflowGitOperations,
  SuppressionEvidenceGitOperations by GitSuppressionEvidenceOperations {
  override val checkpointHistoryOperations: CheckpointHistoryGitOperations = GitCheckpointHistoryOperations
  override val goalSubtaskReviewOperations: GoalSubtaskReviewGitOperations = GitGoalSubtaskReviewOperations
  override val scopedStagingOperations: ScopedStagingGitOperations = GitScopedStagingOperations
  override val runtimePhaseFileManifestOperations: RuntimePhaseFileManifestGitOperations =
    GitRuntimePhaseFileManifestOperations
  override val repositoryFingerprintOperations: RepositoryFingerprintGitOperations = GitRepositoryFingerprintOperations
  override val repositoryOwnedPathsOperations: RepositoryOwnedPathsGitOperations = GitRepositoryOwnedPathsOperations
}

internal object GitRepositoryOwnedPathsOperations : RepositoryOwnedPathsGitOperations {
  override fun ownedPaths(repoRoot: Path): WorkflowGitOperationResult {
    val untracked = runGitCommand(repoRoot, "ls-files", "--others", "--exclude-standard", "-z")
    if (untracked !is WorkflowGitOperationResult.Ok) return untracked
    val tracked = runGitCommand(repoRoot, "diff", "--name-only", "-z", "HEAD")

    val trackedValue = tracked.value.takeIf { tracked is WorkflowGitOperationResult.Ok }.orEmpty()
    return WorkflowGitOperationResult.Ok(

      value = untracked.value.orEmpty() + trackedValue,
    )
  }
}
