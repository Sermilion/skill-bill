package skillbill.engine.featuretask.lifecycle.core
import skillbill.contracts.JsonCodec
import skillbill.workflow.taskruntime.model.handoff.task.NormalizedFeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseOutputValidationResult

object AlwaysValidValidator : FeatureTaskRuntimePhaseOutputTestValidator() {
  override fun validatePhaseOutput(
    phaseOutputText: String,
    sourceLabel: String,
  ): FeatureTaskRuntimePhaseOutputValidationResult =
    FeatureTaskRuntimePhaseOutputValidationResult.AcceptedUnchanged(normalizePhaseOutput(phaseOutputText, sourceLabel))

  override fun validatePhaseOutputText(
    phaseOutputText: String,
    sourceLabel: String,
  ) = Unit

  override fun normalizePhaseOutput(
    phaseOutputText: String,
    sourceLabel: String,
  ): NormalizedFeatureTaskRuntimePhaseOutput = normalizedPhaseOutput(phaseOutputText)

  private fun normalizedPhaseOutput(phaseOutputText: String): NormalizedFeatureTaskRuntimePhaseOutput {
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
