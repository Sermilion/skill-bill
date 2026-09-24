package skillbill.di.experiment
import me.tatarka.inject.annotations.Provides
import skillbill.engine.experiment.ExperimentSelectionService
import skillbill.engine.experiment.report.ExperimentPairReportService
import skillbill.engine.goalrunner.experiment.ExperimentNavigationPairCoordinator
import skillbill.infrastructure.contracts.experiment.ExperimentPayloadSchemaValidator
import skillbill.infrastructure.host.experiment.FileMachineExperimentConfigStore
import skillbill.infrastructure.sqlite.experiment.SqliteExperimentPairOwnerStore
import skillbill.model.EnvironmentContext
import skillbill.ports.config.RepoLocalConfigPort
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.experiment.config.MachineExperimentConfigStore
import skillbill.ports.experiment.descriptor.ExperimentDescriptorCatalog
import skillbill.ports.experiment.isolation.ExperimentIsolationCapabilityPort
import skillbill.ports.experiment.navigation.ExperimentNavigationRunPort
import skillbill.ports.experiment.pair.ExperimentPairOwnerPort
import skillbill.ports.experiment.pair.ExperimentPairReportPort
import skillbill.ports.experiment.selection.ExperimentSelectionPort
import skillbill.ports.experiment.validation.ExperimentPayloadValidationPort

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
  fun experimentNavigationRunPort(coordinator: ExperimentNavigationPairCoordinator): ExperimentNavigationRunPort =
    coordinator

  @Provides @JvmSynthetic
  fun experimentPairReportPort(service: ExperimentPairReportService): ExperimentPairReportPort = service
}
