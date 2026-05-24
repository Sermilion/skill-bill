package skillbill.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InstallPolicyOwnershipArchitectureTest {
  private val runtimeRoot: Path =
    Path.of("").toAbsolutePath().normalize().let { workingDir ->
      if (workingDir.fileName.toString().startsWith("runtime-")) {
        workingDir.parent
      } else {
        workingDir
      }
    }

  @Test
  fun `install policy package must not import filesystem or install implementation mechanics`() {
    val policyRoot = runtimeRoot.resolve("runtime-domain/src/main/kotlin/skillbill/install/policy")
    val policyFiles = kotlinFilesUnder(policyRoot)
    assertTrue(policyFiles.isNotEmpty(), "Install policy package must exist in runtime-domain.")

    val forbiddenImportPattern = installPolicyForbiddenImportPattern()
    val violations = policyFiles.flatMap { sourceFile ->
      sourceFile.readText().lineSequence()
        .mapIndexedNotNull { index, line ->
          val trimmed = line.trim()
          if (forbiddenImportPattern.matches(trimmed)) {
            "${runtimeRoot.relativize(sourceFile)}:${index + 1} imports ${trimmed.removePrefix("import ")}"
          } else {
            null
          }
        }
    }

    assertEquals(
      emptyList(),
      violations,
      "Install plan policy must stay pure: filesystem/process mechanics and infra-fs install implementation " +
        "imports belong in runtime-infra-fs.",
    )
  }

  @Test
  fun `install policy forbidden import regex catches known bad and passes known good`() {
    val forbiddenImportPattern = installPolicyForbiddenImportPattern()
    val mustBeDetectedAsForbidden = listOf(
      "import java.io.File",
      "import java.nio.file.Files",
      "import java.lang.ProcessBuilder",
      "import skillbill.infrastructure.fs.FileSystemInstallPlanningFacts",
      "import skillbill.install.InstallOperations",
      "import skillbill.install.InstallPlanBuilder",
      "import skillbill.install.computeInstallContentHash",
    )
    val mustNotBeDetectedAsForbidden = listOf(
      "import java.nio.file.Path",
      "import skillbill.install.model.InstallPlan",
      "import skillbill.install.policy.InstallPlanPolicy",
      "import skillbill.contracts.install.InstallPlanSchemaValidator",
    )

    val falseNegatives = mustBeDetectedAsForbidden.filterNot(forbiddenImportPattern::matches)
    val falsePositives = mustNotBeDetectedAsForbidden.filter(forbiddenImportPattern::matches)

    assertEquals(emptyList(), falseNegatives, "Install-policy forbidden-import regex missed known-bad imports.")
    assertEquals(emptyList(), falsePositives, "Install-policy forbidden-import regex flagged known-good imports.")
  }

  private fun installPolicyForbiddenImportPattern(): Regex = Regex(
    """^import\s+(""" +
      """java\.io\.File|java\.nio\.file\.Files|java\.lang\.ProcessBuilder|""" +
      """skillbill\.infrastructure(?:\..*)?|""" +
      """skillbill\.install\.(?!model\.|policy\.)[A-Za-z0-9_.*]+""" +
      """)$""",
  )

  private fun kotlinFilesUnder(root: Path): List<Path> {
    if (!Files.exists(root)) return emptyList()
    return Files.walk(root).use { paths ->
      paths
        .filter { path -> path.isRegularFile() && path.extension == "kt" }
        .toList()
    }
  }
}
