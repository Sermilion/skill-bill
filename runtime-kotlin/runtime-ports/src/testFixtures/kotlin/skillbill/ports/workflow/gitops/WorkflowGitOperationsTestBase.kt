package skillbill.ports.workflow.gitops

import skillbill.ports.workflow.gitops.model.WorkflowGitOperationStatus
import skillbill.ports.workflow.gitops.model.WorkflowWorktreeNumstatResult
import skillbill.ports.workflow.gitops.readiness.ReadinessTreeIdentityGitOperations
import java.nio.file.Path

abstract class WorkflowGitOperationsTestBase :
  WorkflowGitRemoteOperationsDefaults(),
  WorkflowGitOperations {
  override fun worktreeNumstat(repoRoot: Path): WorkflowWorktreeNumstatResult =
    WorkflowWorktreeNumstatResult(status = WorkflowGitOperationStatus.OK, files = emptyList())

  override val checkpointHistoryOperations: CheckpointHistoryGitOperations =
    UnavailableCheckpointHistoryGitOperations

  override val goalSubtaskReviewOperations: GoalSubtaskReviewGitOperations = NoopGoalSubtaskReviewGitOperations

  override val repositoryFingerprintOperations: RepositoryFingerprintGitOperations =
    UnavailableRepositoryFingerprintGitOperations

  override val readinessTreeIdentityOperations: ReadinessTreeIdentityGitOperations =
    UnavailableReadinessTreeIdentityGitOperations

  override val repositoryOwnedPathsOperations: RepositoryOwnedPathsGitOperations =
    UnavailableRepositoryOwnedPathsGitOperations

  override val runtimePhaseFileManifestOperations: RuntimePhaseFileManifestGitOperations =
    NoopRuntimePhaseFileManifestGitOperations

  override val scopedStagingOperations: ScopedStagingGitOperations = UnavailableScopedStagingGitOperations

  override fun scopedPathContentsAgainstBase(repoRoot: Path, baseRef: String, headPaths: List<String>) =
    NoopSuppressionEvidenceGitOperations.scopedPathContentsAgainstBase(repoRoot, baseRef, headPaths)
}
