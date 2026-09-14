package skillbill.workflow.taskruntime

interface FeatureTaskRuntimeBuildReceiptValidator {
  fun validateBuildReceipt(buildReceipt: Any, sourceLabel: String)
}
