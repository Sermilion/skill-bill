package skillbill.workflow.taskruntime.model.handoff.task
import skillbill.error.shellcontent.InvalidFeatureTaskRuntimePhaseHandoffSchemaError

internal fun unrecognizedHandoffWireValue(
  field: String,
  value: String,
): Nothing =
  throw InvalidFeatureTaskRuntimePhaseHandoffSchemaError(
    sourceLabel = "<wire>",
    reason = "Unrecognized feature-task-runtime handoff $field wire value '$value'.",
  )
