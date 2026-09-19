package skillbill.infrastructure.sqlite.review.stats
import skillbill.contracts.telemetry.TelemetryMeasurementAvailability
import skillbill.infrastructure.sqlite.review.stats.workflow.buildGoalStats
import skillbill.infrastructure.sqlite.review.stats.workflow.goalIdentityAvailability
import kotlin.test.Test
import kotlin.test.assertEquals

class GoalStatsLogicalGoalGrainTest {
  @Test
  fun `three resume segments of one goal report one goal and three invocations`() {
    val stats = buildGoalStats(
      listOf(
        segment("parent-1:seg:1", parentWorkflowId = "parent-1", resumed = false, status = "blocked"),
        segment("parent-1:seg:2", parentWorkflowId = "parent-1", resumed = true, status = "blocked"),
        segment("parent-1:seg:3", parentWorkflowId = "parent-1", resumed = true, status = "completed"),
      ),
      emptyList(),
    )

    assertEquals(3, stats.totalRuns, "the store holds three invocation segments")
    assertEquals(1, stats.logicalGoals, "all three segments belong to one goal")
    assertEquals(2, stats.resumedInvocations)
    assertEquals(0, stats.invocationsWithUnknownGoal)
    assertEquals(TelemetryMeasurementAvailability.MEASURED.wireValue, stats.goalIdentityAvailability)
  }

  @Test
  fun `segments written before the parent id was persisted are counted as unattributed, not as one goal`() {
    val stats = buildGoalStats(
      listOf(
        segment("legacy-1", parentWorkflowId = null, resumed = false, status = "completed"),
        segment("legacy-2", parentWorkflowId = null, resumed = true, status = "completed"),
        segment("parent-9:seg:1", parentWorkflowId = "parent-9", resumed = false, status = "completed"),
      ),
      emptyList(),
    )

    assertEquals(1, stats.logicalGoals, "only the attributable segment names a goal")
    assertEquals(2, stats.invocationsWithUnknownGoal)
    assertEquals(
      TelemetryMeasurementAvailability.UNAVAILABLE_INCOMPLETE.wireValue,
      stats.goalIdentityAvailability,
      "a partially attributable store must not read as a measured goal count",
    )
  }

  private fun segment(
    workflowId: String,
    parentWorkflowId: String?,
    resumed: Boolean,
    status: String,
  ): Map<String, Any?> = mapOf(
    "workflow_id" to workflowId,
    "issue_key" to "SKILL-236",
    "feature_name" to "telemetry truth",
    "subtask_total" to 2,
    "resumed" to if (resumed) 1 else 0,
    "started_at" to "2026-09-15T10:00:00Z",
    "status" to status,
    "finished_at" to "2026-09-15T11:00:00Z",
    "finished_duration_ms" to 3_600_000L,
    "subtasks_complete" to 2,
    "subtasks_blocked" to 0,
    "subtasks_skipped" to 0,
    "mode" to "runtime",
    "parent_workflow_id" to parentWorkflowId,
  )
}
