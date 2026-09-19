package skillbill.workflow.taskruntime.model.handoff.task
import skillbill.error.shellcontent.InvalidFeatureTaskRuntimePhaseHandoffSchemaError
import skillbill.workflow.taskruntime.model.audit.reason
import skillbill.workflow.taskruntime.model.core.reason
import skillbill.workflow.taskruntime.model.core.task
import skillbill.workflow.taskruntime.model.handoff.envelope.value
import skillbill.workflow.taskruntime.model.persistence.artifact.value
import skillbill.workflow.taskruntime.model.persistence.task.runtime.goal.field
import skillbill.workflow.taskruntime.model.persistence.task.runtime.goal.reason
import skillbill.workflow.taskruntime.model.persistence.task.runtime.implementation.value
import skillbill.workflow.taskruntime.model.persistence.task.runtime.run.value
import skillbill.workflow.taskruntime.model.phase.reason
import skillbill.workflow.taskruntime.model.phase.sourceLabel
import skillbill.workflow.taskruntime.model.repair.task.reason
import skillbill.workflow.taskruntime.model.repair.task.value
import skillbill.workflow.taskruntime.model.validation.reason

internal fun unrecognizedHandoffWireValue(field: String, value: String): Nothing =
  throw InvalidFeatureTaskRuntimePhaseHandoffSchemaError(
    sourceLabel = "<wire>",
    reason = "Unrecognized feature-task-runtime handoff $field wire value '$value'.",
  )
