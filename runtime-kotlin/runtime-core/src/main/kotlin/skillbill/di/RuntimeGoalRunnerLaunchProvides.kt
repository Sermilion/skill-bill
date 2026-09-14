package skillbill.di

import me.tatarka.inject.annotations.Provides
import skillbill.application.agentrun.AgentRunGoalRunnerSubtaskLauncher
import skillbill.infrastructure.fs.GhGoalPullRequestPort
import skillbill.infrastructure.fs.launcher.agentrun.FileSystemAgentRunLauncher
import skillbill.infrastructure.fs.launcher.agentrun.PathExecutableLookup
import skillbill.model.OptionalCallbacks
import skillbill.ports.agentrun.AgentRunLauncher
import skillbill.ports.agentrun.ExecutableLookup
import skillbill.ports.goalrunner.runner.GoalPullRequestPort
import skillbill.ports.goalrunner.runner.GoalRunnerSubtaskLauncher

internal interface RuntimeGoalRunnerLaunchProvides {
  @Provides @JvmSynthetic
  fun goalPullRequestPort(callbacks: OptionalCallbacks, adapter: GhGoalPullRequestPort): GoalPullRequestPort =
    callbacks.goalPullRequestPort ?: adapter

  @Provides @JvmSynthetic
  fun goalRunnerSubtaskLauncher(adapter: AgentRunGoalRunnerSubtaskLauncher): GoalRunnerSubtaskLauncher = adapter

  @Provides @JvmSynthetic
  fun agentRunLauncher(callbacks: OptionalCallbacks, adapter: FileSystemAgentRunLauncher): AgentRunLauncher =
    callbacks.agentRunLauncher ?: adapter

  @Provides @JvmSynthetic
  fun executableLookup(callbacks: OptionalCallbacks): ExecutableLookup =
    callbacks.executableLookup ?: PathExecutableLookup()
}
