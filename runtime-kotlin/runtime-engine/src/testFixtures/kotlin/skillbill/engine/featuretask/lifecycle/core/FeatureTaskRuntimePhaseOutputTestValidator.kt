package skillbill.engine.featuretask.lifecycle.core
import skillbill.contracts.JsonCodec
import skillbill.workflow.taskruntime.model.handoff.task.NormalizedFeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseOutputValidationResult
import skillbill.ports.taskruntime.FeatureTaskRuntimePhaseOutputValidator

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

  override fun validatePhaseOutputText(
    phaseOutputText: String,
    sourceLabel: String,
  ) = Unit

  override fun normalizePhaseOutput(
    phaseOutputText: String,
    sourceLabel: String,
  ): NormalizedFeatureTaskRuntimePhaseOutput {
    validatePhaseOutputText(phaseOutputText, sourceLabel)
    val envelope = phaseOutputMap(phaseOutputText)
    return NormalizedFeatureTaskRuntimePhaseOutput(
      canonicalJson = JsonCodec.mapToJsonString(envelope),
      envelope = envelope,
    )
  }

  private fun phaseOutputMap(phaseOutputText: String): Map<String, Any?> =
    JsonCodec.parseObjectOrNull(phaseOutputText)
      ?.let(JsonCodec::jsonElementToValue)
      ?.let(JsonCodec::anyToStringAnyMap)
      ?: error("fixture phase output is not an object")
}
