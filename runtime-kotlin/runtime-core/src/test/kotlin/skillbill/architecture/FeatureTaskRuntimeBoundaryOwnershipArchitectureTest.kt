package skillbill.architecture

import kotlin.io.path.readText
import kotlin.test.Test
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
    assertTrue(production.contains("fun recordPhaseTokenUsage("))
    assertTrue(production.contains("val phaseTokenView: Map<String, Pair<Int, Int>>"))
    assertFalse(
      Regex("""class ValidationSettlementState[\s\S]*?MutableSet<""").containsMatchIn(production),
    )
    assertFalse(
      Regex("""attempted:\s*MutableList<""").containsMatchIn(production),
    )
  }
}
