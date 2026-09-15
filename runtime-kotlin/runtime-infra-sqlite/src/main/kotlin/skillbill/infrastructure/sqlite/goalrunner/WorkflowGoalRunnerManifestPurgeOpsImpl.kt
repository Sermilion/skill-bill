package skillbill.infrastructure.sqlite.goalrunner

internal class WorkflowGoalRunnerManifestPurgeOpsImpl(
  private val ctx: WorkflowGoalRunnerManifestStoreContext,
) : GoalRunnerManifestPurgeCommands {
  override fun listOwnedGoalChildWorkflowIds(parentWorkflowId: String): List<String> =
    ctx.database.read { it.workflowStates.listGoalChildWorkflowIdsByParent(parentWorkflowId) }

  override fun purgeDecomposedGoal(parentWorkflowId: String) {
    ctx.database.transaction { unitOfWork ->
      goalRunnerPurgePersistence(unitOfWork).purgeDecomposedGoal(unitOfWork, parentWorkflowId)
    }
  }
}
