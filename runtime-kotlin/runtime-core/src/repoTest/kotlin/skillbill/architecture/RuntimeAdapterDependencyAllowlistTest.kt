package skillbill.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class RuntimeAdapterDependencyAllowlistTest {
  private val runtimeRoot: Path = ArchitectureScanSupport.runtimeRoot

  @Test
  fun `every declared module has only the curated main-source runtime project dependencies`() {
    assertEquals(
      RuntimeModuleCatalog.declaredGradleModules.toSet(),
      RuntimeModuleCatalog.mainProjectDependenciesByModule.keys,
      "RuntimeAdapterDependencyAllowlistTest must classify every declared Gradle module.",
    )

    val drift =
      RuntimeModuleCatalog.declaredGradleModules.mapNotNull { moduleName ->
        val expected = RuntimeModuleCatalog.mainProjectDependenciesByModule.getValue(moduleName)
        val actual = mainProjectDependencies(moduleName)
        val missing = expected - actual
        val extra = actual - expected
        if (missing.isEmpty() && extra.isEmpty()) {
          null
        } else {
          buildString {
            append(moduleName)
            if (missing.isNotEmpty()) {
              append("\n  Missing: ")
              append(missing.sorted().joinToString())
            }
            if (extra.isNotEmpty()) {
              append("\n  Extra: ")
              append(extra.sorted().joinToString())
            }
          }
        }
      }

    assertEquals(
      emptyList(),
      drift,
      "Main-source project dependencies drifted from the curated per-module allow-list.",
    )
  }

  @Test
  fun `every declared module has only the curated test-fixtures runtime project dependencies`() {
    assertEquals(
      RuntimeModuleCatalog.declaredGradleModules.toSet(),
      RuntimeModuleCatalog.testFixturesProjectDependenciesByModule.keys,
      "RuntimeAdapterDependencyAllowlistTest must classify every declared Gradle module.",
    )

    val drift =
      RuntimeModuleCatalog.declaredGradleModules.mapNotNull { moduleName ->
        val expected = RuntimeModuleCatalog.testFixturesProjectDependenciesByModule.getValue(moduleName)
        val actual = testFixturesProjectDependencies(moduleName)
        val missing = expected - actual
        val extra = actual - expected
        if (missing.isEmpty() && extra.isEmpty()) {
          null
        } else {
          buildString {
            append(moduleName)
            if (missing.isNotEmpty()) {
              append("\n  Missing: ")
              append(missing.sorted().joinToString())
            }
            if (extra.isNotEmpty()) {
              append("\n  Extra: ")
              append(extra.sorted().joinToString())
            }
          }
        }
      }

    assertEquals(
      emptyList(),
      drift,
      "Test-fixtures-source project dependencies drifted from the curated per-module allow-list.",
    )
  }

  private fun testFixturesProjectDependencies(moduleName: String): Set<String> {
    val buildFile =
      runtimeRoot.resolve(
        "${RuntimeModuleCatalog.runtimeKotlinModuleDirectory(moduleName)}/build.gradle.kts",
      )
    val source = Files.readString(buildFile)
    val testFixturesConfigurations = listOf("testFixturesImplementation", "testFixturesApi")
    val projectDependencies = mutableSetOf<String>()
    source.lineSequence().forEach { line ->
      if (testFixturesConfigurations.any { configName -> line.contains(configName) }) {
        Regex("project\\(\":([A-Za-z0-9:-]+)\"\\)")
          .findAll(line)
          .forEach { match -> projectDependencies += match.groupValues[1] }
      }
    }
    return projectDependencies
  }

  private fun mainProjectDependencies(moduleName: String): Set<String> {
    val buildFile =
      runtimeRoot.resolve(
        "${RuntimeModuleCatalog.runtimeKotlinModuleDirectory(moduleName)}/build.gradle.kts",
      )
    val source = Files.readString(buildFile)
    val testConfigurations =
      listOf(
        "testImplementation",
        "testFixturesImplementation",
        "testFixturesApi",
        "testRuntimeOnly",
        "testCompileOnly",
        "androidTestImplementation",
        "jvmTestImplementation",
        "commonTestImplementation",
      )
    val testBlockOpen =
      Regex("^\\s*(jvmTest|androidTest|commonTest)\\.dependencies\\s*\\{")
    val projectDependencies = mutableSetOf<String>()
    var depth = 0
    var testBlockDepth = -1
    source.lineSequence().forEach { line ->
      val openMatch = testBlockOpen.find(line)
      if (openMatch != null && testBlockDepth < 0) {
        testBlockDepth = depth
      }
      val inTestBlock = testBlockDepth in 0..depth
      val isTestConfig = testConfigurations.any { configName -> line.contains(configName) }
      if (!inTestBlock && !isTestConfig) {
        Regex("project\\(\":([A-Za-z0-9:-]+)\"\\)")
          .findAll(line)
          .forEach { match -> projectDependencies += match.groupValues[1] }
      }
      depth += line.count { it == '{' }
      depth -= line.count { it == '}' }
      if (depth <= testBlockDepth) {
        testBlockDepth = -1
      }
    }
    return projectDependencies
  }
}
