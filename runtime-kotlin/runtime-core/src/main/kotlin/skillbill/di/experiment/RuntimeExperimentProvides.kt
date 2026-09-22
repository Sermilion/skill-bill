package skillbill.di.experiment
import me.tatarka.inject.annotations.Provides
import skillbill.engine.experiment.ExperimentSelectionService
import skillbill.engine.goalrunner.experiment.ExistingGoalRunnerParentDelivery
import skillbill.engine.goalrunner.experiment.ExperimentGoalRunnerFactory
import skillbill.engine.goalrunner.experiment.ExperimentGoalRunnerPort
import skillbill.infrastructure.contracts.experiment.ExperimentPayloadSchemaValidator
import skillbill.infrastructure.host.experiment.FileMachineExperimentConfigStore
import skillbill.infrastructure.sqlite.experiment.SqliteExperimentPairOwnerStore
import skillbill.model.EnvironmentContext
import skillbill.model.RuntimeContext
import skillbill.ports.config.RepoLocalConfigPort
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.experiment.config.MachineExperimentConfigStore
import skillbill.ports.experiment.descriptor.ExperimentDescriptorCatalog
import skillbill.ports.experiment.isolation.ExperimentIsolationCapabilityPort
import skillbill.ports.experiment.measurement.ExperimentArmMeasurementPort
import skillbill.ports.experiment.pair.ExperimentPairOwnerPort
import skillbill.ports.experiment.publication.ExperimentParentDeliveryPort
import skillbill.ports.experiment.selection.ExperimentSelectionPort
import skillbill.ports.experiment.validation.ExperimentPayloadValidationPort
import skillbill.ports.goalrunner.runner.GoalPullRequestPort
import skillbill.ports.goalrunner.runner.GoalRunnerManifestStore
import skillbill.ports.workflow.gitops.WorkflowGitOperations

internal interface RuntimeExperimentProvides {
  @Provides @JvmSynthetic
  fun experimentDescriptorCatalog(): ExperimentDescriptorCatalog? = null

  @Provides @JvmSynthetic
  fun machineExperimentConfigStore(context: EnvironmentContext): MachineExperimentConfigStore =
    FileMachineExperimentConfigStore(context)

  @Provides @JvmSynthetic
  fun experimentSelectionPort(
    machineConfig: MachineExperimentConfigStore,
    repoLocalConfigPort: RepoLocalConfigPort,
    descriptorCatalog: ExperimentDescriptorCatalog?,
  ): ExperimentSelectionPort =
    ExperimentSelectionService(
      machineConfig = machineConfig,
      repoLocalConfigPort = repoLocalConfigPort,
      descriptorCatalog = descriptorCatalog,
    )

  @Provides @JvmSynthetic
  fun experimentIsolationCapability(): ExperimentIsolationCapabilityPort = FilesystemExperimentIsolationCapability

  @Provides @JvmSynthetic
  fun experimentPayloadValidation(): ExperimentPayloadValidationPort = ExperimentPayloadSchemaValidator()

  @Provides @JvmSynthetic
  fun experimentPairOwner(
    database: DatabaseSessionFactory,
    payloadValidation: ExperimentPayloadValidationPort,
  ): ExperimentPairOwnerPort = SqliteExperimentPairOwnerStore(database, payloadValidation)

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
