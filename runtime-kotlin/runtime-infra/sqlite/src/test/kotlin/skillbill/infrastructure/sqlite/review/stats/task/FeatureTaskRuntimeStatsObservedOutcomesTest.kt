package skillbill.infrastructure.sqlite.review.stats.task
import skillbill.infrastructure.sqlite.review.accounting.Map
import skillbill.infrastructure.sqlite.review.core.Map
import skillbill.infrastructure.sqlite.review.review.review
import skillbill.infrastructure.sqlite.review.stage.Map
import skillbill.infrastructure.sqlite.review.stage.and.review
import skillbill.infrastructure.sqlite.review.stage.finished.review
import skillbill.infrastructure.sqlite.review.stage.review
import skillbill.infrastructure.sqlite.review.stage.runtime.review
import skillbill.infrastructure.sqlite.review.stats.Map
import skillbill.infrastructure.sqlite.review.stats.completionStatus
import skillbill.infrastructure.sqlite.review.stats.health.Map
import skillbill.infrastructure.sqlite.review.stats.recorded.review
import skillbill.infrastructure.sqlite.review.stats.review
import skillbill.infrastructure.sqlite.review.stats.sessionId
import skillbill.infrastructure.sqlite.review.stats.workflow.STALE_COMPLETION_STATUS
import skillbill.infrastructure.sqlite.review.stats.workflow.buildFeatureTaskRuntimeStats
import kotlin.test.Test
import kotlin.test.assertEquals

class FeatureTaskRuntimeStatsObservedOutcomesTest {
  @Test
  fun `a reconciler-closed run is reported on its own and never depresses the completed rate`() {
    val stats = buildFeatureTaskRuntimeStats(
      listOf(
        finishedRow("ftr-1", "completed"),
        finishedRow("ftr-2", "completed"),
        finishedRow("ftr-3", STALE_COMPLETION_STATUS),
      ),
    )

    assertEquals(3, stats.finishedRuns)
    assertEquals(2, stats.observedRuns)
    assertEquals(1, stats.reconcilerClosedRuns)
    assertEquals(1.0, stats.completedRate, "both observed runs completed; the stale row is not evidence otherwise")
    assertEquals(0.0, stats.errorRate)
    assertEquals(1, stats.completionStatusCounts[STALE_COMPLETION_STATUS])
  }

  @Test
  fun `a run still in progress is neither observed nor reconciler-closed`() {
    val stats = buildFeatureTaskRuntimeStats(
      listOf(finishedRow("ftr-1", "blocked"), mapOf("session_id" to "ftr-2", "feature_size" to "SMALL")),
    )

    assertEquals(1, stats.inProgressRuns)
    assertEquals(1, stats.observedRuns)
    assertEquals(0, stats.reconcilerClosedRuns)
    assertEquals(1.0, stats.blockedRate)
  }

  private fun finishedRow(sessionId: String, completionStatus: String): Map<String, Any?> = mapOf(
    "session_id" to sessionId,
    "feature_size" to "MEDIUM",
    "finished_at" to "2026-09-15T10:00:00Z",
    "completion_status" to completionStatus,
    "completed_phase_ids" to """["implement"]""",
    "phase_outcomes" to """{"implement":"completed"}""",
  )
}
