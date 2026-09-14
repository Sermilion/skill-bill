package skillbill.workflow.goal

interface GoalPlanningPreparationEnvelopeValidator {
  fun validate(envelope: Any, sourceLabel: String)
}
