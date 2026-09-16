package skillbill.workflow.taskruntime

import skillbill.contracts.JsonCodec
import skillbill.error.FeatureTaskRuntimePhaseOutputFailureCode
import skillbill.error.InvalidFeatureTaskRuntimePhaseOutputSchemaError
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutputValidationResult
import skillbill.workflow.taskruntime.model.NormalizedFeatureTaskRuntimePhaseOutput

/**
 * Domain port for validating a phase's output payload; the concrete JSON-Schema
 * validator lives in `runtime-infra-fs`. Accepting JSON/YAML text keeps
 * `runtime-domain` free of Jackson and avoids a raw-map boundary surface.
 */
interface FeatureTaskRuntimePhaseOutputValidator {
  /**
   * Returns the versioned validation outcome. Infrastructure adapters override this
   * to add structural repair; the default keeps existing test doubles and callers
   * source-compatible while preserving the old normalization seam.
   */
  fun validatePhaseOutput(
    phaseOutputText: String,
    sourceLabel: String,
  ): FeatureTaskRuntimePhaseOutputValidationResult = try {
    FeatureTaskRuntimePhaseOutputValidationResult.AcceptedUnchanged(
      normalizePhaseOutput(phaseOutputText, sourceLabel),
    )
  } catch (error: InvalidFeatureTaskRuntimePhaseOutputSchemaError) {
    FeatureTaskRuntimePhaseOutputValidationResult.Rejected(
      code = FeatureTaskRuntimePhaseOutputFailureCode.fromWire(error.failureCode),
      reason = error.payloadFreeReason ?: "Phase output was rejected by the phase-output contract.",
      diagnosticReason = error.reason,
      payloadFreeReason = error.payloadFreeReason,
    )
  }

  /**
   * Parses [phaseOutputText] (JSON or YAML) and validates it against the canonical
   * per-phase output schema. Throws [InvalidFeatureTaskRuntimePhaseOutputSchemaError]
   * on malformed input, a non-object root, empty `{}`, or any schema violation.
   * [sourceLabel] (typically the phase id) is woven into the failure message.
   */
  fun validatePhaseOutputText(phaseOutputText: String, sourceLabel: String)

  fun validateAndReadPhaseOutput(phaseOutputText: String, sourceLabel: String): Any {
    validatePhaseOutputText(phaseOutputText, sourceLabel)
    return JsonCodec.parseObjectOrNull(phaseOutputText)
      ?.let(JsonCodec::jsonElementToValue)
      ?.let(JsonCodec::anyToStringAnyMap)
      ?: emptyMap<String, Any?>()
  }

  fun normalizePhaseOutput(phaseOutputText: String, sourceLabel: String): NormalizedFeatureTaskRuntimePhaseOutput {
    val envelope = JsonCodec.anyToStringAnyMap(validateAndReadPhaseOutput(phaseOutputText, sourceLabel))
      ?: emptyMap()
    return NormalizedFeatureTaskRuntimePhaseOutput(
      canonicalJson = JsonCodec.mapToJsonString(envelope),
      envelope = envelope,
    )
  }
}
