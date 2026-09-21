package skillbill.ports.workflow.gitops

import skillbill.ports.workflow.gitops.worktree.LinkedWorktreeAddRequest
import skillbill.ports.workflow.gitops.worktree.LinkedWorktreeRemoveRequest
import skillbill.ports.workflow.gitops.worktree.WorkflowGitLinkedWorktreeOperations

object UnavailableWorkflowGitLinkedWorktreeOperations : WorkflowGitLinkedWorktreeOperations {
  override fun addLinkedWorktree(request: LinkedWorktreeAddRequest) {
    throw UnsupportedOperationException("Linked worktree add is unavailable in this harness.")
  }

  override fun removeLinkedWorktree(request: LinkedWorktreeRemoveRequest) {
    throw UnsupportedOperationException("Linked worktree remove is unavailable in this harness.")
  }
}
