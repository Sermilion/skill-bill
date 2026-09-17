package skillbill.engine.featuretask

import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutputValidationResult
import skillbill.workflow.taskruntime.model.NormalizedFeatureTaskRuntimePhaseOutput

object AlwaysValidValidator : FeatureTaskRuntimePhaseOutputTestValidator() {
  override fun validatePhaseOutput(
    phaseOutputText: String,
    sourceLabel: String,
  ): FeatureTaskRuntimePhaseOutputValidationResult =
    FeatureTaskRuntimePhaseOutputValidationResult.AcceptedUnchanged(normalizePhaseOutput(phaseOutputText, sourceLabel))

  override fun validatePhaseOutputText(phaseOutputText: String, sourceLabel: String) = Unit

  override fun validateAndReadPhaseOutput(phaseOutputText: String, sourceLabel: String): Any =
    emptyMap<String, Any?>()

  override fun normalizePhaseOutput(
    phaseOutputText: String,
    sourceLabel: String,
  ): NormalizedFeatureTaskRuntimePhaseOutput {
    return NormalizedFeatureTaskRuntimePhaseOutput(phaseOutputText, emptyMap())
  }
}
