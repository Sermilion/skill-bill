package skillbill.infrastructure.fs

import me.tatarka.inject.annotations.Inject
import skillbill.contracts.JsonCodec
import skillbill.infrastructure.fs.contracts.workflow.GoalPlanningPreparationSchemaValidator
import skillbill.workflow.goal.GoalPlanningPreparationEnvelopeValidator

@Inject
class GoalPlanningPreparationEnvelopeValidatorAdapter : GoalPlanningPreparationEnvelopeValidator {
  override fun validate(envelope: Any, sourceLabel: String) {
    val wire = JsonCodec.anyToStringAnyMap(envelope)
      ?: throw IllegalArgumentException("Goal planning preparation envelope at $sourceLabel must decode to an object.")
    GoalPlanningPreparationSchemaValidator.validate(wire, sourceLabel)
  }
}
