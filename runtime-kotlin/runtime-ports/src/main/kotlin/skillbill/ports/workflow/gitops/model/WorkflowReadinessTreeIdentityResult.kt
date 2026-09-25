package skillbill.ports.workflow.gitops.model

sealed interface WorkflowReadinessTreeIdentityResult {
  data class Resolved(val identity: ReadinessTreeIdentity) : WorkflowReadinessTreeIdentityResult

  data class Failed(val error: String) : WorkflowReadinessTreeIdentityResult
}
