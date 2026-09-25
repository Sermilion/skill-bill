package skillbill.engine.goalrunner.persist

import skillbill.contracts.JsonCodec
import skillbill.error.shellcontent.InvalidWorkflowStateSchemaError
import skillbill.ports.taskruntime.FeatureTaskRuntimePhaseOutputValidator
import skillbill.ports.workflow.WorkflowStateRepository
import skillbill.ports.workflow.get
import skillbill.ports.workflow.model.WorkflowFamily
import skillbill.ports.workflow.model.toSnapshot
import skillbill.workflow.engine.model.WorkflowStateSnapshot
import skillbill.workflow.taskruntime.model.phase.requireAcceptedOutput
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition

internal fun goalReviewEmissionEnvelope(
  rawResult: String,
  phaseOutputValidator: FeatureTaskRuntimePhaseOutputValidator,
): Map<String, Any?> {
  if (JsonCodec.parseObjectOrNull(rawResult.trim()) == null) return emptyMap<String, Any?>()
  return JsonCodec.anyToStringAnyMap(
    phaseOutputValidator
      .validatePhaseOutput(rawResult, FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW)
      .requireAcceptedOutput(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW)
      .normalizedOutput
      .envelopePayload(),
  ) ?: error("Normalized review output was not a string-keyed object.")
}

fun taskRuntimeRecordOrNull(
  workflowStates: WorkflowStateRepository,
  workflowId: String,
): WorkflowStateSnapshot? =
  try {
    WorkflowFamily.TASK_RUNTIME.get(workflowStates, workflowId)
  } catch (error: InvalidWorkflowStateSchemaError) {
    if (error.message.orEmpty().contains("mode='")) {
      null
    } else {
      throw error
    }
  }

fun featureTaskRecordForLegacyControls(
  workflowStates: WorkflowStateRepository,
  workflowId: String,
): WorkflowStateSnapshot? = workflowStates.getFeatureTaskWorkflow(workflowId)?.toSnapshot()
