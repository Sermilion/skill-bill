package skillbill.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.test.Test
import kotlin.test.assertEquals

object PortNullObjectCensus {
  private val declaration = Regex("""(?<!data )\bobject\s+((?:Unavailable|Noop|Empty|Unconfigured)\w*)""")

  fun namesIn(source: String): Set<String> = declaration.findAll(source).map { it.groupValues[1] }.toSet()
}

class PortNullObjectAbsenceArchitectureTest {
  private val runtimeRoot: Path =
    Path.of("").toAbsolutePath().normalize().let { workingDir ->
      if (workingDir.fileName.toString().startsWith("runtime-")) workingDir.parent else workingDir
    }

  @Test
  fun `no runtime module declares a null-object substitute in main source`() {
    val declarations = RuntimeModuleCatalog.declaredGradleModules
      .map { runtimeRoot.resolve("$it/src/main") }
      .filter { Files.isDirectory(it) }
      .flatMap { root ->
        Files.walk(root).use { paths ->
          paths
            .filter { Files.isRegularFile(it) && it.extension == "kt" }
            .toList()
        }
      }
      .flatMap { path -> PortNullObjectCensus.namesIn(Files.readString(path)).map { "$it (${path.fileName})" } }
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
    val fixtureRoot = runtimeRoot.resolve("runtime-kotlin/runtime-ports/src/testFixtures")
    val fixtureFiles = kotlinFiles(fixtureRoot)
    val declarations = fixtureFiles.flatMap { path ->
      Regex("""(?m)^\s*(?:internal\s+)?object\s+([A-Za-z]\w*)\b""")
        .findAll(Files.readString(path))
        .map { match -> match.groupValues[1] to path }
        .toList()
    }
    val referenceFiles = kotlinFiles(runtimeRoot.resolve("runtime-kotlin"))
      .filterNot { path -> path.fileName.toString() == "PortNullObjectClassification.kt" }

    val missingReferences = declarations
      .filter { (name, declarationPath) ->
        referenceFiles
          .filterNot { path -> path == declarationPath }
          .none { path -> Regex("""\b${Regex.escape(name)}\b""").containsMatchIn(Files.readString(path)) }
      }
      .map { (name, path) -> "$name (${runtimeRoot.relativize(path)})" }
      .sorted()

    assertEquals(emptyList(), missingReferences)
    assertEquals(
      emptySet(),
      PortNullObjectClassification.classifiedObjects.keys.intersect(
        setOf("StubGovernedReviewEvidenceEndpointBinder", "CheckpointHistoryGitOperationsRefusalTest"),
      ),
    )
  }

  private fun kotlinFiles(root: Path): List<Path> {
    if (!Files.isDirectory(root)) return emptyList()
    return Files.walk(root).use { paths ->
      paths
        .filter { path -> Files.isRegularFile(path) && path.extension == "kt" }
        .toList()
    }
  }
}
