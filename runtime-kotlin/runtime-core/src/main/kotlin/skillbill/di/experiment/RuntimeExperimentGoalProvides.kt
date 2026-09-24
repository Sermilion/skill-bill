package skillbill.di.experiment

import me.tatarka.inject.annotations.Provides
import skillbill.engine.goalrunner.experiment.ExistingGoalRunnerParentDelivery
import skillbill.ports.experiment.measurement.ExperimentArmMeasurementPort
import skillbill.ports.experiment.publication.ExperimentParentDeliveryPort
import skillbill.ports.goalrunner.runner.GoalPullRequestPort
import skillbill.ports.goalrunner.runner.GoalRunnerManifestStore
import skillbill.ports.workflow.gitops.WorkflowGitOperations

internal interface RuntimeExperimentGoalProvides {
  @Provides
  fun experimentArmMeasurementPort(): ExperimentArmMeasurementPort? = null

  @Provides
  fun experimentParentDeliveryPort(
    manifestStore: GoalRunnerManifestStore,
    pullRequestPort: GoalPullRequestPort,
    gitOperations: WorkflowGitOperations,
  ): ExperimentParentDeliveryPort =
    ExistingGoalRunnerParentDelivery(
      manifestStore = manifestStore,
      pullRequestPort = pullRequestPort,
      gitOperations = gitOperations,
    )
}
