package skillbill.ports.workflow.gitops.model

sealed interface WorkflowGitOperationResult {
  val value: String
  val error: String
  val wireValue: String
  val ok: Boolean get() = this is Ok
  val status: String get() = wireValue

  data class Ok(
    override val value: String = "",
    override val error: String = "",
  ) : WorkflowGitOperationResult {
    override val wireValue: String = WorkflowGitOperationStatus.OK.wireValue
  }

  data class Failed(
    override val error: String = "",
    override val value: String = "",
  ) : WorkflowGitOperationResult {
    override val wireValue: String = WorkflowGitOperationStatus.ERROR.wireValue
  }
}
