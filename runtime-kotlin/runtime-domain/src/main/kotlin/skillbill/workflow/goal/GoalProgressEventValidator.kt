package skillbill.workflow.goal

interface GoalProgressEventValidator {
  fun validate(event: Any, sourceLabel: String)
}
