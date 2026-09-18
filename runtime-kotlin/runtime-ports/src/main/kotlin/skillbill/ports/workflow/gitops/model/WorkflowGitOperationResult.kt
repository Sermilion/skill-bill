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

  companion object {
    operator fun invoke(status: String, value: String = "", error: String = ""): WorkflowGitOperationResult =
      fromWire(status, value, error)

    fun fromWire(status: String, value: String = "", error: String = ""): WorkflowGitOperationResult = when (
      WorkflowGitOperationStatus.fromWire(status)
    ) {
      WorkflowGitOperationStatus.OK -> Ok(value = value, error = error)
      WorkflowGitOperationStatus.ERROR -> Failed(error = error.ifBlank { status }, value = value)
      null -> Failed(error = error.ifBlank { status }, value = value)
    }
  }
}

fun WorkflowGitOperationResult.recordsNothingToCommit(): Boolean {
  val text = "$error $value"
  return NOTHING_TO_COMMIT_MARKERS.any { marker -> marker in text }
}

private val NOTHING_TO_COMMIT_MARKERS = listOf(
  "no changes added to commit",
  "nothing to commit",
  "nothing added to commit",
)
