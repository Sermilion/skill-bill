package skillbill.infrastructure.fs

import me.tatarka.inject.annotations.Inject
import skillbill.infrastructure.fs.contracts.workflow.FeatureTaskRuntimeQuarantineSchemaValidator
import skillbill.workflow.taskruntime.FeatureTaskRuntimeQuarantineValidator

@Inject
class FeatureTaskRuntimeQuarantineValidatorAdapter : FeatureTaskRuntimeQuarantineValidator {
  override fun validateQuarantineRecord(quarantineRecord: Any, sourceLabel: String) {
    FeatureTaskRuntimeQuarantineSchemaValidator.validate(
      requireFeatureTaskRuntimeArtifactMap(quarantineRecord, sourceLabel),
      sourceLabel,
    )
  }
}
