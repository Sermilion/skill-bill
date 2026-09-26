package skillbill.ports.experiment.descriptor

import skillbill.experiment.model.ExperimentExecutionMode
import skillbill.ports.experiment.descriptor.model.ExperimentDescriptorRecord

object TestExperimentDescriptorCatalog {
  val goalPairFixture: ExperimentDescriptorRecord =
    ExperimentDescriptorRecord(
      name = "fixture-goal",
      descriptorVersion = "test-1",
      executionMode = ExperimentExecutionMode.GOAL_PAIR,
      requiredLauncherCapabilities = emptySet(),
      treatmentCapability = "fixture-treatment",
    )

  val navigationFixture: ExperimentDescriptorRecord =
    ExperimentDescriptorRecord(
      name = "fixture-navigation",
      descriptorVersion = "test-1",
      executionMode = ExperimentExecutionMode.NAVIGATION,
      requiredLauncherCapabilities = emptySet(),
      treatmentCapability = "fixture-navigation-treatment",
    )

  fun catalog(records: List<ExperimentDescriptorRecord> = listOf(goalPairFixture, navigationFixture)) =
    object : ExperimentDescriptorCatalog {
      override fun listCompatible(mode: ExperimentExecutionMode): List<ExperimentDescriptorRecord> =
        records.filter { it.executionMode == mode }

      override fun resolve(
        name: String,
        mode: ExperimentExecutionMode,
      ): ExperimentDescriptorRecord? = records.firstOrNull { it.name == name && it.executionMode == mode }
    }
}
