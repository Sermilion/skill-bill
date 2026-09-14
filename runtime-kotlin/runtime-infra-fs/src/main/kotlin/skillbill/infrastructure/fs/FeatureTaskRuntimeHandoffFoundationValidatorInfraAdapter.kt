package skillbill.infrastructure.fs

import me.tatarka.inject.annotations.Inject
import skillbill.infrastructure.fs.contracts.workflow.FeatureTaskRuntimePersistenceSchemaValidator
import skillbill.infrastructure.fs.contracts.workflow.FeatureTaskRuntimePhaseHandoffSchemaValidator
import skillbill.infrastructure.fs.contracts.workflow.FeatureTaskRuntimeProjectionMeasurementSchemaValidator
import skillbill.infrastructure.fs.contracts.workflow.FeatureTaskRuntimeSharedEvidenceProjectionSchemaValidator
import skillbill.workflow.taskruntime.FeatureTaskRuntimeHandoffFoundationValidator

@Inject
class FeatureTaskRuntimeHandoffFoundationValidatorInfraAdapter : FeatureTaskRuntimeHandoffFoundationValidator {
  override fun validateDeclaration(payload: Any, sourceLabel: String) =
    FeatureTaskRuntimePhaseHandoffSchemaValidator.validate(
      requireFeatureTaskRuntimeArtifactMap(payload, sourceLabel),
      sourceLabel,
    )

  override fun validatePersistenceRecord(payload: Any, sourceLabel: String) =
    FeatureTaskRuntimePersistenceSchemaValidator.validate(
      requireFeatureTaskRuntimeArtifactMap(payload, sourceLabel),
      sourceLabel,
    )

  override fun validateMeasurement(payload: Any, sourceLabel: String) =
    FeatureTaskRuntimeProjectionMeasurementSchemaValidator.validate(
      requireFeatureTaskRuntimeArtifactMap(payload, sourceLabel),
      sourceLabel,
    )

  override fun validateSharedEvidenceProjection(payload: Any, sourceLabel: String) =
    FeatureTaskRuntimeSharedEvidenceProjectionSchemaValidator.validate(
      requireFeatureTaskRuntimeArtifactMap(payload, sourceLabel),
      sourceLabel,
    )
}
