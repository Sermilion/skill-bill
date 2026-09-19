package skillbill.ports.workflow.gitops

import skillbill.ports.workflow.gitops.readiness.ReadinessTreeIdentityGitOperations

object NoopWorkflowGitOperations :
  WorkflowGitOperations,
  WorkflowGitBranchOperations by NoopWorkflowGitBranchOperations,
  WorkflowGitRemoteOperations by NoopWorkflowGitRemoteOperations,
  WorkflowGitCommitHistoryOperations by NoopWorkflowGitCommitHistoryOperations,
  WorkflowGitWorktreeOperations by NoopWorkflowGitWorktreeOperations,
  SuppressionEvidenceGitOperations by NoopSuppressionEvidenceGitOperations {
  override val checkpointHistoryOperations: CheckpointHistoryGitOperations =
    UnavailableCheckpointHistoryGitOperations

  override val goalSubtaskReviewOperations: GoalSubtaskReviewGitOperations = NoopGoalSubtaskReviewGitOperations

  override val repositoryFingerprintOperations: RepositoryFingerprintGitOperations =
    NoopRepositoryFingerprintGitOperations

  override val readinessTreeIdentityOperations: ReadinessTreeIdentityGitOperations =
    UnavailableReadinessTreeIdentityGitOperations

  override val repositoryOwnedPathsOperations: RepositoryOwnedPathsGitOperations =
    UnavailableRepositoryOwnedPathsGitOperations

  override val runtimePhaseFileManifestOperations: RuntimePhaseFileManifestGitOperations =
    NoopRuntimePhaseFileManifestGitOperations

  override val scopedStagingOperations: ScopedStagingGitOperations = UnavailableScopedStagingGitOperations
}
