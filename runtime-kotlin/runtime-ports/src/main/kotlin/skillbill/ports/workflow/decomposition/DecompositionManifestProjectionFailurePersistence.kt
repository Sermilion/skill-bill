package skillbill.ports.workflow.decomposition

import skillbill.contracts.decomposition.DecompositionManifestProjectionFailurePayloadKeys
import skillbill.ports.persistence.UnitOfWork
import skillbill.ports.workflow.get
import skillbill.ports.workflow.model.WorkflowFamily
import skillbill.ports.workflow.save
import skillbill.workflow.decomposition.runtime.model.DecompositionManifestProjectionOutcome
import skillbill.workflow.engine.WorkflowEngine
import skillbill.workflow.engine.model.DurableWorkflowArtifactFamily
import skillbill.workflow.engine.model.WorkflowArtifactPatch
import skillbill.workflow.engine.model.WorkflowUpdateInput

enum class DecompositionManifestProjectionFailurePersistence {
  PERSISTED,
  OWNER_ABSENT,
}

fun persistDecompositionManifestProjectionFailure(
  engine: WorkflowEngine,
  unitOfWork: UnitOfWork,
  workflowId: String,
  outcome: DecompositionManifestProjectionOutcome.Failed,
): DecompositionManifestProjectionFailurePersistence {
  val family = WorkflowFamily.TASK_RUNTIME
  val existing =
    family.get(unitOfWork.workflowStates, workflowId)
      ?: return DecompositionManifestProjectionFailurePersistence.OWNER_ABSENT
  val updated =
    engine.updateRecord(
      family.definition,
      existing,
      WorkflowUpdateInput(
        workflowStatus = existing.workflowStatus,
        currentStepId = existing.currentStepId,
        stepUpdates = null,
        artifactsPatch =
          WorkflowArtifactPatch.from(
            mapOf(
              DurableWorkflowArtifactFamily.DECOMPOSITION_MANIFEST_PROJECTION_FAILURE.entry(
                mapOf(
                  DecompositionManifestProjectionFailurePayloadKeys.OPERATION to outcome.operation,
                  DecompositionManifestProjectionFailurePayloadKeys.TARGET_PATH to outcome.targetPath,
                ),
              ),
            ),
          ),
        sessionId = existing.sessionId.orEmpty(),
      ),
    )
  family.save(unitOfWork.workflowStates, updated)
  return DecompositionManifestProjectionFailurePersistence.PERSISTED
}

fun clearDecompositionManifestProjectionFailure(
  engine: WorkflowEngine,
  unitOfWork: UnitOfWork,
  workflowId: String,
): DecompositionManifestProjectionFailurePersistence {
  val family = WorkflowFamily.TASK_RUNTIME
  val existing =
    family.get(unitOfWork.workflowStates, workflowId)
      ?: return DecompositionManifestProjectionFailurePersistence.OWNER_ABSENT
  val updated =
    engine.updateRecord(
      family.definition,
      existing,
      WorkflowUpdateInput(
        workflowStatus = existing.workflowStatus,
        currentStepId = existing.currentStepId,
        stepUpdates = null,
        artifactsPatch =
          WorkflowArtifactPatch.from(
            existing.artifacts.toMutableMap().apply {
              DurableWorkflowArtifactFamily.DECOMPOSITION_MANIFEST_PROJECTION_FAILURE.removeFrom(this)
            },
          ),
        sessionId = existing.sessionId.orEmpty(),
        replaceArtifacts = true,
      ),
    )
  family.save(unitOfWork.workflowStates, updated)
  return DecompositionManifestProjectionFailurePersistence.PERSISTED
}
