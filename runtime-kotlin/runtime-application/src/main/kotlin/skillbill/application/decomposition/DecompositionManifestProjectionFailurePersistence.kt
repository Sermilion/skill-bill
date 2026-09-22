package skillbill.application.decomposition

import skillbill.application.decomposition.model.RetryDecompositionManifestProjectionArgs
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

internal enum class DecompositionManifestProjectionFailurePersistence {
  PERSISTED,
  OWNER_ABSENT,
}

internal fun persistDecompositionManifestProjectionFailure(
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
              DECOMPOSITION_MANIFEST_PROJECTION_FAILURE_ARTIFACT_KEY to
                DecompositionManifestWriteGuard.failureArtifact(outcome),
            ),
          ),
        sessionId = existing.sessionId.orEmpty(),
      ),
    )
  family.save(unitOfWork.workflowStates, updated)
  return DecompositionManifestProjectionFailurePersistence.PERSISTED
}

internal fun clearDecompositionManifestProjectionFailure(
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
            DurableWorkflowArtifacts.fromJson(existing.artifactsJson).toMutableMap().apply {
              remove(DECOMPOSITION_MANIFEST_PROJECTION_FAILURE_ARTIFACT_KEY)
            },
          ),
        sessionId = existing.sessionId.orEmpty(),
        replaceArtifacts = true,
      ),
    )
  family.save(unitOfWork.workflowStates, updated)
  return DecompositionManifestProjectionFailurePersistence.PERSISTED
}

fun retryDecompositionManifestProjectionFromAuthoritativeState(
  args: RetryDecompositionManifestProjectionArgs,
): DecompositionManifestProjectionOutcome {
  val database = args.database
  val engine = args.engine
  val decompositionManifestWriter = args.decompositionManifestWriter
  val decompositionManifestValidator = args.decompositionManifestValidator
  val decompositionManifestStore = args.decompositionManifestStore
  val repoRoot = args.repoRoot
  val workflowId = args.workflowId
  val artifactsJson =
    database.read { unitOfWork ->
      WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId)?.artifactsJson
    } ?: return DecompositionManifestProjectionOutcome.Absent
  val outcome =
    decompositionManifestWriter.writeProjectionFromWorkflowState(
      repoRoot = repoRoot,
      artifactsJson = artifactsJson,
      validator = decompositionManifestValidator,
      fileStore = decompositionManifestStore,
    )
  if (outcome is DecompositionManifestProjectionOutcome.Written) {
    val cleared =
      database.transaction { unitOfWork ->
        clearDecompositionManifestProjectionFailure(engine, unitOfWork, workflowId)
      }
    if (cleared == DecompositionManifestProjectionFailurePersistence.OWNER_ABSENT) {
      return DecompositionManifestProjectionOutcome.Absent
    }
  }
  return outcome
}

internal fun decompositionManifestProjectionFailure(
  artifactsJson: String,
): DecompositionManifestProjectionOutcome.Failed? {
  val artifacts = DurableWorkflowArtifacts.fromJson(artifactsJson)
  val payload =
    artifacts[DECOMPOSITION_MANIFEST_PROJECTION_FAILURE_ARTIFACT_KEY] as? Map<*, *>
      ?: return null
  val operation = payload[DecompositionManifestProjectionFailurePayloadKeys.OPERATION] as? String ?: return null
  val targetPath = payload[DecompositionManifestProjectionFailurePayloadKeys.TARGET_PATH] as? String ?: return null
  return DecompositionManifestProjectionOutcome.Failed(operation = operation, targetPath = targetPath)
}
