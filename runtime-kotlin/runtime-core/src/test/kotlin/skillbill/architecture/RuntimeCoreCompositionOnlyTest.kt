package skillbill.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RuntimeCoreCompositionOnlyTest {
  private val runtimeKotlinRoot: Path =
    ArchitectureScanSupport.runtimeRoot.resolve("runtime-kotlin")

  @Test
  fun `every declared module has a Gradle edge expectation`() {
    val covered = RuntimeModuleCatalog.moduleEdgeExpectations.keys
    assertEquals(
      RuntimeModuleCatalog.declaredGradleModules.toSet(),
      covered,
      "Every module in declaredGradleModules must have an edge expectation entry.",
    )
  }

  @Test
  fun `module api edges match the recorded expectation`() {
    RuntimeModuleCatalog.moduleEdgeExpectations.forEach { (moduleName, expectation) ->
      val source = Files.readString(
        runtimeKotlinRoot.resolve(
          "${RuntimeModuleCatalog.gradleModuleIdToDirectoryPath(moduleName)}/build.gradle.kts",
        ),
      )
      assertModuleEdgesMatchExpectation(moduleName, source, expectation)
    }
  }

  @Test
  fun `module implementation edges match the recorded expectation`() {
    RuntimeModuleCatalog.moduleEdgeExpectations.forEach { (moduleName, expectation) ->
      val source = Files.readString(
        runtimeKotlinRoot.resolve(
          "${RuntimeModuleCatalog.gradleModuleIdToDirectoryPath(moduleName)}/build.gradle.kts",
        ),
      )
      assertModuleEdgesMatchExpectation(moduleName, source, expectation)
    }
  }

  @Test
  fun `runtime-core does not publish infrastructure or entrypoint modules as api`() {
    val source = Files.readString(runtimeKotlinRoot.resolve("runtime-core/build.gradle.kts"))
    val apiEdges = ArchitectureScanSupport.projectEdgesForConfiguration(source, "api")
    val banned =
      apiEdges.filter { edge ->
        edge.startsWith("runtime-infra-") ||
          edge == "runtime-cli" ||
          edge == "runtime-mcp"
      }
    assertTrue(
      banned.isEmpty(),
      "runtime-core must not publish infrastructure or entrypoint modules as api(...). " +
        "Offenders: $banned",
    )
  }

  @Test
  fun `project edge reader fails when an edge is added`() {
    val source = """
    dependencies {
      api(project(":runtime-application"))
      api(project(":runtime-ports"))
      implementation(project(":runtime-domain"))
      implementation(project(":runtime-contracts"))
      implementation(project(":runtime-infra:fs"))
      implementation(project(":runtime-infra:http"))
      implementation(project(":runtime-infra:sqlite"))
      implementation(project(":runtime-extra"))
    }
    """.trimIndent()
    assertFailsWith<AssertionError> {
      assertModuleEdgesMatchExpectation(
        "runtime-core",
        source,
        RuntimeModuleCatalog.moduleEdgeExpectations.getValue("runtime-core"),
      )
    }
  }

  @Test
  fun `project edge reader fails when an edge is removed`() {
    val source = """
      dependencies {
        api(project(":runtime-application"))
        api(project(":runtime-ports"))
        implementation(project(":runtime-domain"))
        implementation(project(":runtime-contracts"))
        implementation(project(":runtime-infra:fs"))
        implementation(project(":runtime-infra:http"))
      }
    """.trimIndent()
    assertFailsWith<AssertionError> {
      assertModuleEdgesMatchExpectation(
        "runtime-core",
        source,
        RuntimeModuleCatalog.moduleEdgeExpectations.getValue("runtime-core"),
      )
    }
  }

  @Test
  fun `project edge reader fails when an edge changes configuration`() {
    val source = """
      dependencies {
        api(project(":runtime-application"))
        api(project(":runtime-ports"))
        api(project(":runtime-domain"))
        implementation(project(":runtime-contracts"))
        implementation(project(":runtime-infra:fs"))
        implementation(project(":runtime-infra:http"))
        implementation(project(":runtime-infra:sqlite"))
      }
    """.trimIndent()
    assertFailsWith<AssertionError> {
      assertModuleEdgesMatchExpectation(
        "runtime-core",
        source,
        RuntimeModuleCatalog.moduleEdgeExpectations.getValue("runtime-core"),
      )
    }
  }

  private fun assertModuleEdgesMatchExpectation(
    moduleName: String,
    source: String,
    expectation: RuntimeModuleCatalog.ModuleEdgeExpectation,
  ) {
    assertEquals(
      expectation.api,
      ArchitectureScanSupport.projectEdgesForConfiguration(source, "api"),
      "$moduleName api(project(...)) edges drifted from the recorded expectation.",
    )
    assertEquals(
      expectation.implementation,
      ArchitectureScanSupport.projectEdgesForConfiguration(source, "implementation"),
      "$moduleName implementation(project(...)) edges drifted from the recorded expectation.",
    )
  }
}
