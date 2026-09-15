package skillbill.infrastructure.fs

import me.tatarka.inject.annotations.Inject
import skillbill.infrastructure.fs.contracts.workflow.FeatureTaskRuntimePlanningProjectionSchemaValidator
import skillbill.workflow.taskruntime.FeatureTaskRuntimePlanningProjectionValidator

@Inject
class FeatureTaskRuntimePlanningProjectionValidatorAdapter : FeatureTaskRuntimePlanningProjectionValidator {
  override fun validatePlanningProjection(producedOutputs: Any, sourceLabel: String) {
    FeatureTaskRuntimePlanningProjectionSchemaValidator.validate(
      requireFeatureTaskRuntimeArtifactMap(producedOutputs, sourceLabel),
      sourceLabel,
    )
  }
}
