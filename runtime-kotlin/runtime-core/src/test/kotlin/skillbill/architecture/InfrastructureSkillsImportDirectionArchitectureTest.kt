package skillbill.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class InfrastructureSkillsImportDirectionArchitectureTest {
  @Test
  fun `skills production sources respect the agentaddon to module-root package order`() {
    val violations = skillsImportDirectionViolations()
    assertEquals(emptyList(), violations, violations.joinToString("\n"))
  }

  private fun skillsImportDirectionViolations(): List<String> {
    val skillsRoot = moduleMainKotlinRoot("runtime-infra:skills")
    val sourceFiles = kotlinFilesUnderWithArchitectureAsserts(skillsRoot)

    val violations = mutableListOf<String>()
    sourceFiles.forEach { file ->
        val text = Files.readString(file)
        val sourcePackage = readPackageName(text, file)
        val sourceLayer = skillsLayerIndex(sourcePackage)
        readSkillImports(text).forEach { imported ->
          val targetLayer = skillsLayerIndex(imported)
          if (targetLayer > sourceLayer) {
            violations +=
              "${ArchitectureScanSupport.runtimeRoot.relativize(file)} imports $imported " +
              "(layer $targetLayer) from layer $sourceLayer"
          }
        }
    }
    return violations.sorted()
  }

  private fun skillsLayerIndex(packageName: String): Int {
    if (!packageName.startsWith("skillbill.infrastructure.skills")) return -1
    val orderedPrefixes =
      listOf(
        "skillbill.infrastructure.skills.agentaddon",
        "skillbill.infrastructure.skills.nativeagent",
        "skillbill.infrastructure.skills.scaffold",
        "skillbill.infrastructure.skills.install",
        "skillbill.infrastructure.skills.skillremove",
        "skillbill.infrastructure.skills",
      )
    return orderedPrefixes.indexOfLast { prefix ->
      packageName == prefix || packageName.startsWith("$prefix.")
    }
  }

  private fun readPackageName(
    text: String,
    file: Path,
  ): String =
    text.lineSequence().firstOrNull { it.startsWith("package ") }
      ?.removePrefix("package ")
      ?.trimEnd(';')
      ?.trim()
      ?: error("Missing package in $file")

  private fun readSkillImports(text: String): List<String> =
    text.lineSequence()
      .map { it.trim() }
      .filter { it.startsWith("import ") && !it.startsWith("import typealias ") }
      .map { it.removePrefix("import ").trimEnd(';').trim() }
      .filter { it.startsWith("skillbill.infrastructure.skills") }
      .toList()
}
