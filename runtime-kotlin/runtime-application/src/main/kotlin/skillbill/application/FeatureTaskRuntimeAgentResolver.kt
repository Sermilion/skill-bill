package skillbill.application

import skillbill.application.model.FeatureTaskRuntimeAgentAssignment
import skillbill.application.model.FeatureTaskRuntimeResolvedPhaseAgent

/**
 * Pure per-phase agent resolver. The run-wide override is independent and wins at the launch
 * seam; the invoked side resolves in order:
 *
 *   per-phase map entry -> invoked agent id -> `SKILL_BILL_AGENT` env -> invoking agent (default)
 *
 * The default is the invoking agent id; there is no hardcoded fallback.
 */
object FeatureTaskRuntimeAgentResolver {
  /**
   * Resolves the effective agent for [phaseId]. [invokedAgentId] is the run's already-resolved
   * invoking agent (must be non-blank); [environment] supplies the optional `SKILL_BILL_AGENT`
   * fallback consulted only when neither a per-phase entry nor the invoked agent is usable.
   */
  fun resolve(
    phaseId: String,
    assignment: FeatureTaskRuntimeAgentAssignment,
    invokedAgentId: String,
    environment: Map<String, String> = emptyMap(),
  ): FeatureTaskRuntimeResolvedPhaseAgent {
    require(phaseId.isNotBlank()) { "FeatureTaskRuntimeAgentResolver.resolve requires a non-blank phaseId." }
    require(invokedAgentId.isNotBlank()) {
      "FeatureTaskRuntimeAgentResolver.resolve requires a non-blank invokedAgentId; the invoking agent is the " +
        "documented default and must always be present (no hardcoded codex fallback)."
    }
    val resolvedInvoked = assignment.perPhaseAgentIds[phaseId]?.takeIf(String::isNotBlank)
      ?: invokedAgentId.takeIf(String::isNotBlank)
      ?: environment[SKILL_BILL_AGENT_ENV]?.takeIf(String::isNotBlank)
      ?: invokedAgentId
    return FeatureTaskRuntimeResolvedPhaseAgent(
      phaseId = phaseId,
      invokedAgentId = resolvedInvoked,
      configuredAgentOverrideId = assignment.override?.takeIf(String::isNotBlank),
    )
  }

  private const val SKILL_BILL_AGENT_ENV = "SKILL_BILL_AGENT"
}
