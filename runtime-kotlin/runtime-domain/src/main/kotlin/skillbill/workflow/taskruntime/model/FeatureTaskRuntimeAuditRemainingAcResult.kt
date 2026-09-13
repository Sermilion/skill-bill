package skillbill.workflow.taskruntime.model

sealed interface FeatureTaskRuntimeAuditRemainingAcResult {
  data object EmptyRemainingList : FeatureTaskRuntimeAuditRemainingAcResult
  data class RemainingCriteriaText(val text: String) : FeatureTaskRuntimeAuditRemainingAcResult
  data object WhitespaceOnlyFinalResponse : FeatureTaskRuntimeAuditRemainingAcResult
  data object MissingFinalResponse : FeatureTaskRuntimeAuditRemainingAcResult
}
