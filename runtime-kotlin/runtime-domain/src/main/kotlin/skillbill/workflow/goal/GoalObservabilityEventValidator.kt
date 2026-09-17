package skillbill.workflow.goal

import skillbill.error.InvalidGoalObservabilityEventSchemaError
import skillbill.workflow.taskruntime.FeatureTaskRuntimeWireArtifactValidator

typealias GoalObservabilityEventValidator = FeatureTaskRuntimeWireArtifactValidator

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
