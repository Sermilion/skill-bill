package skillbill.engine.featuretask

import skillbill.workflow.taskruntime.FeatureTaskRuntimeHandoffFoundationValidator

object AcceptingFeatureTaskRuntimeHandoffFoundationValidator : FeatureTaskRuntimeHandoffFoundationValidator {
  override fun validateDeclaration(payload: Any, sourceLabel: String) = Unit
  override fun validatePersistenceRecord(payload: Any, sourceLabel: String) = Unit
  override fun validateMeasurement(payload: Any, sourceLabel: String) = Unit
  override fun validateSharedEvidenceProjection(payload: Any, sourceLabel: String) = Unit
}
