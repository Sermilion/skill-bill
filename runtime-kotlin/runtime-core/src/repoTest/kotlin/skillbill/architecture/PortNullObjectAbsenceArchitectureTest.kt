package skillbill.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

object PortNullObjectCensus {
  private val declaration = Regex("""(?<!data )\bobject\s+((?:Unavailable|Noop|Empty|Unconfigured)\w*)""")

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
  fun `the census flags a substitute declaration and ignores a sealed data object case`() {
    assertEquals(
      setOf("NoopWorkflowGitOperations"),
      PortNullObjectCensus.namesIn("object NoopWorkflowGitOperations : WorkflowGitOperations"),
    )
    assertEquals(
      emptySet(),
      PortNullObjectCensus.namesIn("  data object Empty : ValidationGateTriageResult"),
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
        Regex("""(?m)^\s*(?:internal\s+)?object\s+((?:Unavailable|Noop|Empty|Unconfigured)[A-Za-z]\w*)\b""")
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
                    """^\s*(?:internal\s+)?object\s+${Regex.escape(name)}\b""",
                  ).containsMatchIn(line)
              }
            }
        }
        .map { (name, path) -> "$name (${runtimeRoot.relativize(path)})" }
        .sorted()

    assertEquals(emptyList(), missingReferences)
  }
}
