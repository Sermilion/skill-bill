package skillbill.infrastructure.sqlite.goalrunner.manifest
import skillbill.ports.goalrunner.GoalParentProjectionWriter
import skillbill.contracts.issuekey.normalizeRequiredIssueKey
import skillbill.infrastructure.sqlite.goalrunner.control.reconcileControlStateForManifest
import skillbill.workflow.decomposition.runtime.decompositionRuntime
import skillbill.ports.workflow.decomposition.findDecomposedParentWorkflow
import skillbill.infrastructure.sqlite.workflow.decomposition.requireRuntimeModeForEngineWrite
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.goalrunner.runner.model.GoalRunnerManifestState
import skillbill.ports.persistence.UnitOfWork
import skillbill.ports.workflow.get
import skillbill.ports.workflow.model.WorkflowFamily
import skillbill.ports.workflow.model.toSnapshot
import skillbill.ports.workflow.saveRecord
import skillbill.ports.workflow.toRecord
import skillbill.ports.workflow.decomposition.DecompositionManifestValidator
import skillbill.workflow.engine.WorkflowEngine
import skillbill.workflow.engine.model.WorkflowArtifactPatch
import skillbill.workflow.engine.model.DurableWorkflowArtifacts
import skillbill.workflow.engine.model.WorkflowUpdateInput

internal data class SavedManifestProjection(
  internal val state: GoalRunnerManifestState,
  internal val projectionArtifacts: DurableWorkflowArtifacts,
)

internal class WorkflowGoalRunnerManifestProjectionPersistence(
  private val database: DatabaseSessionFactory,
  private val engine: WorkflowEngine,
  private val parentProjection: GoalParentProjectionWriter,
  private val decompositionManifestValidator: DecompositionManifestValidator,
) {
  fun save(state: GoalRunnerManifestState): SavedManifestProjection =
    database.transaction { unitOfWork -> saveInTransaction(unitOfWork, state) }

  fun saveInTransaction(
    unitOfWork: UnitOfWork,
    state: GoalRunnerManifestState,
    clearOutOfBandAcceptances: Boolean = false,
    mergeConcurrentProgress: Boolean = true,
  ): SavedManifestProjection {
    val existingRecord =
      unitOfWork.workflowStates.getFeatureTaskWorkflow(state.parentWorkflowId)
        ?: unitOfWork.workflowStates.findDecomposedParentWorkflow(
          state.manifest.issueKey,
          decompositionManifestValidator,
        )
        ?: error("Unknown decomposed parent workflow '${state.parentWorkflowId}'.")
    existingRecord.requireRuntimeModeForEngineWrite()
    val existingSnapshot = existingRecord.toSnapshot()
    if (clearOutOfBandAcceptances) {
      unitOfWork.goalRunnerControls.clearOutOfBandAcceptances(existingSnapshot.workflowId)
      unitOfWork.goalRunnerControls.clearControlState(existingSnapshot.workflowId)
    }
    val manifest =
      if (mergeConcurrentProgress) {
        mergeConcurrentGoalProgress(
          existingSnapshot.decompositionRuntime() ?: state.manifest,
          state.manifest,
        )
      } else {
        state.manifest
      }
    val updated =
      engine.updateRecord(
        WorkflowFamily.TASK_RUNTIME.definition,
        existingSnapshot,
        WorkflowUpdateInput(
          workflowStatus = existingSnapshot.workflowStatus,
          currentStepId = existingSnapshot.currentStepId,
          stepUpdates = null,
          artifactsPatch =
            WorkflowArtifactPatch.from(
              parentProjection.artifacts(manifest, existingSnapshot.artifacts),
            ),
          sessionId = existingSnapshot.sessionId.orEmpty(),
          replaceArtifacts = true,
        ),
      )
    WorkflowFamily.TASK_RUNTIME.saveRecord(
      unitOfWork.workflowStates,
      updated.toRecord().copy(issueKey = normalizeRequiredIssueKey(manifest.issueKey)),
    )
    val refreshed = WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, updated.workflowId) ?: updated
    reconcileControlStateForManifest(unitOfWork, refreshed.workflowId, decompositionManifestValidator)
    return SavedManifestProjection(
      state =
        GoalRunnerManifestState(
          parentWorkflowId = refreshed.workflowId,
          dbPath = unitOfWork.dbPath.toString(),
          manifest = refreshed.decompositionRuntime() ?: manifest,
          controlState = unitOfWork.goalRunnerControls.controlState(refreshed.workflowId),
          repoRoot = state.repoRoot,
        ),
      projectionArtifacts = refreshed.artifacts,
    )
  }
}
