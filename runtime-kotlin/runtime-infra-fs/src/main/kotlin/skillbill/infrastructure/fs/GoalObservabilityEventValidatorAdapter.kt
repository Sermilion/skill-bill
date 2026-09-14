package skillbill.infrastructure.fs

import me.tatarka.inject.annotations.Inject
import skillbill.contracts.JsonCodec
import skillbill.infrastructure.fs.contracts.workflow.GoalObservabilityEventSchemaValidator
import skillbill.workflow.goal.GoalObservabilityEventValidator

@Inject
class GoalObservabilityEventValidatorAdapter : GoalObservabilityEventValidator {
  override fun validate(event: Any, sourceLabel: String) {
    val wire = JsonCodec.anyToStringAnyMap(event)
      ?: throw IllegalArgumentException("Goal observability event at $sourceLabel must decode to an object.")
    GoalObservabilityEventSchemaValidator.validate(wire, sourceLabel)
  }
}
