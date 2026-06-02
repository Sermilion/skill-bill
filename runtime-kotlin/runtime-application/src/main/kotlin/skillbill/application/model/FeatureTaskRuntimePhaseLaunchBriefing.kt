package skillbill.application.model

import skillbill.boundary.OpenBoundaryMap
import skillbill.contracts.JsonSupport
import skillbill.error.InvalidWorkflowStateSchemaError

/**
 * SKILL-65 Subtask 3 (AC2, AC8): the typed, fully-assembled launch briefing for
 * one phase, produced by
 * [skillbill.application.FeatureTaskRuntimePhaseBriefingAssembler] from the pure
 * domain [skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseHandoff].
 *
 * It carries the three handoff layers as typed fields plus a deterministic
 * serialized [briefingText] the runner hands to the launcher. The launched agent
 * does NOT select its own inputs: every field here is derived from the phase's
 * static declaration and the run-invariants, never from the agent.
 *
 * Run-invariants (layer 1) are present unconditionally on EVERY phase; the type
 * shape makes that a construction guarantee rather than a runtime option.
 */
data class FeatureTaskRuntimePhaseLaunchBriefing(
  val phaseId: String,
  /** Layer 1: run-invariants injected into every phase (spec, criteria, mandates). */
  val specReference: String,
  val acceptanceCriteria: List<String>,
  val mandatesAndOverrides: List<String>,
  /** Layer 2: latest-iteration upstream outputs, keyed by producing phase id. */
  val upstreamOutputsByPhaseId: Map<String, String>,
  /** Layer 3: statically-declared derived-context keys (e.g. "diff" for review). */
  val derivedContextKeys: List<String>,
  /** Deterministic, human-and-agent-readable serialization of all three layers. */
  val briefingText: String,
) {
  init {
    require(phaseId.isNotBlank()) { "FeatureTaskRuntimePhaseLaunchBriefing.phaseId must be non-blank." }
    require(specReference.isNotBlank()) {
      "FeatureTaskRuntimePhaseLaunchBriefing.specReference must be non-blank; run-invariants are unconditional."
    }
    require(acceptanceCriteria.isNotEmpty()) {
      "FeatureTaskRuntimePhaseLaunchBriefing.acceptanceCriteria must be non-empty; run-invariants are unconditional."
    }
    require(briefingText.isNotBlank()) { "FeatureTaskRuntimePhaseLaunchBriefing.briefingText must be non-blank." }
  }

  /**
   * SKILL-65 Subtask 3 (AC2/AC7/AC8): serializes the assembled briefing for the
   * durable per-phase briefing artifact store, preserving all three handoff
   * layers so a consumer can read the unconditional run-invariants, the
   * latest-iteration upstream outputs, and the declared derived-context keys back
   * out by phase id.
   */
  @OpenBoundaryMap("Feature-task-runtime per-phase launch briefing artifact map at the durable workflow-artifact seam")
  fun toArtifactMap(): Map<String, Any?> = linkedMapOf(
    "phase_id" to phaseId,
    "spec_reference" to specReference,
    "acceptance_criteria" to acceptanceCriteria,
    "mandates_and_overrides" to mandatesAndOverrides,
    "upstream_outputs_by_phase_id" to LinkedHashMap(upstreamOutputsByPhaseId),
    "derived_context_keys" to derivedContextKeys,
    "briefing_text" to briefingText,
  )

  companion object {
    /**
     * SKILL-65 Subtask 3 (AC7): strict decode of one persisted briefing map.
     * Loud-fails with a typed [InvalidWorkflowStateSchemaError] on any
     * missing/malformed field, mirroring the per-phase record/ledger read seams;
     * no best-effort parsing.
     */
    @OpenBoundaryMap("Feature-task-runtime per-phase launch briefing decode from the durable workflow-artifact map")
    fun fromArtifactMap(raw: Map<String, Any?>): FeatureTaskRuntimePhaseLaunchBriefing =
      FeatureTaskRuntimePhaseLaunchBriefing(
        phaseId = raw.requireStringField("phase_id"),
        specReference = raw.requireStringField("spec_reference"),
        acceptanceCriteria = raw.requireStringListField("acceptance_criteria"),
        mandatesAndOverrides = raw.requireStringListField("mandates_and_overrides"),
        upstreamOutputsByPhaseId = raw.requireStringMapField("upstream_outputs_by_phase_id"),
        derivedContextKeys = raw.requireStringListField("derived_context_keys"),
        briefingText = raw.requireStringField("briefing_text"),
      )

    // Single loud-fail seam so each strict decoder stays within the throw-count
    // budget while still mirroring the typed schema-error behavior of the
    // per-phase record/ledger read seams.
    private fun schemaError(detail: String): Nothing = throw InvalidWorkflowStateSchemaError(detail)

    private fun Map<String, Any?>.requireStringField(key: String): String {
      val value = this[key] ?: schemaError("Feature-task-runtime briefing artifact map is missing field '$key'.")
      return (value as? String)?.takeIf(String::isNotBlank)
        ?: schemaError("Feature-task-runtime briefing artifact field '$key' must decode to a non-blank string.")
    }

    private fun Map<String, Any?>.requireStringListField(key: String): List<String> {
      val list = (if (containsKey(key)) this[key] else schemaError(missingMessage(key, "list"))) as? List<*>
        ?: schemaError("Feature-task-runtime briefing artifact field '$key' must decode to a list.")
      return list.map { element ->
        element as? String ?: schemaError("Feature-task-runtime briefing artifact field '$key' must contain strings.")
      }
    }

    private fun Map<String, Any?>.requireStringMapField(key: String): Map<String, String> {
      val rawValue = if (containsKey(key)) this[key] else schemaError(missingMessage(key, "map"))
      val map = JsonSupport.anyToStringAnyMap(rawValue)
        ?: schemaError("Feature-task-runtime briefing artifact field '$key' must decode to a string-keyed map.")
      return map.entries.associate { (mapKey, mapValue) ->
        mapKey to (
          mapValue as? String
            ?: schemaError("Feature-task-runtime briefing artifact field '$key' must map to string values.")
          )
      }
    }

    private fun missingMessage(key: String, kind: String): String =
      "Feature-task-runtime briefing artifact map is missing required $kind field '$key'."
  }
}
