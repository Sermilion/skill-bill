package skillbill.ports.workflow.gitops.model

sealed interface WorkflowGitNameListResult {
  data class Listed(val names: List<String>) : WorkflowGitNameListResult

  data class Failed(val error: String) : WorkflowGitNameListResult
}
