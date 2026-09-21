package skillbill.infrastructure.host.experiment
import skillbill.experiment.model.ExperimentExecutionMode
import skillbill.infrastructure.host.experiment.catalog.FileSystemExperimentDescriptorCatalog
import skillbill.ports.experiment.descriptor.ExperimentDescriptorCatalog
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FileSystemExperimentDescriptorCatalogTest {
  @Test
  fun `production catalog registers codegraph for goal pairs`() {
    val repoRoot = locateSkillBillRepoRoot()
    val catalog: ExperimentDescriptorCatalog = FileSystemExperimentDescriptorCatalog.discover(repoRoot)
    val descriptor = catalog.resolve("codegraph", ExperimentExecutionMode.GOAL_PAIR)
    assertNotNull(descriptor, "dropping the YAML without a catalog loader leaves codegraph unknown")
    assertEquals("codegraph", descriptor.treatmentCapability)
    assertEquals(ExperimentExecutionMode.GOAL_PAIR, descriptor.executionMode)
  }

  private fun locateSkillBillRepoRoot(): Path {
    val cwd = Path.of(".").toAbsolutePath().normalize()
    return FileSystemExperimentDescriptorCatalog.findRepoRootForExperiments(cwd)
      ?: error("Expected orchestration/experiments under the skill-bill repository root.")
  }
}
