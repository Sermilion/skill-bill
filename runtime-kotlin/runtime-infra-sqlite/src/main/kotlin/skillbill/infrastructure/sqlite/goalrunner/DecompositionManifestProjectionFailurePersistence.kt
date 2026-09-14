package skillbill.infrastructure.sqlite.goalrunner

import skillbill.contracts.decomposition.DecompositionManifestProjectionFailurePayloadKeys
import skillbill.ports.persistence.UnitOfWork
import skillbill.ports.workflow.get
import skillbill.ports.workflow.model.WorkflowFamily
import skillbill.ports.workflow.save
import skillbill.workflow.decomposition.runtime.DECOMPOSITION_MANIFEST_PROJECTION_FAILURE_ARTIFACT_KEY
import skillbill.workflow.decomposition.runtime.model.DecompositionManifestProjectionOutcome
import skillbill.workflow.engine.WorkflowEngine
import skillbill.workflow.engine.model.DurableWorkflowArtifacts
import skillbill.workflow.engine.model.WorkflowArtifactPatch
import skillbill.workflow.engine.model.WorkflowUpdateInput

internal fun persistDecompositionManifestProjectionFailure(
  engine: WorkflowEngine,
  unitOfWork: UnitOfWork,
  workflowId: String,
  outcome: DecompositionManifestProjectionOutcome.Failed,
) {
  val family = WorkflowFamily.TASK_RUNTIME
  val existing = family.get(unitOfWork.workflowStates, workflowId) ?: return
  val updated = engine.updateRecord(
    family.definition,
    existing,
    WorkflowUpdateInput(
      workflowStatus = existing.workflowStatus,
      currentStepId = existing.currentStepId,
      stepUpdates = null,
      artifactsPatch = WorkflowArtifactPatch.from(
        mapOf(
          DECOMPOSITION_MANIFEST_PROJECTION_FAILURE_ARTIFACT_KEY to
            mapOf(
              DecompositionManifestProjectionFailurePayloadKeys.OPERATION to outcome.operation,
              DecompositionManifestProjectionFailurePayloadKeys.TARGET_PATH to outcome.targetPath,
            ),
        ),
      ),
      sessionId = existing.sessionId.orEmpty(),
    ),
  )
  family.save(unitOfWork.workflowStates, updated)
}

internal fun clearDecompositionManifestProjectionFailure(
  engine: WorkflowEngine,
  unitOfWork: UnitOfWork,
  workflowId: String,
) {
  val family = WorkflowFamily.TASK_RUNTIME
  val existing = family.get(unitOfWork.workflowStates, workflowId) ?: return
  val updated = engine.updateRecord(
    family.definition,
    existing,
    WorkflowUpdateInput(
      workflowStatus = existing.workflowStatus,
      currentStepId = existing.currentStepId,
      stepUpdates = null,
      artifactsPatch = WorkflowArtifactPatch.from(
        DurableWorkflowArtifacts.fromJson(existing.artifactsJson).toMutableMap().apply {
          remove(DECOMPOSITION_MANIFEST_PROJECTION_FAILURE_ARTIFACT_KEY)
        },
      ),
      sessionId = existing.sessionId.orEmpty(),
      replaceArtifacts = true,
    ),
  )
  family.save(unitOfWork.workflowStates, updated)
}
