package skillbill.di.goal
import me.tatarka.inject.annotations.Provides
import skillbill.application.agentrun.AgentRunGoalRunnerSubtaskLauncher
import skillbill.infrastructure.sqlite.goalrunner.outcome.WorkflowGoalRunnerOutcomeStore
import skillbill.ports.goalrunner.persistence.GoalRunnerChildRepairStore
import skillbill.ports.goalrunner.runner.GoalRunnerAttemptLedgerStore
import skillbill.ports.goalrunner.runner.GoalRunnerSubtaskLauncher

internal interface RuntimeGoalRunnerLaunchProvides {
  @Provides
  fun goalRunnerSubtaskLauncher(adapter: AgentRunGoalRunnerSubtaskLauncher): GoalRunnerSubtaskLauncher = adapter

  @Provides
  fun goalRunnerAttemptLedgerStore(adapter: WorkflowGoalRunnerOutcomeStore): GoalRunnerAttemptLedgerStore = adapter

  @Provides
  fun goalRunnerChildRepairStore(adapter: WorkflowGoalRunnerOutcomeStore): GoalRunnerChildRepairStore = adapter
}
