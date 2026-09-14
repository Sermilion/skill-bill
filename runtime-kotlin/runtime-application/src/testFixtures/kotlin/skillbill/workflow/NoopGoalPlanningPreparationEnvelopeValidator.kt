package skillbill.workflow

import skillbill.workflow.goal.GoalPlanningPreparationEnvelopeValidator

object NoopGoalPlanningPreparationEnvelopeValidator : GoalPlanningPreparationEnvelopeValidator {
  override fun validate(envelope: Any, sourceLabel: String) = Unit
}
