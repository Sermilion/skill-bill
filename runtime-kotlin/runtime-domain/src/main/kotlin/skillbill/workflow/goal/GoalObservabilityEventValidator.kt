package skillbill.workflow.goal
import skillbill.error.shellcontent.InvalidGoalObservabilityEventSchemaError
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeWireArtifactValidator
typealias GoalObservabilityEventValidator = FeatureTaskRuntimeWireArtifactValidator

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
