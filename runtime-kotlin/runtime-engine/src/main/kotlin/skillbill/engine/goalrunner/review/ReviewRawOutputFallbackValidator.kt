package skillbill.engine.goalrunner.review

import skillbill.contracts.JsonCodec
import skillbill.error.shellcontent.InvalidFeatureTaskRuntimePhaseOutputSchemaError
import skillbill.ports.taskruntime.FeatureTaskRuntimePhaseOutputValidator
import skillbill.workflow.taskruntime.model.handoff.task.NormalizedFeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseOutputValidationResult

object ReviewRawOutputFallbackValidator : FeatureTaskRuntimePhaseOutputValidator {
  override fun validatePhaseOutput(
    phaseOutputText: String,
    sourceLabel: String,
  ): FeatureTaskRuntimePhaseOutputValidationResult =
    FeatureTaskRuntimePhaseOutputValidationResult.AcceptedUnchanged(
      normalizePhaseOutput(phaseOutputText, sourceLabel),
    )

  override fun validatePhaseOutputText(
    phaseOutputText: String,
    sourceLabel: String,
  ) {
    validatePhaseOutput(phaseOutputText, sourceLabel)
  }

  override fun normalizePhaseOutput(
    phaseOutputText: String,
    sourceLabel: String,
  ): NormalizedFeatureTaskRuntimePhaseOutput {
    val parsed =
      JsonCodec.parseObjectOrNull(phaseOutputText)
        ?: throw InvalidFeatureTaskRuntimePhaseOutputSchemaError(
          sourceLabel = sourceLabel,
          reason = "must be a JSON object when no runtime schema validator is injected.",
        )
    val envelope =
      parsed
        .let(JsonCodec::jsonElementToValue)
        .let(JsonCodec::anyToStringAnyMap)
        ?: throw InvalidFeatureTaskRuntimePhaseOutputSchemaError(
          sourceLabel = sourceLabel,
          reason = "must decode to a string-keyed object when no runtime schema validator is injected.",
        )
    return NormalizedFeatureTaskRuntimePhaseOutput(
      canonicalJson = JsonCodec.mapToJsonString(envelope),
      envelope = envelope,
    )
  }
}
