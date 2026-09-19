package skillbill.engine.featuretask.model.core
data class FeatureTaskRuntimeAgentAssignment(
  val perPhaseAgentIds: Map<String, String> = emptyMap(),
  val override: String? = null,
) {
  init {
    perPhaseAgentIds.forEach { (phaseId, agentId) ->
      require(phaseId.isNotBlank()) {
        "FeatureTaskRuntimeAgentAssignment.perPhaseAgentIds must not contain a blank phase id."
      }
      require(agentId.isNotBlank()) {
        "FeatureTaskRuntimeAgentAssignment.perPhaseAgentIds['$phaseId'] must not map to a blank agent id."
      }
    }
    override?.let { value ->
      require(value.isNotBlank()) {
        "FeatureTaskRuntimeAgentAssignment.override must not be blank when provided."
      }
    }
  }
}

data class FeatureTaskRuntimeResolvedPhaseAgent(
  val phaseId: String,
  val invokedAgentId: String,
  val configuredAgentOverrideId: String?,
) {

  val resolvedAgentId: String = configuredAgentOverrideId ?: invokedAgentId

  init {
    require(phaseId.isNotBlank()) { "FeatureTaskRuntimeResolvedPhaseAgent.phaseId must be non-blank." }
    require(invokedAgentId.isNotBlank()) { "FeatureTaskRuntimeResolvedPhaseAgent.invokedAgentId must be non-blank." }
    configuredAgentOverrideId?.let { value ->
      require(value.isNotBlank()) {
        "FeatureTaskRuntimeResolvedPhaseAgent.configuredAgentOverrideId must not be blank when provided."
      }
    }
  }
}
