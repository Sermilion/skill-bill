package skillbill.workflow.taskruntime


/** Schema-validation port for the three SKILL-146 foundation wire contracts plus SKILL-164 shared evidence. */
interface FeatureTaskRuntimeHandoffFoundationValidator {
  fun validateDeclaration(payload: Any, sourceLabel: String)
  fun validatePersistenceRecord(payload: Any, sourceLabel: String)
  fun validateMeasurement(payload: Any, sourceLabel: String)
  fun validateSharedEvidenceProjection(payload: Any, sourceLabel: String) = Unit
}
