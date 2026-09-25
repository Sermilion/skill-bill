package skillbill.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

object PortNullObjectCensus {
  private val declaration =
    Regex(
      """(?<!(?:data|enum|sealed|value) )\b(?:object|class)\s+((?:Unavailable|Noop|Empty|Unconfigured)\w*)""",
    )

  fun namesIn(source: String): Set<String> = declaration.findAll(source).map { it.groupValues[1] }.toSet()
}

class PortNullObjectAbsenceArchitectureTest {
  private val runtimeRoot: Path = ArchitectureScanSupport.runtimeRoot

  @Test
  fun `no runtime module declares a null-object substitute in main source`() {
    val moduleMainRoots =
      RuntimeModuleCatalog.declaredGradleModules
        .filter { moduleName -> moduleName != "runtime-infra" }
        .map { moduleName -> moduleMainKotlinRoot(moduleName) }
    val infraMainRoots =
      RuntimeModuleCatalog.declaredGradleModules
        .filter { moduleName -> moduleName.startsWith("runtime-infra:") }
        .map { moduleName -> moduleMainKotlinRoot(moduleName) }
    assertEquals(
      7,
      infraMainRoots.size,
      "Every runtime-infra module must contribute a scanned main source root.",
    )
    val declarations =
      moduleMainRoots
        .flatMap { root ->
          val sourceFiles = kotlinFilesUnderWithArchitectureAsserts(root)
          sourceFiles.flatMap { path ->
            PortNullObjectCensus.namesIn(Files.readString(path)).map { "$it (${path.fileName})" }
          }
        }
        .sorted()

    assertEquals(
      emptyList(),
      declarations,
      "A Noop, Unavailable, Empty, or Unconfigured substitute reached production. Make the port " +
        "nullable at the reached call site and move the substitute into that module's testFixtures.",
    )
  }

  @Test
  fun `the census flags object and class substitutes and ignores data object and enum cases`() {
    assertEquals(
      setOf("NoopWorkflowGitOperations"),
      PortNullObjectCensus.namesIn("object NoopWorkflowGitOperations : WorkflowGitOperations"),
    )
    assertEquals(
      setOf("NoopWorkflowGitOperations"),
      PortNullObjectCensus.namesIn("class NoopWorkflowGitOperations : WorkflowGitOperations"),
    )
    assertEquals(
      emptySet(),
      PortNullObjectCensus.namesIn("  data object Empty : ValidationGateTriageResult"),
    )
    assertEquals(
      emptySet(),
      PortNullObjectCensus.namesIn("  enum class Unavailable : ReviewCheckpointFileIdentity"),
    )
  }

  @Test
  fun `every runtime-ports test fixture object has an outside test or fixture reference`() {
    val fixtureRoot =
      runtimeRoot.resolve(
        "${RuntimeModuleCatalog.runtimeKotlinModuleDirectory("runtime-ports")}/src/testFixtures/kotlin",
      )
    val fixtureFiles = kotlinFilesUnderWithArchitectureAsserts(fixtureRoot)
    val declarations =
      fixtureFiles.flatMap { path ->
        Regex(
          """(?m)^\s*(?:internal\s+)?(?:object|class)\s+""" +
            """((?:Unavailable|Noop|Empty|Unconfigured)[A-Za-z]\w*)\b""",
        )
          .findAll(Files.readString(path))
          .map { match -> match.groupValues[1] to path }
          .toList()
      }
    val runtimeKotlinRoot = runtimeRoot.resolve("runtime-kotlin")
    val referenceFiles =
      kotlinFilesUnderWithArchitectureAsserts(runtimeKotlinRoot)
        .filterNot { path -> path.fileName.toString() == "PortNullObjectClassification.kt" }

    val missingReferences =
      declarations
        .filter { (name, declarationPath) ->
          referenceFiles
            .none { path ->
              Files.readString(path).lineSequence().any { line ->
                Regex("""\b${Regex.escape(name)}\b""").containsMatchIn(line) &&
                  !Regex(
                    """^\s*(?:internal\s+)?(?:object|class)\s+${Regex.escape(name)}\b""",
                  ).containsMatchIn(line)
              }
            }
        }
        .map { (name, path) -> "$name (${runtimeRoot.relativize(path)})" }
        .sorted()

    assertEquals(emptyList(), missingReferences)
  }
}
