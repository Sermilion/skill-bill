package skillbill.engine.featuretask

import skillbill.contracts.JsonCodec
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseOutputValidator
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutputValidationResult
import skillbill.workflow.taskruntime.model.NormalizedFeatureTaskRuntimePhaseOutput

abstract class FeatureTaskRuntimePhaseOutputTestValidator : FeatureTaskRuntimePhaseOutputValidator {
  override fun validatePhaseOutput(
    phaseOutputText: String,
    sourceLabel: String,
  ): FeatureTaskRuntimePhaseOutputValidationResult {
    validatePhaseOutputText(phaseOutputText, sourceLabel)
    return FeatureTaskRuntimePhaseOutputValidationResult.AcceptedUnchanged(
      normalizePhaseOutput(phaseOutputText, sourceLabel),
    )
  }

  override fun validatePhaseOutputText(phaseOutputText: String, sourceLabel: String) = Unit

  override fun validateAndReadPhaseOutput(phaseOutputText: String, sourceLabel: String): Any {
    validatePhaseOutputText(phaseOutputText, sourceLabel)
    return phaseOutputMap(phaseOutputText)
  }

  override fun normalizePhaseOutput(
    phaseOutputText: String,
    sourceLabel: String,
  ): NormalizedFeatureTaskRuntimePhaseOutput {
    val envelope = JsonCodec.anyToStringAnyMap(validateAndReadPhaseOutput(phaseOutputText, sourceLabel))
      ?: error("fixture phase output is not a string-keyed object")
    return NormalizedFeatureTaskRuntimePhaseOutput(
      canonicalJson = JsonCodec.mapToJsonString(envelope),
      envelope = envelope,
    )
  }

  private fun phaseOutputMap(phaseOutputText: String): Map<String, Any?> = JsonCodec.parseObjectOrNull(phaseOutputText)
    ?.let(JsonCodec::jsonElementToValue)
    ?.let(JsonCodec::anyToStringAnyMap)
    ?: error("fixture phase output is not an object")
}
