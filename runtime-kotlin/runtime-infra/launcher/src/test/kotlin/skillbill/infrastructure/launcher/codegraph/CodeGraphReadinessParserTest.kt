package skillbill.infrastructure.launcher.codegraph

import skillbill.contracts.codegraph.CodeGraphDegradationReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CodeGraphReadinessParserTest {
  @Test
  fun `watcher degradation indexing and pending footer are never fresh evidence`() {
    listOf(
      "file.kt (indexing in progress)",
      "Some files have codegraph entries may be stale",
      "2 files elsewhere are pending index sync",
      "CodeGraph auto-sync is DISABLED; the index is frozen",
    ).forEach { banner ->
      val degraded =
        assertIs<CodeGraphReadiness.Degraded>(CodeGraphReadinessParser.parse(0, "Nodes: 2\n$banner", "", true))
      assertEquals(CodeGraphDegradationReason.PENDING_SYNCHRONIZATION, degraded.reason)
    }
  }

  @Test
  fun `exit zero without statistics or with missing index is not ready`() {
    assertEquals(
      CodeGraphDegradationReason.UNAVAILABLE_CAPABILITY,
      assertIs<CodeGraphReadiness.Degraded>(CodeGraphReadinessParser.parse(0, "", "", true)).reason,
    )
    assertEquals(
      CodeGraphDegradationReason.MISSING_GRAPH,
      assertIs<CodeGraphReadiness.Degraded>(CodeGraphReadinessParser.parse(0, "No index", "", true)).reason,
    )
  }

  @Test
  fun `pending synchronization is not reported as ready`() {
    val readiness = CodeGraphReadinessParser.parse(
      exitCode = 0,
      stdout = "Nodes: 10\n### Pending sync: src/Main.kt",
      stderr = "",
      graphPresent = true,
    )

    val degraded = assertIs<CodeGraphReadiness.Degraded>(readiness)
    assertEquals(CodeGraphDegradationReason.PENDING_SYNCHRONIZATION, degraded.reason)
  }

  @Test
  fun `healthy status with a local graph is ready`() {
    val readiness = CodeGraphReadinessParser.parse(
      exitCode = 0,
      stdout = "Nodes: 10\nEdges: 9\nFiles: 4\nJournal: wal",
      stderr = "",
      graphPresent = true,
    )

    assertIs<CodeGraphReadiness.Ready>(readiness)
  }
}
