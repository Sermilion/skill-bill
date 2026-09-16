package skillbill.application.workflow

import skillbill.contracts.JsonCodec
import skillbill.ports.persistence.UnitOfWork
import skillbill.workflow.decomposition.DecompositionManifestValidator
import skillbill.workflow.engine.model.WorkflowStateSnapshot
import skillbill.workflow.taskruntime.decodeGoalContinuationArtifactFromArtifact
import skillbill.workflow.taskruntime.model.FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_ARTIFACT_KEY

internal data class PendingDecompositionProjection(
  val ownerWorkflowId: String,
  val artifactsJson: String,
)

internal fun ContinuationStepResult.withPendingProjection(
  ownerWorkflowId: String,
  artifactsJson: String,
): ContinuationStepResult = copy(
  projectionOwnerWorkflowId = ownerWorkflowId,
  projectionArtifactsJson = artifactsJson,
)

internal fun resolveDecompositionProjectionOwner(
  record: WorkflowStateSnapshot,
  unitOfWork: UnitOfWork,
  validator: DecompositionManifestValidator,
): String? {
  val manifest = record.decompositionRuntime(validator) ?: return null
  return if (record.isGoalContinuationChildWorkflow()) {
    unitOfWork.workflowStates.findDecomposedParentWorkflowForRuntime(manifest, validator)
      ?.workflowId
      ?: goalContinuationParentWorkflowId(record.artifactsJson)
  } else {
    record.workflowId
  }
}

internal fun goalContinuationParentWorkflowIdForSettlement(artifactsJson: String): String? =
  goalContinuationParentWorkflowId(artifactsJson)

private fun goalContinuationParentWorkflowId(artifactsJson: String): String? {
  val artifacts = decodeWorkflowArtifacts(artifactsJson)
  val raw = artifacts[FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_ARTIFACT_KEY] ?: return null
  val map = JsonCodec.anyToStringAnyMap(raw) ?: return null
  return decodeGoalContinuationArtifactFromArtifact(map)?.parentWorkflowId?.takeIf(String::isNotBlank)
}
