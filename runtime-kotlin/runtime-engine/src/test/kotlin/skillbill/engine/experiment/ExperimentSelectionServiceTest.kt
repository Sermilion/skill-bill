package skillbill.engine.experiment

import skillbill.config.model.ExperimentAvailabilityPolicy
import skillbill.config.model.RepoLocalConfig
import skillbill.error.shellcontent.ExperimentDescriptorUnavailableError
import skillbill.error.shellcontent.ExperimentSelectionConflictError
import skillbill.error.shellcontent.InvalidExperimentDescriptorSchemaError
import skillbill.experiment.model.ExperimentExecutionMode
import skillbill.ports.config.RepoLocalConfigPort
import skillbill.ports.config.model.ReadRepoLocalConfigRequest
import skillbill.ports.config.model.ReadRepoLocalConfigResult
import skillbill.ports.experiment.config.MachineExperimentConfigStore
import skillbill.ports.experiment.descriptor.ExperimentDescriptorCatalog
import skillbill.ports.experiment.descriptor.model.ExperimentDescriptorRecord
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExperimentSelectionServiceTest {
  @Test
  fun `omitted parameter stays ordinary even when machine config enables experiments`() {
    val service = service(
      machinePolicy = ExperimentAvailabilityPolicy.ExplicitNames(listOf("fixture-goal")),
      catalog = validCatalog(),
    )

    val selection = service.resolveForLaunch(
      repoRoot = Path.of("."),
      parameter = null,
      mode = ExperimentExecutionMode.GOAL_PAIR,
      savedSelection = null,
    )

    assertEquals(emptyList(), selection.normalizedNames)
    assertEquals(emptySet(), selection.treatmentCapabilities)
  }

  @Test
  fun `comma separated selection is one normalized pair with all treatment capabilities`() {
    val service = service(
      machinePolicy = null,
      catalog = validCatalog(
        ExperimentDescriptorRecord(
          name = "second-fixture",
          descriptorVersion = "test-1",
          executionMode = ExperimentExecutionMode.GOAL_PAIR,
          requiredLauncherCapabilities = emptySet(),
          treatmentCapability = "second-treatment",
        ),
      ),
    )

    val selection = service.resolveForLaunch(
      repoRoot = Path.of("."),
      parameter = "second-fixture,fixture-goal",
      mode = ExperimentExecutionMode.GOAL_PAIR,
      savedSelection = null,
    )

    assertEquals(listOf("fixture-goal", "second-fixture"), selection.normalizedNames)
    assertEquals(setOf("fixture-treatment", "second-treatment"), selection.treatmentCapabilities)
  }

  @Test
  fun `saved selection remains captured when current availability is disabled`() {
    val service = service(
      machinePolicy = ExperimentAvailabilityPolicy.Disabled,
      catalog = validCatalog(),
    )

    val selection = service.resolveForLaunch(
      repoRoot = Path.of("."),
      parameter = null,
      mode = ExperimentExecutionMode.GOAL_PAIR,
      savedSelection = listOf("fixture-goal"),
    )

    assertEquals(listOf("fixture-goal"), selection.normalizedNames)
  }

  @Test
  fun `empty repository availability disables a requested name before launch`() {
    val service = service(
      machinePolicy = ExperimentAvailabilityPolicy.Disabled,
      catalog = validCatalog(),
    )

    assertFailsWith<ExperimentDescriptorUnavailableError> {
      service.resolveForLaunch(Path.of("."), "fixture-goal", ExperimentExecutionMode.GOAL_PAIR, null)
    }
  }

  @Test
  fun `explicit resume selection rejects a conflicting normalized set`() {
    val service = service(machinePolicy = null, catalog = validCatalog())

    assertFailsWith<ExperimentSelectionConflictError> {
      service.resolveForLaunch(
        repoRoot = Path.of("."),
        parameter = "none",
        mode = ExperimentExecutionMode.GOAL_PAIR,
        savedSelection = listOf("fixture-goal"),
      )
    }
  }

  @Test
  fun `invalid descriptor capability fails before selection is returned`() {
    val service = ExperimentSelectionService(
      machineConfig = object : MachineExperimentConfigStore {
        override fun readExperimentsAvailability() = null
      },
      repoLocalConfigPort = object : RepoLocalConfigPort {
        override fun readRepoLocalConfig(request: ReadRepoLocalConfigRequest) =
          ReadRepoLocalConfigResult(RepoLocalConfig.defaults())
      },
      descriptorCatalog = invalidDescriptorCatalog(),
    )

    assertFailsWith<InvalidExperimentDescriptorSchemaError> {
      service.resolveForLaunch(
        repoRoot = Path.of("."),
        parameter = "fixture-goal",
        mode = ExperimentExecutionMode.GOAL_PAIR,
        savedSelection = null,
      )
    }
  }

  @Test
  fun `mode-incompatible descriptor is rejected instead of entering a goal pair`() {
    val navigationDescriptor = ExperimentDescriptorRecord(
      name = "fixture-navigation",
      descriptorVersion = "test-1",
      executionMode = ExperimentExecutionMode.NAVIGATION,
      requiredLauncherCapabilities = emptySet(),
      treatmentCapability = "fixture-treatment",
    )
    val service = service(
      machinePolicy = null,
      catalog = object : ExperimentDescriptorCatalog {
        override fun listCompatible(mode: ExperimentExecutionMode): List<ExperimentDescriptorRecord> =
          listOf(navigationDescriptor)

        override fun resolve(name: String, mode: ExperimentExecutionMode): ExperimentDescriptorRecord? =
          navigationDescriptor.takeIf { it.name == name && it.executionMode == mode }
      },
    )

    assertFailsWith<InvalidExperimentDescriptorSchemaError> {
      service.resolveForLaunch(
        repoRoot = Path.of("."),
        parameter = "fixture-navigation",
        mode = ExperimentExecutionMode.GOAL_PAIR,
        savedSelection = null,
      )
    }
  }

  private fun invalidDescriptorCatalog(): ExperimentDescriptorCatalog = object : ExperimentDescriptorCatalog {
    private val descriptor = ExperimentDescriptorRecord(
      name = "fixture-goal",
      descriptorVersion = "test-1",
      executionMode = ExperimentExecutionMode.GOAL_PAIR,
      requiredLauncherCapabilities = setOf(""),
      treatmentCapability = "fixture-treatment",
    )

    override fun listCompatible(mode: ExperimentExecutionMode): List<ExperimentDescriptorRecord> = listOf(descriptor)

    override fun resolve(name: String, mode: ExperimentExecutionMode): ExperimentDescriptorRecord? =
      descriptor.takeIf { it.name == name && it.executionMode == mode }
  }

  private fun service(
    machinePolicy: ExperimentAvailabilityPolicy?,
    catalog: ExperimentDescriptorCatalog,
  ): ExperimentSelectionService = ExperimentSelectionService(
    machineConfig = object : MachineExperimentConfigStore {
      override fun readExperimentsAvailability() = machinePolicy
    },
    repoLocalConfigPort = object : RepoLocalConfigPort {
      override fun readRepoLocalConfig(request: ReadRepoLocalConfigRequest) =
        ReadRepoLocalConfigResult(RepoLocalConfig.defaults())
    },
    descriptorCatalog = catalog,
  )

  private fun validCatalog(vararg additional: ExperimentDescriptorRecord): ExperimentDescriptorCatalog {
    val records = listOf(
      ExperimentDescriptorRecord(
        name = "fixture-goal",
        descriptorVersion = "test-1",
        executionMode = ExperimentExecutionMode.GOAL_PAIR,
        requiredLauncherCapabilities = emptySet(),
        treatmentCapability = "fixture-treatment",
      ),
    ) + additional
    return object : ExperimentDescriptorCatalog {
      override fun listCompatible(mode: ExperimentExecutionMode): List<ExperimentDescriptorRecord> =
        records.filter { it.executionMode == mode }

      override fun resolve(name: String, mode: ExperimentExecutionMode): ExperimentDescriptorRecord? =
        records.firstOrNull { it.name == name && it.executionMode == mode }
    }
  }
}
