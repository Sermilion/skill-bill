package skillbill.engine.featuretask.phase.record

import me.tatarka.inject.annotations.Inject
import skillbill.engine.featuretask.persist.FeatureTaskRuntimeWorkflowPersistence
import skillbill.engine.featuretask.phase.core.decomposeTerminalFromWorkflowArtifacts
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.workflow.get
import skillbill.ports.workflow.model.WorkflowFamily
import skillbill.ports.workflow.save
import skillbill.workflow.engine.WorkflowEngine
import skillbill.workflow.engine.WorkflowSnapshotValidator
import skillbill.workflow.engine.model.WorkflowArtifactPatch
import skillbill.workflow.engine.model.WorkflowUpdateInput
import skillbill.workflow.model.WorkflowStatus
import skillbill.workflow.taskruntime.artifact.asWorkflowArtifactEntry
import skillbill.workflow.taskruntime.artifact.decomposeTerminalFromWorkflowArtifacts
import skillbill.workflow.taskruntime.model.core.FEATURE_TASK_RUNTIME_DECOMPOSE_TERMINAL_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeDecomposeTerminal
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition

@Inject
class FeatureTaskRuntimeDecomposeTerminalRecorder(
  private val database: DatabaseSessionFactory,
  private val workflowSnapshotValidator: WorkflowSnapshotValidator,
) {
  private val engine: WorkflowEngine = WorkflowEngine(workflowSnapshotValidator)

  fun recordDecomposeTerminal(
    workflowId: String,
    terminal: FeatureTaskRuntimeDecomposeTerminal,
  ): Boolean =
    database.transaction { unitOfWork ->
      val record =
        WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId)
          ?: return@transaction false
      val updated =
        engine.updateRecord(
          WorkflowFamily.TASK_RUNTIME.definition,
          record,
          WorkflowUpdateInput(
            workflowStatus = WorkflowStatus.COMPLETED,
            currentStepId = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PLAN,
            stepUpdates = null,
            artifactsPatch =
              WorkflowArtifactPatch.from(
                mapOf(FEATURE_TASK_RUNTIME_DECOMPOSE_TERMINAL_ARTIFACT_KEY to terminal.asWorkflowArtifactEntry()),
              ),
            sessionId = record.sessionId.orEmpty(),
          ),
        )
      WorkflowFamily.TASK_RUNTIME.save(unitOfWork.workflowStates, updated)
      true
    }

  fun loadDecomposeTerminal(workflowId: String): FeatureTaskRuntimeDecomposeTerminal? =
    database.read { unitOfWork ->
      val record =
        WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId)
          ?: return@read null
      decomposeTerminalFromWorkflowArtifacts(FeatureTaskRuntimeWorkflowPersistence.artifactsFrom(record))
    }
}
