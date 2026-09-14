package skillbill.workflow.goal

object NoopGoalObservabilityEventValidator : GoalObservabilityEventValidator {
  override fun validate(event: Any, sourceLabel: String) {
  }
}
