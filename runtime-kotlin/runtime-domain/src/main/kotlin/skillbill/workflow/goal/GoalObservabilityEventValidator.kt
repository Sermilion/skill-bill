package skillbill.workflow.goal

import skillbill.error.InvalidGoalObservabilityEventSchemaError

interface GoalObservabilityEventValidator {
  fun validate(event: Any, sourceLabel: String)
}

fun invalidGoalObservabilityEvent(
  sourceLabel: String,
  fieldPath: String,
  reason: String,
  cause: Throwable? = null,
): InvalidGoalObservabilityEventSchemaError = InvalidGoalObservabilityEventSchemaError(
  sourceLabel = sourceLabel,
  fieldPath = fieldPath,
  reason = reason,
  cause = cause,
)
