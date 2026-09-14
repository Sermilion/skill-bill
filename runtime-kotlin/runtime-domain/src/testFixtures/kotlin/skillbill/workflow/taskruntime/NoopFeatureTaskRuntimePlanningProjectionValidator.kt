package skillbill.workflow.taskruntime

object NoopFeatureTaskRuntimePlanningProjectionValidator : FeatureTaskRuntimePlanningProjectionValidator {
  override fun validatePlanningProjection(producedOutputs: Any, sourceLabel: String) {
  }
}
