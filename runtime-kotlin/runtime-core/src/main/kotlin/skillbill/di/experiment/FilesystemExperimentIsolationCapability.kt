package skillbill.di.experiment

import skillbill.error.shellcontent.ExperimentIsolationCapabilityRefusalError
import skillbill.ports.experiment.isolation.ExperimentArmIsolationContext
import skillbill.ports.experiment.isolation.ExperimentIsolationCapabilityPort

internal object FilesystemExperimentIsolationCapability : ExperimentIsolationCapabilityPort {
  private const val REQUIRED_WRITABLE_PATH_COUNT = 6

  override fun assertLaunchSupported(context: ExperimentArmIsolationContext) {
    val paths = listOf(
      context.statePaths.runtimeDatabase,
      context.statePaths.learningStore,
      context.statePaths.graphIndex,
      context.statePaths.buildOutput,
      context.statePaths.writableCache,
      context.statePaths.worktreeEditJournal,
    ).filterNotNull()
    if (paths.size != REQUIRED_WRITABLE_PATH_COUNT || paths.distinct().size != paths.size) {
      throw ExperimentIsolationCapabilityRefusalError(
        "Experiment arm ${context.armId.wireValue} does not have distinct writable state paths.",
      )
    }
    if (context.statePaths.sharedHostCaches.isEmpty()) {
      throw ExperimentIsolationCapabilityRefusalError(
        "Experiment arm ${context.armId.wireValue} did not declare shared host caches.",
      )
    }
  }
}
