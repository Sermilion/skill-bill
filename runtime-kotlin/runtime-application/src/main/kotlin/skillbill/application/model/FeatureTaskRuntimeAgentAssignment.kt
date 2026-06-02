package skillbill.application.model

/**
 * SKILL-65 Subtask 3 (AC6): static per-phase agent assignment for the
 * feature-task-runtime pipeline.
 *
 * Carries the design-time per-phase agent map (phase id -> configured agent id)
 * plus an optional run-wide [override] that wins over everything at the launch
 * seam. There is intentionally no API that lets a running agent choose its own
 * per-phase assignment; the map is a property of the run request.
 *
 * The resolution order is owned by [skillbill.application.FeatureTaskRuntimeAgentResolver]
 * and mirrors the SKILL-64 precedent (`GoalCliCommands.resolveInvokedAgentId`):
 * the invoking agent is the documented default — there is NO hardcoded `codex`
 * fallback.
 */
data class FeatureTaskRuntimeAgentAssignment(
  /** Phase id -> configured agent id for that phase. Entries must be non-blank. */
  val perPhaseAgentIds: Map<String, String> = emptyMap(),
  /** Optional run-wide override applied to EVERY phase at the launch seam. */
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

/**
 * SKILL-65 Subtask 3 (AC6): the resolved effective agent ids for one phase. The
 * runner feeds [invokedAgentId] / [configuredAgentOverrideId] to the existing
 * `AgentRunService` (which resolves `override ?: invoked` at the launch seam)
 * and stamps [resolvedAgentId] onto the per-phase record/ledger.
 */
data class FeatureTaskRuntimeResolvedPhaseAgent(
  val phaseId: String,
  /** The non-override side of the resolution (per-phase -> invoked -> env -> default). */
  val invokedAgentId: String,
  /** The run-wide override, when one was supplied; wins at the launch seam. */
  val configuredAgentOverrideId: String?,
) {
  /**
   * The agent that will actually execute the phase: the override when present,
   * otherwise the resolved invoked agent. This is the value recorded as the
   * per-phase `resolved_agent_id`.
   */
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
