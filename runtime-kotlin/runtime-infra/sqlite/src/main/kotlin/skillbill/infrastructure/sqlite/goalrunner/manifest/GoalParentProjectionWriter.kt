package skillbill.infrastructure.sqlite.goalrunner.manifest
import skillbill.contracts.issuekey.normalizeRequiredIssueKey
import skillbill.goalrunner.GOAL_OUT_OF_BAND_ACCEPTANCE_ARTIFACT_KEY
import skillbill.goalrunner.GOAL_REVIEW_POLICY_ARTIFACT_KEY
import skillbill.infrastructure.sqlite.workflow.decomposition.decompositionRuntime
import skillbill.ports.goalrunner.GoalRunnerPersistenceSession
import skillbill.ports.workflow.model.WorkflowFamily
import skillbill.ports.workflow.saveRecord
import skillbill.ports.workflow.toRecord
import skillbill.workflow.decomposition.DecompositionManifestValidator
import skillbill.workflow.decomposition.encodeManifestWireMap
import skillbill.workflow.decomposition.model.DecompositionManifest
import skillbill.workflow.decomposition.runtime.DECOMPOSITION_RUNTIME_ARTIFACT_KEY
import skillbill.workflow.engine.WorkflowEngine
import skillbill.workflow.engine.model.WorkflowArtifactPatch
import skillbill.workflow.engine.model.WorkflowStateSnapshot
import skillbill.workflow.engine.model.WorkflowUpdateInput

internal class GoalParentProjectionWriter(
  private val engine: WorkflowEngine,
  private val validator: DecompositionManifestValidator,
) {
  fun artifacts(
    manifest: DecompositionManifest,
    existingArtifacts: Map<String, Any?> = emptyMap(),
  ): Map<String, Any?> =
    LinkedHashMap(existingArtifacts).apply {
      remove(GOAL_REVIEW_POLICY_ARTIFACT_KEY)
      remove(GOAL_OUT_OF_BAND_ACCEPTANCE_ARTIFACT_KEY)
      put(
        DECOMPOSITION_RUNTIME_ARTIFACT_KEY,
        validator.encodeManifestWireMap(manifest, DECOMPOSITION_RUNTIME_ARTIFACT_KEY),
      )
    }

  fun rewrite(
    unitOfWork: GoalRunnerPersistenceSession,
    existing: WorkflowStateSnapshot,
  ) {
    val manifest =
      existing.decompositionRuntime(validator)
        ?: error("Goal parent workflow '${existing.workflowId}' has no decomposition manifest.")
    val updated =
      engine.updateRecord(
        WorkflowFamily.TASK_RUNTIME.definition,
        existing,
        WorkflowUpdateInput(
          workflowStatus = existing.workflowStatus,
          currentStepId = existing.currentStepId,
          stepUpdates = null,
          artifactsPatch = WorkflowArtifactPatch.from(artifacts(manifest, existing.artifacts)),
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
