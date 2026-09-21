package skillbill.di.experiment
import me.tatarka.inject.annotations.Provides
import skillbill.contracts.experiment.codegraph.CodeGraphDependencyPayloadKeys
import skillbill.engine.experiment.ExperimentSelectionService
import skillbill.engine.experiment.codegraph.CodeGraphExperimentArmMeasurement
import skillbill.engine.experiment.codegraph.CodeGraphPairSetupService
import skillbill.engine.goalrunner.experiment.ExistingGoalRunnerParentDelivery
import skillbill.engine.goalrunner.experiment.ExperimentGoalRunnerFactory
import skillbill.engine.goalrunner.experiment.ExperimentGoalRunnerPort
import skillbill.experiment.model.ExperimentExecutionMode
import skillbill.infrastructure.contracts.experiment.ExperimentPayloadSchemaValidator
import skillbill.infrastructure.host.experiment.FileMachineExperimentConfigStore
import skillbill.infrastructure.host.experiment.catalog.FileSystemExperimentDescriptorCatalog
import skillbill.infrastructure.host.experiment.codegraph.CodeGraphDependencyLoader
import skillbill.infrastructure.host.experiment.codegraph.CodeGraphUsageLedgerHolder
import skillbill.infrastructure.host.experiment.codegraph.DeferredCodeGraphRetrievalPort
import skillbill.infrastructure.host.experiment.codegraph.FileSystemCodeGraphToolInstaller
import skillbill.infrastructure.sqlite.experiment.SqliteExperimentPairOwnerStore
import skillbill.model.EnvironmentContext
import skillbill.model.RuntimeContext
import skillbill.ports.config.RepoLocalConfigPort
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.experiment.codegraph.CodeGraphConfigurationKeys
import skillbill.ports.experiment.codegraph.CodeGraphPairSetupPort
import skillbill.ports.experiment.codegraph.CodeGraphRetrievalPort
import skillbill.ports.experiment.codegraph.CodeGraphToolInstallPort
import skillbill.ports.experiment.codegraph.CodeGraphUsageLedgerPort
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
import java.nio.file.Path

internal interface RuntimeExperimentProvides : RuntimeExperimentCodeGraphProvides {
  @Provides @JvmSynthetic
  fun experimentDescriptorCatalog(context: EnvironmentContext): ExperimentDescriptorCatalog? {
    val starts = listOf(
      context.repositoryRoot,
      context.userHome,
      Path.of(".").toAbsolutePath().normalize(),
    )
    val start = starts.firstOrNull(FileSystemExperimentDescriptorCatalog::hasCatalog) ?: return null
    return FileSystemExperimentDescriptorCatalog.discover(start)
  }

  @Provides @JvmSynthetic
  fun machineExperimentConfigStore(context: EnvironmentContext): MachineExperimentConfigStore =
    FileMachineExperimentConfigStore(context)

  @Provides @JvmSynthetic
  fun experimentSelectionPort(
    machineConfig: MachineExperimentConfigStore,
    repoLocalConfigPort: RepoLocalConfigPort,
    descriptorCatalog: ExperimentDescriptorCatalog?,
  ): ExperimentSelectionPort = ExperimentSelectionService(
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
  ): ExperimentGoalRunnerPort = ExperimentGoalRunnerPort { request ->
    val armId = request.experimentArmId
    if (armId == null) {
      factory.create(runtimeContext).run(request)
    } else {
      val armRoot = request.repoRoot.toAbsolutePath().normalize()
      factory.create(
        runtimeContext.copy(
          environment = runtimeContext.environment.copy(
            dbPathOverride = armRoot.resolve(".skill-bill/runtime.db").toString(),
            repositoryRoot = armRoot,
          ),
        ),
      ).run(request)
    }
  }

  @Provides @JvmSynthetic
  fun experimentParentDeliveryPort(
    manifestStore: GoalRunnerManifestStore,
    pullRequestPort: GoalPullRequestPort,
    gitOperations: WorkflowGitOperations,
  ): ExperimentParentDeliveryPort = ExistingGoalRunnerParentDelivery(
    manifestStore = manifestStore,
    pullRequestPort = pullRequestPort,
    gitOperations = gitOperations,
  )
}

internal interface RuntimeExperimentCodeGraphProvides {
  @Provides @JvmSynthetic
  fun codeGraphUsageLedger(): CodeGraphUsageLedgerPort = CodeGraphUsageLedgerHolder

  @Provides @JvmSynthetic
  fun codeGraphToolInstallPort(context: EnvironmentContext): CodeGraphToolInstallPort =
    FileSystemCodeGraphToolInstaller(context.repositoryRoot)

  @Provides @JvmSynthetic
  fun codeGraphPairSetupPort(
    toolInstallPort: CodeGraphToolInstallPort,
    usageLedger: CodeGraphUsageLedgerPort,
  ): CodeGraphPairSetupPort = CodeGraphPairSetupService(toolInstallPort, usageLedger)

  @Provides @JvmSynthetic
  fun codeGraphRetrievalPort(
    context: EnvironmentContext,
    toolInstallPort: CodeGraphToolInstallPort,
    usageLedger: CodeGraphUsageLedgerPort,
    descriptorCatalog: ExperimentDescriptorCatalog?,
  ): CodeGraphRetrievalPort? {
    descriptorCatalog?.resolve("codegraph", ExperimentExecutionMode.GOAL_PAIR) ?: return null
    val dependency = CodeGraphDependencyLoader.load(context.repositoryRoot)
    val releaseTag = (dependency[CodeGraphDependencyPayloadKeys.UPSTREAM] as? Map<*, *>)
      ?.get(CodeGraphDependencyPayloadKeys.RELEASE_TAG)
      ?.toString()
      ?.trim()
      ?: return null
    val configuredOverride = context.environment[
      CodeGraphConfigurationKeys.EXECUTABLE_OVERRIDE_ENV,
    ]?.trim()?.takeIf(String::isNotBlank)
    return DeferredCodeGraphRetrievalPort(
      binaryPath = {
        configuredOverride?.let { Path.of(it) }
          ?: toolInstallPort.resolveInstalledBinary(context.userHome, releaseTag)
      },
      usageLedger = usageLedger,
    )
  }

  @Provides @JvmSynthetic
  fun experimentArmMeasurementPort(
    descriptorCatalog: ExperimentDescriptorCatalog?,
    usageLedger: CodeGraphUsageLedgerPort,
  ): ExperimentArmMeasurementPort? = descriptorCatalog
    ?.resolve("codegraph", ExperimentExecutionMode.GOAL_PAIR)
    ?.let { CodeGraphExperimentArmMeasurement(usageLedger) }
}
