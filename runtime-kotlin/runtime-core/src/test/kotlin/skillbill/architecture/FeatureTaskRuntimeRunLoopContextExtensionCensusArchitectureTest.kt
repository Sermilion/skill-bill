package skillbill.architecture

import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals

class FeatureTaskRuntimeRunLoopContextExtensionCensusArchitectureTest {
  @Test
  fun `run loop context extension census does not grow`() {
    val actual = actualCensus()
    val documented = documentedCensus()

    assertEquals(documented.keys, actual.keys)
    assertEquals(documented.mapValues { (_, counts) -> counts.target }, actual)
    assertEquals(
      documented.mapValues { (_, counts) -> counts.target },
      documented.mapValues { (_, counts) -> counts.current },
      "The documented current census must equal its retained target.",
    )
    assertEquals(
      27,
      actual.values.sum(),
      "The run-loop context extension surface must not grow beyond the retained target.",
    )
    assertEquals(
      documented.values.sumOf { counts -> counts.target },
      27,
      "The retained orchestration target must remain explicit in ARCHITECTURE.md.",
    )
  }

  private fun actualCensus(): Map<String, Int> {
    val runLoopRoot = ArchitectureScanSupport.runtimeRoot.resolve(
      "runtime-kotlin/runtime-engine/src/main/kotlin/skillbill/engine/featuretask",
    )
    return ArchitectureScanSupport.kotlinFilesUnder(runLoopRoot)
      .filter { path -> path.name.startsWith("FeatureTaskRuntimeRunLoop") }
      .associate { path ->
        path.name to CONTEXT_EXTENSION.findAll(path.readText()).count()
      }
  }

  private fun documentedCensus(): Map<String, CensusCounts> {
    val architecture = ArchitectureScanSupport.runtimeRoot
      .resolve("runtime-kotlin/ARCHITECTURE.md")
      .readText()
    return DOCUMENTED_ROW.findAll(architecture).associate { match ->
      match.groupValues[1] to CensusCounts(
        current = match.groupValues[2].toInt(),
        target = match.groupValues[3].toInt(),
      )
    }
  }

  private data class CensusCounts(val current: Int, val target: Int)

  private companion object {
    val CONTEXT_EXTENSION = Regex("""\bfun\s+FeatureTaskRuntimeRunLoopContext\.""")
    val DOCUMENTED_ROW = Regex("""(?m)^\| `([^`]+\.kt)` \| (\d+) \| (\d+) \|$""")
  }
}
