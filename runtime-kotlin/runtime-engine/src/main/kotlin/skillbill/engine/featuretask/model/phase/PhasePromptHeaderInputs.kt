package skillbill.engine.featuretask.model.phase
data class PhasePromptHeaderInputs(
  val issueKey: String,
  val phaseId: String,
  val agentRunValidateFallback: Boolean = false,
  val packCollectAllCommand: String? = null,
  val packConfirmationGateCommand: String? = null,
  val packBuildCommand: String? = null,
  val validationGateRepair: Boolean = false,
  val validationGateTriage: Boolean = false,
  val acceptanceCriteria: List<String> = emptyList(),
)
