package skillbill.workflow.goal

object NoopGoalProgressEventValidator : GoalProgressEventValidator {
  override fun validate(event: Any, sourceLabel: String) {
  }
}
