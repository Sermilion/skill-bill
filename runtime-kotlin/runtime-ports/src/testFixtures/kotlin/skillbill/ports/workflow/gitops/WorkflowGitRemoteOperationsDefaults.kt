package skillbill.ports.workflow.gitops

import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import java.nio.file.Path

abstract class WorkflowGitRemoteOperationsDefaults : WorkflowGitRemoteOperations {
  open override fun pushBranch(
    repoRoot: Path,
    branch: String,
  ): WorkflowGitOperationResult =
    WorkflowGitOperationResult.Failed(
      error = "This git operations implementation cannot push branch '$branch'.",
    )

  open override fun pushBranchWithLease(
    repoRoot: Path,
    branch: String,
  ): WorkflowGitOperationResult =
    WorkflowGitOperationResult.Failed(
      error = "This git operations implementation cannot push branch '$branch' under a lease.",
    )

  open override fun refreshRemoteBranch(
    repoRoot: Path,
    branch: String,
  ): WorkflowGitOperationResult = WorkflowGitOperationResult.Ok(value = branch.trim())

  open override fun localBranchHasUnpushedCommits(
    repoRoot: Path,
    branch: String,
  ): WorkflowGitOperationResult = WorkflowGitOperationResult.Ok(value = "false")
}
