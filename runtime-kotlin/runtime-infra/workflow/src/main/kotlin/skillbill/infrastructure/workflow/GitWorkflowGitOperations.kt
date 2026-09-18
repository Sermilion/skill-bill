package skillbill.infrastructure.workflow

import skillbill.infrastructure.workflow.process.runGitCommand
import skillbill.ports.workflow.gitops.CheckpointHistoryGitOperations
import skillbill.ports.workflow.gitops.GoalSubtaskReviewGitOperations
import skillbill.ports.workflow.gitops.RepositoryFingerprintGitOperations
import skillbill.ports.workflow.gitops.RepositoryOwnedPathsGitOperations
import skillbill.ports.workflow.gitops.RuntimePhaseFileManifestGitOperations
import skillbill.ports.workflow.gitops.ScopedStagingGitOperations
import skillbill.ports.workflow.gitops.SuppressionEvidenceGitOperations
import skillbill.ports.workflow.gitops.SuppressionEvidenceGitOperationsProvider
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
  SuppressionEvidenceGitOperationsProvider {
  override val checkpointHistoryOperations: CheckpointHistoryGitOperations = GitCheckpointHistoryOperations
  override val goalSubtaskReviewOperations: GoalSubtaskReviewGitOperations = GitGoalSubtaskReviewOperations
  override val scopedStagingOperations: ScopedStagingGitOperations = GitScopedStagingOperations
  override val runtimePhaseFileManifestOperations: RuntimePhaseFileManifestGitOperations =
    GitRuntimePhaseFileManifestOperations
  override val repositoryFingerprintOperations: RepositoryFingerprintGitOperations = GitRepositoryFingerprintOperations
  override val repositoryOwnedPathsOperations: RepositoryOwnedPathsGitOperations = GitRepositoryOwnedPathsOperations
  override val suppressionEvidenceOperations: SuppressionEvidenceGitOperations = GitSuppressionEvidenceOperations
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
