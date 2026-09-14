package skillbill.workflow.taskruntime

object NoopFeatureTaskRuntimeImplementationAttemptValidator : FeatureTaskRuntimeImplementationAttemptValidator {
  override fun validateImplementationAttemptRecord(attemptRecord: Any, sourceLabel: String) {
  }
}
