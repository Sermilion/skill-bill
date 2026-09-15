package skillbill.cli

import skillbill.ports.workflow.gitops.RepositoryFingerprintGitOperations
import skillbill.ports.workflow.gitops.RepositoryOwnedPathsGitOperations
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import java.nio.file.Path

internal object TestRepositoryFingerprintOperations : RepositoryFingerprintGitOperations {
  override fun repositoryFingerprint(repoRoot: Path): WorkflowGitOperationResult =
    WorkflowGitOperationResult.Ok(value = "test-repository-fingerprint")
}

internal object TestRepositoryOwnedPathsOperations : RepositoryOwnedPathsGitOperations {
  override fun ownedPaths(repoRoot: Path): WorkflowGitOperationResult = WorkflowGitOperationResult.Ok(value = "")
}
