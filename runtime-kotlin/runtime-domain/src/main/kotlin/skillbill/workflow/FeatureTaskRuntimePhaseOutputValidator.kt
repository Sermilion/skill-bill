package skillbill.workflow

import skillbill.error.InvalidFeatureTaskRuntimePhaseOutputSchemaError

/**
 * SKILL-65 Subtask 1: domain-owned validator port for feature-task-runtime
 * per-phase output payloads.
 *
 * Mirrors [DecompositionManifestValidator]: the concrete JSON-Schema validator
 * lives in `runtime-infra-fs`, and the rest of the runtime reaches it only
 * through this port. The runtime calls this at the per-phase completion seam to
 * raise the progression gate from PRESENCE to VALIDITY — a phase cannot advance
 * unless its output validates.
 *
 * To keep `runtime-domain` free of Jackson/Files and to avoid a raw
 * `Map<String, Any?>` boundary surface, the port accepts the phase output as a
 * JSON or YAML String. Implementations MUST parse and validate it against the
 * canonical schema, throwing [InvalidFeatureTaskRuntimePhaseOutputSchemaError]
 * on malformed input, a non-object root, empty `{}`, or any schema violation so
 * every parse/emission seam stays loud.
 *
 * The `sourceLabel` is the caller-supplied identifier (typically the phase id)
 * woven into the loud-fail message.
 */
interface FeatureTaskRuntimePhaseOutputValidator {
  /**
   * Parses [phaseOutputText] (JSON or YAML) into a phase-output object, then
   * validates it against the canonical per-phase output schema. Throws
   * [InvalidFeatureTaskRuntimePhaseOutputSchemaError] on malformed input, a
   * non-object root, empty `{}`, or any schema violation.
   */
  fun validatePhaseOutputText(phaseOutputText: String, sourceLabel: String)
}
