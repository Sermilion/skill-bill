package skillbill.learnings.model

data class LearningEntry(
  val id: Int,
  val reference: String,
  val scope: LearningScope,
  val scopeKey: String,
  val status: String,
  val title: String,
  val ruleText: String,
  val rationale: String,
  val sourceReviewRunId: String?,
  val sourceFindingId: String?,
) {
  val scopeLabel: String
    get() = if (scopeKey.isBlank()) scope.wireName else "${scope.wireName}:$scopeKey"
}
