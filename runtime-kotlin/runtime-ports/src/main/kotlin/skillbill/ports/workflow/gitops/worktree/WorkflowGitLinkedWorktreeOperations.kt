package skillbill.ports.workflow.gitops.worktree

import skillbill.ports.workflow.gitops.model.LinkedWorktreeAddRequest as LinkedWorktreeAddRequestModel
import skillbill.ports.workflow.gitops.model.LinkedWorktreeRemoveRequest as LinkedWorktreeRemoveRequestModel

typealias LinkedWorktreeAddRequest = LinkedWorktreeAddRequestModel
typealias LinkedWorktreeRemoveRequest = LinkedWorktreeRemoveRequestModel

interface WorkflowGitLinkedWorktreeOperations {
  fun addLinkedWorktree(request: LinkedWorktreeAddRequest)

  fun removeLinkedWorktree(request: LinkedWorktreeRemoveRequest)
}
