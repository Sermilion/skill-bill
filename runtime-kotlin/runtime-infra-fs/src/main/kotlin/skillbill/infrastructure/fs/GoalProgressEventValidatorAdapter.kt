package skillbill.infrastructure.fs

import me.tatarka.inject.annotations.Inject
import skillbill.contracts.JsonCodec
import skillbill.infrastructure.fs.contracts.workflow.GoalProgressEventSchemaValidator
import skillbill.workflow.goal.GoalProgressEventValidator

@Inject
class GoalProgressEventValidatorAdapter : GoalProgressEventValidator {
  override fun validate(event: Any, sourceLabel: String) {
    val wire = JsonCodec.anyToStringAnyMap(event)
      ?: throw IllegalArgumentException("Goal progress event at $sourceLabel must decode to an object.")
    GoalProgressEventSchemaValidator.validate(wire, sourceLabel)
  }
}
