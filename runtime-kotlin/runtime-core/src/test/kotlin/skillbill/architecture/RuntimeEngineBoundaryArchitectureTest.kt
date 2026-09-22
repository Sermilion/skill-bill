package skillbill.architecture

import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuntimeDiagnosticsBestEffortGuardArchitectureTest {
  @Test
  fun `engine production sources delegate diagnostics warning guards to the single owner`() {
    val engineRoot =
      ArchitectureScanSupport.runtimeRoot.resolve(
        "runtime-kotlin/runtime-engine/src/main/kotlin",
      )
    val guardPattern =
      Regex(
        """(?:runCatching|onFailure|catch\s*\([^)]*\))[\s\S]{0,300}diagnostics\.warning""",
      )
    val owner =
      engineRoot.resolve(
        "skillbill/engine/diagnostics/RuntimeDiagnosticsBestEffortWarning.kt",
      )
    val violations =
      ArchitectureScanSupport.kotlinFilesUnder(engineRoot)
        .filter { path -> path != owner && guardPattern.containsMatchIn(path.readText()) }
        .map { path -> engineRoot.relativize(path).toString() }
    assertEquals(emptyList(), violations, violations.joinToString("\n"))
  }
}

class GoalRunnerAgentOutputScannerArchitectureTest {
  @Test
  fun `goal runner engine sources do not declare a brace scanner`() {
    val engineGoalRunnerRoot =
      ArchitectureScanSupport.runtimeRoot.resolve(
        "runtime-kotlin/runtime-engine/src/main/kotlin/skillbill/engine/goalrunner",
      )
    val violations =
      ArchitectureScanSupport.kotlinFilesUnder(engineGoalRunnerRoot).filter { path ->
        path.readText().contains("fun topLevelJsonObjectCandidates")
      }.map { path -> engineGoalRunnerRoot.relativize(path).toString() }
    assertEquals(emptyList(), violations)
  }

  @Test
  fun `adapter-owned agent output scanner remains the single production scanner`() {
    val roots =
      listOf(
        ArchitectureScanSupport.runtimeRoot.resolve("runtime-kotlin/runtime-engine/src/main/kotlin"),
        ArchitectureScanSupport.runtimeRoot.resolve("runtime-kotlin/runtime-application/src/main/kotlin"),
      )
    val matches =
      roots.flatMap { root ->
        ArchitectureScanSupport.kotlinFilesUnder(root).filter { path ->
          path.readText().contains("fun topLevelJsonObjectCandidates")
        }.map { path -> root.relativize(path).toString() }
      }
    assertEquals(
      listOf("skillbill/application/agentoutput/AgentOutputJsonScan.kt"),
      matches.sorted(),
    )
  }
}

class RuntimeApplicationSharedEngineEdgeArchitectureTest {
  @Test
  fun `application main references only pinned engine inbound api types`() {
    val violations =
      engineInboundApiViolations(
        consumerSourceRoots = listOf("runtime-application/src/main/kotlin"),
        allowedTypes = RuntimeEngineInboundApiTest.PINNED_ENGINE_INBOUND_API_TYPES,
      )
    assertEquals(emptyList(), violations, violations.joinToString("\n"))
  }
}

class RuntimeEnginePublicTopLevelDeclarationArchitectureTest {
  @Test
  fun `new public top-level engine declarations stay within inbound api and model packages`() {
    val engineMain =
      ArchitectureScanSupport.runtimeRoot.resolve(
        "runtime-kotlin/runtime-engine/src/main/kotlin",
      )
    val modelPackagePrefixes =
      setOf(
        "skillbill.engine.featuretask.model",
        "skillbill.engine.goalrunner.model",
        "skillbill.engine.goalrunner.planning.model",
        "skillbill.engine.work.model",
      )
    val violations =
      ArchitectureScanSupport.kotlinFilesUnder(engineMain).flatMap { path ->
        val source = path.readText()
        val packageName = ArchitectureScanSupport.declaredPackage(source).orEmpty()
        if (modelPackagePrefixes.any { prefix -> packageName == prefix || packageName.startsWith("$prefix.") }) {
          emptyList()
        } else {
          topLevelPublicDeclarations(
            packageName = packageName,
            source = source,
            allowedTypes = RuntimeEngineInboundApiTest.PINNED_ENGINE_INBOUND_API_TYPES,
            includeDefaultPublic = false,
          ).map { type ->
            "${engineMain.relativize(path)}: $type is outside the pinned inbound API"
          }
        }
      }
    assertTrue(violations.isEmpty(), violations.joinToString("\n"))
  }

  @Test
  fun `visibility census detects Kotlin default-public declarations outside the pinned api`() {
    val source =
      """
      package skillbill.engine.goalrunner

      class NewEngineLeak
      internal class AllowedImplementation
      """.trimIndent()

    assertEquals(
      listOf("skillbill.engine.goalrunner.NewEngineLeak"),
      topLevelPublicDeclarations(
        packageName = "skillbill.engine.goalrunner",
        source = source,
        allowedTypes = RuntimeEngineInboundApiTest.PINNED_ENGINE_INBOUND_API_TYPES,
        includeDefaultPublic = true,
      ),
    )
  }

  @Test
  fun `visibility census allows a pinned api declaration without allowing its package`() {
    val source =
      """
      package skillbill.engine.goalrunner

      class GoalRunner
      class UnpinnedEngineLeak
      """.trimIndent()

    assertEquals(
      listOf("skillbill.engine.goalrunner.UnpinnedEngineLeak"),
      topLevelPublicDeclarations(
        packageName = "skillbill.engine.goalrunner",
        source = source,
        allowedTypes = RuntimeEngineInboundApiTest.PINNED_ENGINE_INBOUND_API_TYPES,
        includeDefaultPublic = true,
      ),
    )
  }

  private fun topLevelPublicDeclarations(
    packageName: String,
    source: String,
    allowedTypes: Set<String>,
    includeDefaultPublic: Boolean,
  ): List<String> {
    var braceDepth = 0
    val declarations = mutableListOf<String>()
    source.lineSequence().forEach { line ->
      if (braceDepth == 0) {
        TOP_LEVEL_DECLARATION.find(line)?.let { match ->
          val explicitVisibility = match.groupValues[1]
          val name = match.groupValues[2]
          val type = "$packageName.$name"
          val hasPublicVisibility =
            explicitVisibility == "public" ||
              includeDefaultPublic && explicitVisibility.isBlank()
          if (hasPublicVisibility && type !in allowedTypes) {
            declarations += type
          }
        }
      }
      braceDepth += line.count { character -> character == '{' }
      braceDepth -= line.count { character -> character == '}' }
    }
    return declarations
  }

  private companion object {
    val TOP_LEVEL_DECLARATION =
      Regex(
        """^\s*(?:(public|internal|private|protected)\s+)?""" +
          """(?:(?:abstract|sealed|data|enum|value|open|final|inline|suspend|""" +
          """operator|infix|tailrec|const|expect|actual|fun)\s+)*""" +
          """(?:class|object|interface|typealias|fun|val|var)\s+""" +
          """([A-Za-z_][A-Za-z0-9_]*)\b""",
      )
  }
}
