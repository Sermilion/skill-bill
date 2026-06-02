package skillbill.application

import skillbill.application.model.FeatureTaskRuntimeAgentAssignment
import skillbill.application.model.FeatureTaskRuntimeResolvedPhaseAgent

/**
 * SKILL-65 Subtask 3 (AC6): pure per-phase agent resolver for the
 * feature-task-runtime pipeline.
 *
 * Resolution order (mirrors the SKILL-64 `GoalCliCommands.resolveInvokedAgentId`
 * precedent, lifted to a per-phase map): the run-wide override is independent
 * and wins at the launch seam (`AgentRunService` resolves `override ?: invoked`);
 * the invoked side resolves
 *
 *   per-phase map entry -> invoked agent id -> `SKILL_BILL_AGENT` env -> default
 *
 * where the documented default is the INVOKING agent id — there is NO hardcoded
 * `codex` fallback (the SKILL-64 fix). The invoked agent id passed in is the one
 * the caller already resolved for the run (explicit flag / env / detected
 * context); this resolver only adds the per-phase layering on top of it.
 *
 * The resolver is pure and deterministic so it is unit-testable without any
 * runtime/process dependency.
 */
object FeatureTaskRuntimeAgentResolver {
  /**
   * Resolves the effective agent for [phaseId]. [invokedAgentId] is the run's
   * already-resolved invoking agent (must be non-blank). [environment] supplies
   * the optional `SKILL_BILL_AGENT` fallback consulted only when neither a
   * per-phase entry nor the invoked agent is usable.
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
