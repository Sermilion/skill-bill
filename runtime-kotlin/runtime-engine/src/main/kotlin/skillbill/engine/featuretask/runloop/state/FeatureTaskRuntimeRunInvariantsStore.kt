package skillbill.engine.featuretask.runloop.state




import skillbill.engine.featuretask.persist.FeatureTaskRuntimeWorkflowPersistence
import me.tatarka.inject.annotations.Inject
import skillbill.application.workflow.model.WorkflowFamily
import skillbill.contracts.JsonCodec
import skillbill.error.InvalidWorkflowStateSchemaError
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.workflow.get
import skillbill.workflow.engine.model.DurableWorkflowArtifacts
import skillbill.workflow.taskruntime.decodeRunInvariantsFromArtifact
import skillbill.workflow.taskruntime.model.FEATURE_TASK_RUNTIME_RUN_INVARIANTS_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRunInvariants

@Inject
class FeatureTaskRuntimeRunInvariantsStore(
  private val database: DatabaseSessionFactory,
  private val workflowPersistence: FeatureTaskRuntimeWorkflowPersistence,
) {
  fun resolve(
    workflowId: String,
    proposed: FeatureTaskRuntimeRunInvariants? = null,
  ): FeatureTaskRuntimeRunInvariants? {
    proposed?.let { persistOrUpdateAgentAddons(workflowId, it) }
    return database.read { unitOfWork ->
      val record = WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId)
        ?: return@read null
      runInvariantsFrom(FeatureTaskRuntimeWorkflowPersistence.artifactsFrom(record))
    }
  }

  private fun persistOrUpdateAgentAddons(workflowId: String, proposed: FeatureTaskRuntimeRunInvariants) {
    database.transaction { unitOfWork ->
      val record = WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId)
        ?: return@transaction
      val artifacts = FeatureTaskRuntimeWorkflowPersistence.artifactsFrom(record)
      val existing = runInvariantsFrom(artifacts)
      when {
        existing == null -> workflowPersistence.persistRunInvariantsPatch(unitOfWork.workflowStates, record, proposed)
        existing.agentAddonSelection != proposed.agentAddonSelection -> workflowPersistence.persistRunInvariantsPatch(
          unitOfWork.workflowStates,
          record,
          existing.copy(agentAddonSelection = proposed.agentAddonSelection),
        )
      }
    }
  }
}

private fun runInvariantsFrom(artifacts: DurableWorkflowArtifacts): FeatureTaskRuntimeRunInvariants? {
  val raw = artifacts[FEATURE_TASK_RUNTIME_RUN_INVARIANTS_ARTIFACT_KEY] ?: return null
  val entryMap = JsonCodec.anyToStringAnyMap(raw)
    ?: throw InvalidWorkflowStateSchemaError(
      "Feature-task-runtime artifact '$FEATURE_TASK_RUNTIME_RUN_INVARIANTS_ARTIFACT_KEY' must decode to a map.",
    )
  return decodeRunInvariantsFromArtifact(entryMap)
}
