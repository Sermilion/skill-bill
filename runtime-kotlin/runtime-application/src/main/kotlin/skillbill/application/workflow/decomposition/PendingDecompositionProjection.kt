package skillbill.application.workflow.decomposition
import skillbill.application.workflow.service.ContinuationStepResult
import skillbill.contracts.JsonCodec
import skillbill.ports.persistence.UnitOfWork
import skillbill.workflow.decomposition.DecompositionManifestValidator
import skillbill.workflow.engine.model.WorkflowStateSnapshot
import skillbill.workflow.engine.model.DurableWorkflowArtifacts
import skillbill.workflow.taskruntime.artifact.decodeGoalContinuationArtifactFromArtifact
import skillbill.workflow.taskruntime.model.persistence.task.runtime.persistence.FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_ARTIFACT_KEY

internal data class PendingDecompositionProjection(
  val ownerWorkflowId: String,
  val artifacts: DurableWorkflowArtifacts,
)

internal fun ContinuationStepResult.withPendingProjection(
  ownerWorkflowId: String,
  artifacts: DurableWorkflowArtifacts,
): ContinuationStepResult =
  copy(
    projectionOwnerWorkflowId = ownerWorkflowId,
    projectionArtifacts = artifacts,
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
      ?: goalContinuationParentWorkflowId(record.artifacts)
  } else {
    record.workflowId
  }
}

internal fun goalContinuationParentWorkflowIdForSettlement(artifacts: Map<String, Any?>): String? =
  goalContinuationParentWorkflowId(artifacts)

private fun goalContinuationParentWorkflowId(artifacts: Map<String, Any?>): String? {
  val raw = artifacts[FEATURE_TASK_RUNTIME_GOAL_CONTINUATION_ARTIFACT_KEY] ?: return null
  val map = JsonCodec.anyToStringAnyMap(raw) ?: return null
  return decodeGoalContinuationArtifactFromArtifact(map)
    ?.parentWorkflowId
    ?.takeIf(String::isNotBlank)
}
