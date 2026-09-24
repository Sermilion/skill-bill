package skillbill.di.experiment

import me.tatarka.inject.annotations.Provides
import skillbill.engine.goalrunner.experiment.ExistingGoalRunnerParentDelivery
import skillbill.engine.goalrunner.experiment.ExperimentGoalRunnerFactory
import skillbill.engine.goalrunner.experiment.ExperimentGoalRunnerPort
import skillbill.model.RuntimeContext
import skillbill.ports.experiment.measurement.ExperimentArmMeasurementPort
import skillbill.ports.experiment.publication.ExperimentParentDeliveryPort
import skillbill.ports.goalrunner.runner.GoalPullRequestPort
import skillbill.ports.goalrunner.runner.GoalRunnerManifestStore
import skillbill.ports.workflow.gitops.WorkflowGitOperations

internal interface RuntimeExperimentGoalProvides {
  @Provides @JvmSynthetic
  fun experimentGoalRunnerPort(
    factory: ExperimentGoalRunnerFactory,
    runtimeContext: RuntimeContext,
  ): ExperimentGoalRunnerPort =
    ExperimentGoalRunnerPort { request ->
      val armId = request.experimentArmId
      if (armId == null) {
        factory.create(runtimeContext).run(request)
      } else {
        val armRoot = request.repoRoot.toAbsolutePath().normalize()
        factory.create(
          runtimeContext.copy(
            environment =
              runtimeContext.environment.copy(
                dbPathOverride = armRoot.resolve(".skill-bill/runtime.db").toString(),
                repositoryRoot = armRoot,
              ),
          ),
        ).run(request)
      }
    }

  @Provides @JvmSynthetic
  fun experimentArmMeasurementPort(): ExperimentArmMeasurementPort? = null

  @Provides @JvmSynthetic
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
