package skillbill.workflow.taskruntime

object NoopFeatureTaskRuntimeBuildReceiptValidator : FeatureTaskRuntimeBuildReceiptValidator {
  override fun validateBuildReceipt(buildReceipt: Any, sourceLabel: String) {
  }
}
