package skillbill.di.goal
import me.tatarka.inject.annotations.Provides
import skillbill.application.agentrun.AgentRunGoalRunnerSubtaskLauncher
import skillbill.infrastructure.launcher.agentrun.FileSystemAgentRunLauncher
import skillbill.infrastructure.launcher.agentrun.PathExecutableLookup
import skillbill.infrastructure.sqlite.goalrunner.outcome.WorkflowGoalRunnerOutcomeStore
import skillbill.infrastructure.workflow.git.goal.GhGoalPullRequestPort
import skillbill.model.OptionalCallbacks
import skillbill.ports.agentrun.AgentRunLauncher
import skillbill.ports.agentrun.ExecutableLookup
import skillbill.ports.goalrunner.persistence.GoalRunnerChildRepairStore
import skillbill.ports.goalrunner.runner.GoalPullRequestPort
import skillbill.ports.goalrunner.runner.GoalRunnerAttemptLedgerStore
import skillbill.ports.goalrunner.runner.GoalRunnerSubtaskLauncher

internal interface RuntimeGoalRunnerLaunchProvides {
  @Provides @JvmSynthetic
  fun goalPullRequestPort(callbacks: OptionalCallbacks): GoalPullRequestPort =
    callbacks.goalPullRequestPort ?: GhGoalPullRequestPort()

  @Provides @JvmSynthetic
  fun goalRunnerSubtaskLauncher(adapter: AgentRunGoalRunnerSubtaskLauncher): GoalRunnerSubtaskLauncher = adapter

  @Provides @JvmSynthetic
  fun agentRunLauncher(callbacks: OptionalCallbacks, adapter: FileSystemAgentRunLauncher): AgentRunLauncher =
    callbacks.agentRunLauncher ?: adapter

  @Provides @JvmSynthetic
  fun executableLookup(callbacks: OptionalCallbacks): ExecutableLookup =
    callbacks.executableLookup ?: PathExecutableLookup()

  @Provides @JvmSynthetic
  fun goalRunnerAttemptLedgerStore(adapter: WorkflowGoalRunnerOutcomeStore): GoalRunnerAttemptLedgerStore = adapter

  @Provides @JvmSynthetic
  fun goalRunnerChildRepairStore(adapter: WorkflowGoalRunnerOutcomeStore): GoalRunnerChildRepairStore = adapter
}
