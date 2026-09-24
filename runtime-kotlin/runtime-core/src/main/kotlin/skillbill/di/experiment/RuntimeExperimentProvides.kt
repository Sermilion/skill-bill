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
import kotlin.random.Random

internal interface RuntimeExperimentProvides {
  @Provides
  fun experimentDescriptorCatalog(): ExperimentDescriptorCatalog? = null

  @Provides
  fun machineExperimentConfigStore(context: EnvironmentContext): MachineExperimentConfigStore =
    FileMachineExperimentConfigStore(context)

  @Provides
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

  @Provides
  fun experimentIsolationCapability(): ExperimentIsolationCapabilityPort = FilesystemExperimentIsolationCapability

  @Provides
  fun experimentPayloadValidation(): ExperimentPayloadValidationPort = ExperimentPayloadSchemaValidator()

  @Provides
  fun experimentPairOwner(
    database: DatabaseSessionFactory,
    payloadValidation: ExperimentPayloadValidationPort,
  ): ExperimentPairOwnerPort = SqliteExperimentPairOwnerStore(database, payloadValidation)

  @Provides
  fun experimentNavigationRunPort(coordinator: ExperimentNavigationPairCoordinator): ExperimentNavigationRunPort =
    coordinator

  @Provides
  fun experimentPairReportPort(service: ExperimentPairReportService): ExperimentPairReportPort = service

  @Provides
  fun experimentRandom(): Random = Random.Default
}
