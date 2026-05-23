package skillbill.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImplementationOwnershipArchitectureTest {
  private val runtimeRoot: Path =
    Path.of("").toAbsolutePath().normalize().let { workingDir ->
      if (workingDir.fileName.toString().startsWith("runtime-")) {
        workingDir.parent
      } else {
        workingDir
      }
    }

  @Test
  fun `implementation ownership moved out of runtime core`() {
    listOf(
      "skillbill/install",
      "skillbill/scaffold",
      "skillbill/nativeagent",
      "skillbill/launcher",
      "skillbill/skillremove",
      "skillbill/workflow",
    ).forEach { packagePath ->
      assertTrue(
        !Files.exists(runtimeRoot.resolve("runtime-core/src/main/kotlin/$packagePath")),
        "runtime-core must not own moved implementation package $packagePath",
      )
    }

    listOf(
      "skillbill/install/InstallOperations.kt",
      "skillbill/scaffold/ScaffoldService.kt",
      "skillbill/nativeagent/NativeAgentOperations.kt",
      "skillbill/launcher/McpRegistrationOperations.kt",
      "skillbill/skillremove/SkillRemoveJvmFileSystem.kt",
    ).forEach { packagePath ->
      assertTrue(
        Files.isRegularFile(runtimeRoot.resolve("runtime-infra-fs/src/main/kotlin/$packagePath")),
        "runtime-infra-fs must own moved filesystem implementation $packagePath",
      )
    }

    assertTrue(
      Files.isRegularFile(
        runtimeRoot.resolve(
          "runtime-application/src/main/kotlin/skillbill/workflow/implement/FeatureImplementWorkflowRuntime.kt",
        ),
      ),
      "workflow runtime-surface metadata must be owned outside runtime-core",
    )
  }

  @Test
  fun `moved filesystem implementation packages do not depend on forbidden adapters`() {
    val infraFsBuild = runtimeRoot.resolve("runtime-infra-fs/build.gradle.kts").readText()
    val forbiddenProjectDependencies = listOf(
      ":runtime-core",
      ":runtime-cli",
      ":runtime-mcp",
      ":runtime-desktop",
      ":runtime-infra-http",
      ":runtime-infra-sqlite",
    )
    val forbiddenDependencies = forbiddenProjectDependencies.filter { dependency ->
      infraFsBuild.contains("project(\"$dependency\")")
    }
    assertEquals(
      emptyList(),
      forbiddenDependencies,
      "runtime-infra-fs must not depend on runtime-core, runtime adapters, or sibling concrete infra adapters.",
    )

    val forbiddenPackages = forbiddenSourcePackages(
      listOf(
        "runtime-core/src/main/kotlin",
        "runtime-cli/src/main/kotlin",
        "runtime-mcp/src/main/kotlin",
        "runtime-desktop",
        "runtime-infra-http/src/main/kotlin",
        "runtime-infra-sqlite/src/main/kotlin",
      ),
    )
    val movedPackageRoots = listOf(
      "skillbill/install",
      "skillbill/scaffold",
      "skillbill/nativeagent",
      "skillbill/launcher",
      "skillbill/skillremove",
    ).map { packagePath -> runtimeRoot.resolve("runtime-infra-fs/src/main/kotlin/$packagePath") }

    val violations = movedPackageRoots
      .flatMap(::kotlinFilesUnder)
      .flatMap { sourceFile ->
        sourceFile.importsForbiddenBy(forbiddenPackages).map { forbiddenImport ->
          "${runtimeRoot.relativize(sourceFile)} imports $forbiddenImport"
        }
      }
      .sorted()

    assertEquals(
      emptyList(),
      violations,
      "Moved runtime-infra-fs implementation packages must use ports/domain/contracts instead of concrete " +
        "runtime-core, CLI, MCP, Desktop, HTTP, or SQLite adapter packages.",
    )
  }

  private fun forbiddenSourcePackages(moduleSourceRoots: List<String>): Set<String> = moduleSourceRoots
    .map { sourceRoot -> runtimeRoot.resolve(sourceRoot) }
    .flatMap(::kotlinFilesUnder)
    .mapNotNull { sourceFile -> packageName(sourceFile) }
    .filterNot { packageName -> packageName == "skillbill" }
    .toSet()

  private fun kotlinFilesUnder(root: Path): List<Path> {
    if (!Files.exists(root)) return emptyList()
    return Files.walk(root).use { paths ->
      paths
        .filter { path -> path.isRegularFile() && path.extension == "kt" }
        .toList()
    }
  }

  private fun packageName(sourceFile: Path): String? = sourceFile.readText().lineSequence()
    .firstOrNull { line -> line.startsWith("package ") }
    ?.removePrefix("package ")
    ?.trim()
    ?.takeIf(String::isNotBlank)

  private fun Path.importsForbiddenBy(forbiddenPackages: Set<String>): List<String> = readText()
    .lineSequence()
    .mapNotNull { line -> line.trim().removePrefix("import ").takeIf { line.trim().startsWith("import ") } }
    .filter { importedPackage ->
      forbiddenPackages.any { forbidden ->
        importedPackage == forbidden || importedPackage.startsWith("$forbidden.")
      }
    }
    .toList()
}
