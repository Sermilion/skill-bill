package skillbill.engine.goalrunner.execution.core

val DIRECT_GOAL_RUNNER_EXECUTION_COORDINATOR: GoalRunnerExecutionCoordinator =
  object : GoalRunnerExecutionCoordinator {
    override fun <T> runOwned(
      parentWorkflowId: String,
      block: () -> T,
    ): T = block()
  }
