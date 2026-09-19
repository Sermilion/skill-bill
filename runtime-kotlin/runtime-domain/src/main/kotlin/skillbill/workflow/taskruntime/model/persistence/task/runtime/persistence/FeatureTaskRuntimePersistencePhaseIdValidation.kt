package skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence
import skillbill.error.shellcontent.InvalidWorkflowStateSchemaError
import skillbill.workflow.taskruntime.model.audit.phaseId
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimePhaseIds
import skillbill.workflow.taskruntime.model.core.fieldPath
import skillbill.workflow.taskruntime.model.core.task
import skillbill.workflow.taskruntime.model.handoff.envelope.phaseId
import skillbill.workflow.taskruntime.model.handoff.task.phaseId
import skillbill.workflow.taskruntime.model.persistence.task.runtime.checkpoint.phaseId
import skillbill.workflow.taskruntime.model.persistence.task.runtime.implementation.phaseId
import skillbill.workflow.taskruntime.model.phase.phaseId
import skillbill.workflow.taskruntime.model.repair.task.phaseId

private val REMOVED_FEATURE_TASK_RUNTIME_PHASE_IDS: Set<String> = setOf("plan_fix")

private val KNOWN_FEATURE_TASK_RUNTIME_PHASE_IDS: Set<String> =
  FeatureTaskRuntimePhaseIds.all.toSet()

internal fun requireKnownFeatureTaskRuntimePhaseId(phaseId: String, fieldPath: String): String {
  if (phaseId in REMOVED_FEATURE_TASK_RUNTIME_PHASE_IDS) {
    throw InvalidWorkflowStateSchemaError(
      "Feature-task-runtime artifact field '$fieldPath' names removed phase '$phaseId'.",
    )
  }
  if (phaseId !in KNOWN_FEATURE_TASK_RUNTIME_PHASE_IDS) {
    throw InvalidWorkflowStateSchemaError(
      "Feature-task-runtime artifact field '$fieldPath' has unknown phase '$phaseId'.",
    )
  }
  return phaseId
}
