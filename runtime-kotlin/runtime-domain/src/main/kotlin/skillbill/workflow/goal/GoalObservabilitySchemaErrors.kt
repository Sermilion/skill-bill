package skillbill.workflow.goal

import skillbill.error.shellcontent.InvalidGoalObservabilityEventSchemaError

fun invalidGoalObservabilityEvent(
  sourceLabel: String,
  fieldPath: String,
  reason: String,
  cause: Throwable? = null,
): InvalidGoalObservabilityEventSchemaError =
  InvalidGoalObservabilityEventSchemaError(
    sourceLabel = sourceLabel,
    fieldPath = fieldPath,
    reason = reason,
    cause = cause,
  )
