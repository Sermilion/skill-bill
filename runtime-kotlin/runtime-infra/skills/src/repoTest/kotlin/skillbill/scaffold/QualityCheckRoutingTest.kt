package skillbill.scaffold

import skillbill.error.shellcontent.MissingValidationGateError
import skillbill.infrastructure.skills.externalplatformpack.FileExternalPlatformPackSourceConfigStore
import skillbill.infrastructure.skills.scaffold.platformpack.catalog.PlatformPackCatalogLoader
import skillbill.infrastructure.skills.scaffold.platformpack.manifest.routeQualityCheck
import skillbill.testing.repoRootFromTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QualityCheckRoutingTest {
  private val isolatedConfigHome: Path = Files.createTempDirectory("skillbill-quality-routing-home")

  private fun routeRepo(evidence: Collection<String>) =
    routeQualityCheck(
      repoRootFromTest(),
      evidence,
      isolatedConfigHome,
      catalogLoader = PlatformPackCatalogLoader(FileExternalPlatformPackSourceConfigStore()),
    )

  @Test
  fun `every maintained dominant stack routes to bill-code-check with pack slug unchanged`() {
    val cases =
      listOf(
        Triple("go", "services/orders/main.go", "bill-code-check"),
        Triple("ios", "App.xcodeproj/project.pbxproj", "bill-code-check"),
        Triple("kotlin", "config/detekt.yml", "bill-code-check"),
        Triple("kmp", "shared/src/commonMain/kotlin/App.kt org.jetbrains.kotlin.multiplatform", "bill-code-check"),
        Triple("php", "composer.json", "bill-code-check"),
        Triple("python", "pyproject.toml", "bill-code-check"),
        Triple("rust", "Cargo.toml", "bill-code-check"),
        Triple("typescript", "tsconfig.json", "bill-code-check"),
      )

    cases.forEach { (stack, evidence, routedSkill) ->
      val route = assertNotNull(routeRepo(listOf(evidence)))
      assertEquals(stack, route.detectedStack)
      assertEquals(routedSkill, route.routedSkill)
      assertFalse(route.fallback)
      assertNull(route.fallbackReason)
    }
  }

  @Test
  fun `ordinary Kotlin and multiplatform paths apply adjacent-pack dominance`() {
    val cases =
      listOf(
        "Feature.kt" to "kotlin",
        "src/main/kotlin/example/Feature.kt" to "kotlin",
        "shared/src/commonMain/kotlin/example/Feature.kt" to "kmp",
        "shared/src/androidMain/kotlin/example/Feature.kt" to "kmp",
        "shared/src/iosMain/kotlin/example/Feature.kt" to "kmp",
        "plugins { kotlin(\"multiplatform\") }" to "kmp",
      )

    cases.forEach { (evidence, expected) ->
      assertEquals(expected, assertNotNull(routeRepo(listOf(evidence))).detectedStack)
    }
  }

  @Test
  fun `mixed Kotlin and KMP ownership routes through the KMP pack with bill-code-check`() {
    val route =
      assertNotNull(
        routeRepo(
          listOf(
            "server/src/main/kotlin/App.kt",
            "server/build.gradle.kts",
            "settings.gradle.kts",
            "config/detekt.yml",
            "shared/src/commonMain/kotlin/Shared.kt",
          ),
        ),
      )

    assertEquals("kmp", route.detectedStack)
    assertEquals("bill-code-check", route.routedSkill)
  }

  @Test
  fun `bill-code-check shell uses only the winning pack validation gate commands`() {
    val content = Files.readString(repoRootFromTest().resolve("skills/bill-code-check/content.md"))

    assertTrue("validation_gate.collect_all_full_gate_command" in content)
    assertTrue("validation_gate.cache_bypassing_collect_all_full_gate_command" in content)
    assertTrue("Do not read a pack quality-check sidecar" in content)
    assertTrue("Do not fall back to another pack, a sidecar, or a conventional task name." in content)
  }

  @Test
  fun `dominant pack without validation_gate throws typed missing-gate error`() {
    val error =
      assertFailsWith<MissingValidationGateError> {
        routeRepo(listOf("manifest-declared code-review fallback"))
      }
    assertContains(error.message.orEmpty(), "generic")
  }

  @Test
  fun `literal signals do not match unrelated substrings`() {
    val route = routeRepo(listOf("docs/unexpected-results.md", "docs/actuality.md", "docs/toolkit-notes.md"))

    assertNull(route)
  }

  @Test
  fun `unresolved mixed-stack evidence does not select by pack ordering`() {
    val failure =
      assertFailsWith<IllegalArgumentException> {
        routeRepo(listOf("src/main.go", "src/main.rs"))
      }

    assertTrue(failure.message.orEmpty().contains("ambiguous"))
  }
}
