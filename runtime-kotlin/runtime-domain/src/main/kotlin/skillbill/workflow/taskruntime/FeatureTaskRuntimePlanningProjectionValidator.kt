package skillbill.workflow.taskruntime


interface FeatureTaskRuntimePlanningProjectionValidator {
  fun validatePlanningProjection(producedOutputs: Any, sourceLabel: String)
}
