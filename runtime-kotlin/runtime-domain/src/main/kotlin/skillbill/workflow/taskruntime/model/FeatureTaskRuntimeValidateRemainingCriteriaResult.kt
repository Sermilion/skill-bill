package skillbill.workflow.taskruntime.model

sealed interface FeatureTaskRuntimeValidateRemainingCriteriaResult {
  data object MissingFinalResponse : FeatureTaskRuntimeValidateRemainingCriteriaResult

  data object EmptyRemainingList : FeatureTaskRuntimeValidateRemainingCriteriaResult

  data class UnfixedCriteria(val items: List<String>) : FeatureTaskRuntimeValidateRemainingCriteriaResult
}
