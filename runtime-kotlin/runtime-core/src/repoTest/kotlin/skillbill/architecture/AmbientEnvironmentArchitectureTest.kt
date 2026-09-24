package skillbill.architecture

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class AmbientEnvironmentArchitectureTest {
  @Test
  fun `every declared module matches its ambient environment baseline`() {
    val drift = ArchitectureScanSupport.ambientEnvironmentDrift()
    assertEquals(emptyList(), drift, drift.joinToString("\n"))
  }

  @Test
  fun `ambient environment rule reports a violation placed in runtime-engine`() {
    val root = Files.createTempDirectory("skillbill-ambient-environment-rejection")
    seedModuleScanTreeWithEngineViolation(
      root,
      """
      package skillbill.engine

      val home = System.getenv("HOME")
      """.trimIndent(),
    )
    val drift = ArchitectureScanSupport.ambientEnvironmentDrift(scanRoot = root, readBaseline = { "" })
    assertEquals(
      listOf(
        "runtime-engine: $SYNTHETIC_ENGINE_VIOLATION_PATH:System.getenv():1 " +
          "is not listed in runtime-engine-ambient-environment-baseline.txt.",
      ),
      drift,
    )
  }

  @Test
  fun `ambient environment scanner fires on every unlisted banned form`() {
    val source =
      """
      package skillbill.example

      import java.nio.file.Path
      import java.nio.file.Paths

      val home = System.getenv("HOME")
      val configured = System.getProperty("user.home")
      val workingDir = Path.of("")
      val legacyWorkingDir = Paths.get("")
      """.trimIndent()
    val violations =
      ArchitectureScanSupport.ambientEnvironmentViolationsInSource(
        relativePath = EXAMPLE_PATH,
        source = source,
        baseline = emptySet(),
      )
    assertEquals(
      listOf(
        "$EXAMPLE_PATH:Path.of(\"\"):1 is not listed in the ambient-environment baseline.",
        "$EXAMPLE_PATH:Paths.get(\"\"):1 is not listed in the ambient-environment baseline.",
        "$EXAMPLE_PATH:System.getProperty():1 is not listed in the ambient-environment baseline.",
        "$EXAMPLE_PATH:System.getenv():1 is not listed in the ambient-environment baseline.",
      ),
      violations,
    )
  }

  private companion object {
    const val EXAMPLE_PATH = "runtime-kotlin/runtime-example/src/main/kotlin/Example.kt"
  }
}
