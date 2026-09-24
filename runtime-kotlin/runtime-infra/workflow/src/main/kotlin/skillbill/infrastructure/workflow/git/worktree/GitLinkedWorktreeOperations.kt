package skillbill.infrastructure.workflow.git.worktree

import skillbill.infrastructure.workflow.process.runGitCommand
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import skillbill.ports.workflow.gitops.worktree.LinkedWorktreeAddRequest
import skillbill.ports.workflow.gitops.worktree.LinkedWorktreeRemoveRequest
import skillbill.ports.workflow.gitops.worktree.WorkflowGitLinkedWorktreeOperations

object GitLinkedWorktreeOperations : WorkflowGitLinkedWorktreeOperations {
  override fun addLinkedWorktree(request: LinkedWorktreeAddRequest) {
    val result =
      runGitCommand(
        request.repositoryRoot,
        "worktree",
        "add",
        "-B",
        request.branchName,
        request.worktreePath.toString(),
        request.baseRef,
      )
    if (result !is WorkflowGitOperationResult.Ok) {
      error("Failed to add linked worktree at ${request.worktreePath}: $result")
    }
  }

  override fun removeLinkedWorktree(request: LinkedWorktreeRemoveRequest) {
    val result =
      runGitCommand(
        request.repositoryRoot,
        "worktree",
        "remove",
        "--force",
        request.worktreePath.toString(),
      )
    if (result !is WorkflowGitOperationResult.Ok) {
      error("Failed to remove linked worktree at ${request.worktreePath}: $result")
    }
  }
}
