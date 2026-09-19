package skillbill.architecture

import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatureTaskRuntimeBoundaryOwnershipArchitectureTest {
  @Test
  fun `phase token and settlement state stay behind named read-only boundaries`() {
    val runtimeEngine = ArchitectureScanSupport.runtimeRoot.resolve(
      "runtime-kotlin/runtime-engine/src/main/kotlin/skillbill/engine",
    )
    val production = ArchitectureScanSupport.kotlinFilesUnder(runtimeEngine)
      .joinToString("\n") { path -> path.readText() }

    assertFalse(production.contains("phaseTokenAccumulator"))
    assertEquals(1, Regex("""fun recordPhaseTokenUsage\(""").findAll(production).count())
    assertTrue(production.contains("fun recordPhaseTokenUsage("))
    assertTrue(production.contains("val phaseTokenView: Map<String, Pair<Int, Int>>"))
    val settlementState = runtimeEngine.resolve(
      "featuretask/runloop/state/FeatureTaskRuntimeRunStateValidation.kt",
    ).readText()
    assertFalse(Regex("""class ValidationSettlementState[\s\S]*?MutableSet<""").containsMatchIn(settlementState))
    assertFalse(
      Regex("""attempted:\s*MutableList<""").containsMatchIn(production),
    )
    assertFalse(
      Regex("""attempted:\s*GoalRunnerAttemptState""").containsMatchIn(production),
    )
    assertTrue(production.contains("recordAttempt: (Int) -> Unit"))
  }
}
