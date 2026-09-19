package skillbill.engine.goalrunner.manifest

import skillbill.application.decomposition.DECOMPOSITION_RUNTIME_ARTIFACT_KEY
import skillbill.application.workflow.decomposition.decompositionRuntime
import skillbill.application.workflow.model.WorkflowFamily
import skillbill.contracts.issuekey.normalizeRequiredIssueKey
import skillbill.engine.featuretask.persist.FeatureTaskRuntimeWorkflowPersistence
import skillbill.goalrunner.GOAL_OUT_OF_BAND_ACCEPTANCE_ARTIFACT_KEY
import skillbill.goalrunner.GOAL_REVIEW_POLICY_ARTIFACT_KEY
import skillbill.ports.persistence.UnitOfWork
import skillbill.ports.workflow.saveRecord
import skillbill.ports.workflow.toRecord
import skillbill.workflow.decomposition.DecompositionManifestValidator
import skillbill.workflow.decomposition.encodeManifestWireMap
import skillbill.workflow.decomposition.model.DecompositionManifest
import skillbill.workflow.engine.WorkflowEngine
import skillbill.workflow.engine.model.WorkflowArtifactPatch
import skillbill.workflow.engine.model.WorkflowStateSnapshot
import skillbill.workflow.engine.model.WorkflowUpdateInput
class GoalParentProjectionWriter(
  private val engine: WorkflowEngine,
  private val validator: DecompositionManifestValidator,
) {
  fun artifacts(manifest: DecompositionManifest, existing: Map<String, Any?> = emptyMap()): Map<String, Any?> =
    LinkedHashMap(existing).apply {
      remove(GOAL_REVIEW_POLICY_ARTIFACT_KEY)
      remove(GOAL_OUT_OF_BAND_ACCEPTANCE_ARTIFACT_KEY)
      put(
        DECOMPOSITION_RUNTIME_ARTIFACT_KEY,
        validator.encodeManifestWireMap(manifest, DECOMPOSITION_RUNTIME_ARTIFACT_KEY),
      )
    }

  fun rewrite(unitOfWork: UnitOfWork, existing: WorkflowStateSnapshot) {
    val manifest = existing.decompositionRuntime(validator)
      ?: error("Goal parent workflow '${existing.workflowId}' has no decomposition manifest.")
    val updated = engine.updateRecord(
      WorkflowFamily.TASK_RUNTIME.definition,
      existing,
      WorkflowUpdateInput(
        workflowStatus = existing.workflowStatus,
        currentStepId = existing.currentStepId,
        stepUpdates = null,
        artifactsPatch = WorkflowArtifactPatch.from(
          artifacts(manifest, FeatureTaskRuntimeWorkflowPersistence.artifactsFrom(existing)),
        ),
        sessionId = existing.sessionId,
        replaceArtifacts = true,
      ),
    )
    WorkflowFamily.TASK_RUNTIME.saveRecord(
      unitOfWork.workflowStates,
      updated.toRecord().copy(issueKey = normalizeRequiredIssueKey(manifest.issueKey)),
    )
  }
}
