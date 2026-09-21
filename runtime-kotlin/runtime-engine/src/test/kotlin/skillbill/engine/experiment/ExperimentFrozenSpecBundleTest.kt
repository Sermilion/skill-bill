package skillbill.engine.experiment

import skillbill.engine.goalrunner.experiment.ExperimentFrozenSpecBundle
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class ExperimentFrozenSpecBundleTest {
  @Test
  fun `copies prepared specification bytes independently for an arm`() {
    val source = Files.createTempDirectory("prepared-spec-source")
    val destination = Files.createTempDirectory("prepared-spec-arm")
    val sourceFile = source.resolve("spec_subtask_1.md")
    Files.writeString(sourceFile, "frozen bytes")

    ExperimentFrozenSpecBundle.copy(source, destination)
    Files.writeString(sourceFile, "changed source")

    assertEquals("frozen bytes", Files.readString(destination.resolve("spec_subtask_1.md")))
  }
}
