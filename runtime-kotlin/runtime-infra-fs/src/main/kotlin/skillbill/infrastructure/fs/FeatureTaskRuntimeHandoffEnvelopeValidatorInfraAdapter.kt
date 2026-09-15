package skillbill.infrastructure.fs

import me.tatarka.inject.annotations.Inject
import skillbill.infrastructure.fs.contracts.workflow.FeatureTaskRuntimeHandoffEnvelopeSchemaValidator
import skillbill.workflow.taskruntime.FeatureTaskRuntimeHandoffEnvelopeValidator

@Inject
class FeatureTaskRuntimeHandoffEnvelopeValidatorInfraAdapter : FeatureTaskRuntimeHandoffEnvelopeValidator {
  override fun validateEnvelope(envelope: Any, workflowId: String?) {
    FeatureTaskRuntimeHandoffEnvelopeSchemaValidator.validate(
      requireFeatureTaskRuntimeArtifactMap(envelope, workflowId ?: "handoff-envelope"),
      workflowId,
    )
  }
}
