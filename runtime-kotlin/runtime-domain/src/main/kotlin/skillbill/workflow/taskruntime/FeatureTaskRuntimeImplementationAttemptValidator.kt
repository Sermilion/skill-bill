package skillbill.workflow.taskruntime


interface FeatureTaskRuntimeImplementationAttemptValidator {
  fun validateImplementationAttemptRecord(attemptRecord: Any, sourceLabel: String)
}
