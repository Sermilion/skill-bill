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
      kotlinFilesUnderWithArchitectureAsserts(engineRoot)
        .filter { path -> path != owner && guardPattern.containsMatchIn(path.readText()) }
        .map { path -> engineRoot.relativize(path).toString() }
    assertEquals(emptyList(), violations, violations.joinToString("\n"))
  }
}

class GoalRunnerAgentOutputScannerArchitectureTest {
  @Test
  fun `adapter-owned agent output scanner remains the single production scanner`() {
    val roots =
      listOf(
        ArchitectureScanSupport.runtimeRoot.resolve("runtime-kotlin/runtime-engine/src/main/kotlin"),
        ArchitectureScanSupport.runtimeRoot.resolve("runtime-kotlin/runtime-application/src/main/kotlin"),
      )
    val matches =
      roots.flatMap { root ->
        kotlinFilesUnderWithArchitectureAsserts(root).filter { path ->
          path.readText().contains("fun topLevelJsonObjectCandidates")
        }.map { path -> root.relativize(path).toString() }
      }
    assertEquals(
      listOf("skillbill/engine/agentoutput/AgentOutputJsonScan.kt"),
      matches.sorted(),
    )
  }
}

class RuntimeApplicationSharedEngineEdgeArchitectureTest {
  @Test
  fun `application main references only pinned engine inbound api types`() {
    val violations =
      engineInboundApiViolations(
        consumerSourceRoots = listOf(moduleMainKotlinRootRelative("runtime-application")),
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
      kotlinFilesUnderWithArchitectureAsserts(engineMain).flatMap { path ->
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

  @Test
  fun `feature-task run-loop step declarations reference each other acyclically`() {
    val cycles = ArchitectureScanSupport.cyclicComponents(runLoopStepEdges(runLoopSources()))
    assertTrue(
      cycles.isEmpty(),
      cycles.joinToString("\n") { cycle -> "run-loop step cycle: ${cycle.joinToString(" -> ")}" },
    )
  }

  @Test
  fun `run-loop phase blocking leaf references no other run-loop step declaration`() {
    val edges = runLoopStepEdges(runLoopSources())
    assertEquals(
      emptySet(),
      edges.getValue(PHASE_BLOCKING_STEP),
      "$PHASE_BLOCKING_STEP must stay a leaf and reference only run-loop carriers.",
    )
  }

  @Test
  fun `run-loop step census reports a mutually referencing pair as one cycle naming both`() {
    val sources =
      mapOf(
        "core/FeatureTaskRuntimeRunLoopAlpha.kt" to
          """
          package skillbill.engine.featuretask.runloop.core

          object FeatureTaskRuntimeRunLoopAlpha {
            internal fun settle(): String = FeatureTaskRuntimeRunLoopBeta.settle()
          }
          """.trimIndent(),
        "core/FeatureTaskRuntimeRunLoopBeta.kt" to
          """
          package skillbill.engine.featuretask.runloop.core

          object FeatureTaskRuntimeRunLoopBeta {
            internal fun settle(): String = FeatureTaskRuntimeRunLoopAlpha.settle()
          }
          """.trimIndent(),
      )
    assertEquals(
      listOf(listOf("FeatureTaskRuntimeRunLoopAlpha", "FeatureTaskRuntimeRunLoopBeta")),
      ArchitectureScanSupport.cyclicComponents(runLoopStepEdges(sources)),
    )
  }

  @Test
  fun `run-loop top-level declarations call no run-loop step declaration`() {
    val calls = runLoopTopLevelStepCalls(runLoopSources())
    assertEquals(
      emptyList(),
      calls,
      "Top-level run-loop declarations are invisible to the step graph; move these into a step:\n" +
        calls.joinToString("\n"),
    )
  }

  @Test
  fun `run-loop top-level census reports helpers that hide a step edge`() {
    val sources =
      mapOf(
        "core/FeatureTaskRuntimeRunLoopAlpha.kt" to
          """
          package skillbill.engine.featuretask.runloop.core

          object FeatureTaskRuntimeRunLoopAlpha {
            internal fun settle(): String = relay()
          }
          """.trimIndent(),
        "core/FeatureTaskRuntimeRunLoopRelay.kt" to
          """
          package skillbill.engine.featuretask.runloop.core

          internal fun relay(): String =
            FeatureTaskRuntimeRunLoopBeta.settle()

          internal class RelayHolder {
            internal fun label(): String = "${'$'}{FeatureTaskRuntimeRunLoopGamma.settle()}"
          }
          """.trimIndent(),
        "core/FeatureTaskRuntimeRunLoopBeta.kt" to
          """
          package skillbill.engine.featuretask.runloop.core

          object FeatureTaskRuntimeRunLoopBeta {
            internal fun settle(): String = FeatureTaskRuntimeRunLoopAlpha.settle()
          }
          """.trimIndent(),
        "core/FeatureTaskRuntimeRunLoopGamma.kt" to
          """
          package skillbill.engine.featuretask.runloop.core

          @Suppress("unused") internal sealed class FeatureTaskRuntimeRunLoopGamma {
            internal fun settle(): String = "gamma"
          }
          """.trimIndent(),
      )
    assertEquals(
      listOf(
        "core/FeatureTaskRuntimeRunLoopRelay.kt: RelayHolder -> FeatureTaskRuntimeRunLoopGamma",
        "core/FeatureTaskRuntimeRunLoopRelay.kt: relay -> FeatureTaskRuntimeRunLoopBeta",
      ),
      runLoopTopLevelStepCalls(sources),
    )
  }

  private fun runLoopTopLevelStepCalls(sources: Map<String, String>): List<String> {
    val segmentsByPath =
      sources.mapValues { (_, source) -> runLoopStepSegments(strippedRunLoopSource(source)) }
    val stepNames = segmentsByPath.values.flatMap { (_, bodies) -> bodies.keys }.toSet() - RUN_LOOP_CARRIERS
    return segmentsByPath.flatMap { (path, segments) ->
      topLevelDeclarationSegments(segments.first).flatMap { (declaration, body) ->
        stepNames
          .filter { step -> Regex("""\b${Regex.escape(step)}\b(?!\s*\.\s*[A-Z])""").containsMatchIn(body) }
          .map { step -> "$path: $declaration -> $step" }
      }
    }.sorted()
  }

  private fun topLevelDeclarationSegments(fileScope: String): List<Pair<String, String>> {
    var braceDepth = 0
    var current: Pair<String, StringBuilder>? = null
    val segments = mutableListOf<Pair<String, StringBuilder>>()
    fileScope.lineSequence().forEach { line ->
      if (braceDepth == 0 && line.isNotBlank() && !line.first().isWhitespace() && line.first() !in ")]}") {
        current =
          topLevelDeclarationName(line)?.let { name ->
            (name to StringBuilder()).also(segments::add)
          }
      }
      current?.second?.appendLine(line)
      braceDepth += line.count { character -> character == '{' } - line.count { character -> character == '}' }
    }
    return segments.map { (declaration, body) -> declaration to body.toString() }
  }

  private fun topLevelDeclarationName(line: String): String? =
    TOP_LEVEL_FUNCTION.find(line)?.groupValues?.get(1)
      ?: TOP_LEVEL_TYPE.find(line)?.groupValues?.get(1)
      ?: TOP_LEVEL_PROPERTY.find(line)?.groupValues?.get(1)

  private fun runLoopSources(): Map<String, String> {
    val runLoopRoot =
      ArchitectureScanSupport.runtimeRoot.resolve(
        "runtime-kotlin/runtime-engine/src/main/kotlin/skillbill/engine/featuretask/runloop",
      )
    return kotlinFilesUnderWithArchitectureAsserts(runLoopRoot)
      .associate { path -> runLoopRoot.relativize(path).toString() to path.readText() }
  }

  private fun strippedRunLoopSource(source: String): String =
    CommentStripper(
      source,
      blankStringLiterals = true,
      preserveTemplateExpressions = true,
    ).strip()

  private fun runLoopStepEdges(sources: Map<String, String>): Map<String, Set<String>> {
    val segmentsByPath =
      sources.mapValues { (_, source) -> runLoopStepSegments(strippedRunLoopSource(source)) }
    val stepNames = segmentsByPath.values.flatMap { (_, bodies) -> bodies.keys }.toSet() - RUN_LOOP_CARRIERS
    return buildMap {
      segmentsByPath.values.forEach { (fileScope, bodies) ->
        bodies.filterKeys { name -> name in stepNames }.forEach { (name, body) ->
          put(
            name,
            stepNames.filterTo(mutableSetOf()) { candidate ->
              candidate != name && referencesDeclaration(candidate, fileScope + body)
            },
          )
        }
      }
    }
  }

  private fun runLoopStepSegments(source: String): Pair<String, Map<String, String>> {
    var braceDepth = 0
    var parenDepth = 0
    var current: String? = null
    var entered = false
    val bodies = linkedMapOf<String, StringBuilder>()
    val fileScope = StringBuilder()
    source.lineSequence().forEach { line ->
      if (current == null && braceDepth == 0) {
        STEP_DECLARATION.find(line)?.groupValues?.get(1)?.let { name ->
          current = name
          entered = false
          parenDepth = 0
          bodies[name] = StringBuilder()
        }
      }
      current?.let { name -> bodies.getValue(name).appendLine(line) } ?: fileScope.appendLine(line)
      braceDepth += line.count { character -> character == '{' }
      braceDepth -= line.count { character -> character == '}' }
      parenDepth += line.count { character -> character == '(' }
      parenDepth -= line.count { character -> character == ')' }
      if (braceDepth > 0) entered = true
      // A body-less declaration (`data class Foo(...)`) never opens a brace, so it ends once its
      // constructor parentheses close; without this it would swallow every later declaration.
      if (braceDepth <= 0 && (entered || parenDepth <= 0)) {
        current = null
        entered = false
      }
    }
    return fileScope.toString() to bodies.mapValues { (_, body) -> body.toString() }
  }

  private fun referencesDeclaration(
    name: String,
    text: String,
  ): Boolean = Regex("""\b${Regex.escape(name)}\b""").containsMatchIn(text)

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
    const val PHASE_BLOCKING_STEP = "FeatureTaskRuntimeRunLoopPhaseBlocking"

    val RUN_LOOP_CARRIERS = setOf("FeatureTaskRuntimeRunLoopSession", "FeatureTaskRuntimeRunLoopContext")

    val DECLARATION_MODIFIERS =
      """(?:(?:internal|private|public|protected|abstract|sealed|data|value|enum|annotation|open|""" +
        """final|inline|suspend|operator|infix|tailrec|const|expect|actual|lateinit)\s+)*"""

    val STEP_DECLARATION =
      Regex(
        """^(?:@[A-Za-z_][A-Za-z0-9_.]*(?:\([^)]*\))?\s+)*$DECLARATION_MODIFIERS""" +
          """(?:object|class|interface)\s+(FeatureTaskRuntimeRunLoop[A-Za-z0-9_]*)\b""",
      )

    val TOP_LEVEL_TYPE =
      Regex(
        """^(?:@[A-Za-z_][A-Za-z0-9_.]*(?:\([^)]*\))?\s+)*$DECLARATION_MODIFIERS""" +
          """(?:object|class|interface|typealias)\s+([A-Za-z_][A-Za-z0-9_]*)\b""",
      )

    val TOP_LEVEL_PROPERTY =
      Regex(
        """^(?:@[A-Za-z_][A-Za-z0-9_.]*(?:\([^)]*\))?\s+)*$DECLARATION_MODIFIERS""" +
          """(?:val|var)\s+(?:<[^>]*>\s*)?(?:[A-Za-z0-9_.<>?, ]+\.)?([A-Za-z_][A-Za-z0-9_]*)\b""",
      )

    val TOP_LEVEL_FUNCTION =
      Regex(
        """^(?:(?:internal|private|public|inline|suspend|operator|infix)\s+)*fun\s+""" +
          """(?:<[^>]*>\s*)?(?:[A-Za-z0-9_.<>?, ]+\.)?([A-Za-z_][A-Za-z0-9_]*)\s*\(""",
      )

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
