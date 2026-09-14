package skillbill.workflow.taskruntime

object NoopFeatureTaskRuntimeQuarantineValidator : FeatureTaskRuntimeQuarantineValidator {
  override fun validateQuarantineRecord(quarantineRecord: Any, sourceLabel: String) {
  }
}
