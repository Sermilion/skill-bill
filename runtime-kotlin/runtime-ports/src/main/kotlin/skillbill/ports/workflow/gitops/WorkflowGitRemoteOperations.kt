package skillbill.ports.workflow.gitops

import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import java.nio.file.Path

interface WorkflowGitRemoteOperations {
  companion object {
    const val ABSENT_REMOTE_BRANCH = "absent"
  }

  fun pushBranch(repoRoot: Path, branch: String): WorkflowGitOperationResult

  fun pushBranchWithLease(repoRoot: Path, branch: String): WorkflowGitOperationResult

  fun refreshRemoteBranch(repoRoot: Path, branch: String): WorkflowGitOperationResult

  fun localBranchHasUnpushedCommits(repoRoot: Path, branch: String): WorkflowGitOperationResult
}
