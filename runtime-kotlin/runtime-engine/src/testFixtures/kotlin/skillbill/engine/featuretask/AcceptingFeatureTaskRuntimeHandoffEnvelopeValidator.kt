package skillbill.engine.featuretask

import skillbill.workflow.taskruntime.FeatureTaskRuntimeHandoffEnvelopeValidator

object AcceptingFeatureTaskRuntimeHandoffEnvelopeValidator : FeatureTaskRuntimeHandoffEnvelopeValidator {
  override fun validateEnvelope(envelope: Any, workflowId: String?) = Unit
}
