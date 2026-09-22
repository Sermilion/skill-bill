package skillbill.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeGradleModuleLayeringTest {
  private val nestedInfrastructureModules = listOf(
    "runtime-infra:host",
    "runtime-infra:contracts",
    "runtime-infra:skills",
    "runtime-infra:launcher",
    "runtime-infra:workflow",
    "runtime-infra:http",
    "runtime-infra:sqlite",
  )

  private val runtimeRoot: Path = ArchitectureScanSupport.runtimeRoot

  @Test
  fun `settings declares runtime modules`() {
    assertEquals(
      RuntimeModuleCatalog.moduleEdgeExpectations.keys,
      declaredSettingsModules(),
    )
  }

  @Test
  fun `nested infrastructure ids resolve to nested directories and replace flat directories`() {
    nestedInfrastructureModules.forEach { moduleName ->
      val nestedDirectory = runtimeRoot.resolve(
        RuntimeModuleCatalog.runtimeKotlinModuleDirectory(moduleName),
      )
      assertTrue(Files.isDirectory(nestedDirectory), "Missing nested module directory: $nestedDirectory")
      assertFalse(
        Files.exists(runtimeRoot.resolve(moduleName.replace(':', '-'))),
        "Legacy flat module directory still exists for $moduleName.",
      )
    }
  }

  @Test
  fun `runtime build sources contain no flat infrastructure project references`() {
    val staleReferences = Files.walk(runtimeRoot).use { paths ->
      paths
        .filter { path ->
          Files.isRegularFile(path) &&
            !path.toString().contains("/build/") &&
            (
              path.fileName.toString().endsWith(".gradle.kts") ||
                path.fileName.toString().endsWith(".kt")
              )
        }
        .flatMap { path ->
          Regex("""project\(":runtime-infra-(?:fs|http|sqlite)""")
            .findAll(Files.readString(path))
            .map { "${runtimeRoot.relativize(path)}:${it.range.first + 1}" }
            .toList()
            .stream()
        }
        .toList()
    }
    assertEquals(emptyList(), staleReferences)
  }

  @Test
  fun `nested library builds apply the jvm-library convention`() {
    nestedInfrastructureModules.forEach { moduleName ->
      val build = Files.readString(
        runtimeRoot.resolve(
          "${RuntimeModuleCatalog.runtimeKotlinModuleDirectory(moduleName)}/build.gradle.kts",
        ),
      )
      assertContains(build, """id("skillbill.jvm-library")""")
    }
  }

  @Test
  fun `top level runtime modules do not depend upward`() {
    assertNoProjectDependencies("runtime-contracts")
    assertNoProjectDependencies(
      "runtime-domain",
      "runtime-ports",
      "runtime-application",
      "runtime-engine",
      "runtime-core",
    )
    assertNoProjectDependencies("runtime-ports", "runtime-application", "runtime-core")
    assertNoProjectDependencies(
      "runtime-application",
      "runtime-infra:host",
      "runtime-infra:contracts",
      "runtime-infra:skills",
      "runtime-infra:launcher",
      "runtime-infra:workflow",
      "runtime-infra:http",
      "runtime-infra:sqlite",
    )

    nestedInfrastructureModules.forEach { moduleName ->
      assertNoProjectDependencies(
        moduleName,
        "runtime-application",
        "runtime-core",
        "runtime-cli",
        "runtime-mcp",
      )
    }
  }

  private fun declaredSettingsModules(): Set<String> {
    val settings = Files.readString(runtimeRoot.resolve("runtime-kotlin/settings.gradle.kts"))
    val includeBlock =
      Regex("include\\((.*?)\\)", RegexOption.DOT_MATCHES_ALL)
        .find(settings)
        ?.groupValues
        ?.get(1)
        .orEmpty()
    return Regex("\"([A-Za-z0-9:-]+)\"")
      .findAll(includeBlock)
      .map { match -> match.groupValues[1] }
      .toSet()
  }

  private fun assertNoProjectDependencies(moduleName: String, vararg bannedDependencies: String) {
    val modulePath = RuntimeModuleCatalog.runtimeKotlinModuleDirectory(moduleName)
    val buildFile = runtimeRoot.resolve("$modulePath/build.gradle.kts")
    val source = Files.readString(buildFile)
    val projectDependencies =
      source.lineSequence()
        .filterNot { line -> TEST_CONFIGURATIONS.any { it in line } }
        .flatMap { line -> Regex("project\\(\":([A-Za-z0-9:-]+)\"\\)").findAll(line) }
        .map { match -> match.groupValues[1] }
        .toSet()
    val violations =
      if (bannedDependencies.isEmpty()) {
        projectDependencies
      } else {
        projectDependencies.intersect(bannedDependencies.toSet())
      }
    assertTrue(
      violations.isEmpty(),
      "$moduleName has banned project dependencies: ${violations.joinToString()}",
    )
  }

  private companion object {
    val TEST_CONFIGURATIONS: List<String> = listOf(
      "testImplementation",
      "testFixturesImplementation",
      "testFixturesApi",
      "testRuntimeOnly",
      "testCompileOnly",
    )
  }
}
