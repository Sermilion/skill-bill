package skillbill.engine.featuretask

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
    return emptyMap<String, Any?>()
  }

  override fun normalizePhaseOutput(
    phaseOutputText: String,
    sourceLabel: String,
  ): NormalizedFeatureTaskRuntimePhaseOutput {
    validatePhaseOutputText(phaseOutputText, sourceLabel)
    return NormalizedFeatureTaskRuntimePhaseOutput(phaseOutputText, emptyMap())
  }
}
