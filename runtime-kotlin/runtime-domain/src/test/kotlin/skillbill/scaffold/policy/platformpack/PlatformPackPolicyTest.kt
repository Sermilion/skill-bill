package skillbill.scaffold.policy.platformpack

import skillbill.model.FileLocation
import skillbill.scaffold.policy.sharedContractNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlatformPackPolicyTest {
  @Test
  fun `buildPlatformPackInstallPaths includes baseline and selected specialists`() {
    val packRoot = FileLocation("/repo/platform-packs/java")
    val specialistPaths =
      mapOf(
        "ui" to packRoot.resolve("code-review").resolve("bill-java-code-review-ui"),
      )

    val paths =
      buildPlatformPackInstallPaths(
        packRoot = packRoot,
        baselineName = "bill-java-code-review",
        specialistPaths = specialistPaths,
        selectedAreas = listOf("ui"),
      )

    assertEquals(2, paths.size)
    assertEquals(packRoot.resolve("code-review").resolve("bill-java-code-review"), paths[0])
    assertEquals(specialistPaths.getValue("ui"), paths[1])
  }

  @Test
  fun `platformPackNotes mentions preset when applied and includes shared contract note`() {
    val notes =
      platformPackNotes(
        platform = "java",
        presetUsed = true,
        selectedAreas = listOf("ui", "security"),
      )

    assertTrue(notes.any { it.contains("built-in platform preset for 'java'") })
    assertTrue(notes.any { it.contains("Full platform pack scaffolded with 2 approved code-review area stubs") })
    assertTrue(notes.contains(sharedContractNote()))
  }

  @Test
  fun `platformPackNotes reports full pack creation without preset note`() {
    val notes =
      platformPackNotes(
        platform = "custom",
        presetUsed = false,
        selectedAreas = listOf("ui"),
      )

    assertTrue(notes.any { it.contains("Full platform pack scaffolded with 1 approved code-review area stubs") })
    assertTrue(notes.none { it.contains("built-in platform preset") })
  }
}
