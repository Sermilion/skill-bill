package skillbill.ports.workflow.gitops.model

sealed interface WorkflowPathContentIdentitiesResult {
  data class Resolved(val identities: Map<String, String>) : WorkflowPathContentIdentitiesResult

  data class Failed(val error: String) : WorkflowPathContentIdentitiesResult
}
