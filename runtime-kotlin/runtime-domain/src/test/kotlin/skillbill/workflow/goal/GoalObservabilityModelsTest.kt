package skillbill.workflow.goal

import skillbill.error.shellcontent.InvalidGoalObservabilityEventSchemaError
import skillbill.error.shellcontent.InvalidGoalProgressEventSchemaError
import skillbill.error.shellcontent.InvalidWorkflowStateSchemaError
import skillbill.goalrunner.model.GoalAttemptLedgerAction
import skillbill.workflow.goal.model.GOAL_OBSERVABILITY_HISTORY_LIMIT
import skillbill.workflow.goal.model.GoalObservabilityEvent
import skillbill.workflow.goal.model.GoalObservabilityHistory
import skillbill.workflow.goal.model.GoalObservabilityRecordKind
import skillbill.workflow.goal.model.GoalProgressEventKind
import skillbill.workflow.goal.model.GoalProgressOutcome
import skillbill.workflow.goal.model.goalObservabilityEventFromArtifact
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeWireArtifactKind
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class GoalObservabilityModelsTest {
  @Test
  fun `history append preserves sequence order and prunes oldest entries`() {
    val history =
      (1..(GOAL_OBSERVABILITY_HISTORY_LIMIT + 2))
        .fold(GoalObservabilityHistory()) { current, sequence ->
          current.append(event(sequence))
        }

    assertEquals(GOAL_OBSERVABILITY_HISTORY_LIMIT, history.events.size)
    assertEquals(3, history.events.first().sequenceNumber)
    assertEquals(GOAL_OBSERVABILITY_HISTORY_LIMIT + 2, history.events.last().sequenceNumber)
  }

  @Test
  fun `unknown goal progress event kind wire token fails typed workflow error`() {
    assertFailsWith<InvalidGoalProgressEventSchemaError> {
      GoalProgressEventKind.fromWire("not-an-event-kind")
    }
  }

  @Test
  fun `unknown goal observability and ledger tokens fail with typed workflow errors`() {
    assertFailsWith<InvalidGoalObservabilityEventSchemaError> {
      GoalObservabilityRecordKind.fromWire("not-a-record-kind")
    }
    assertFailsWith<InvalidGoalProgressEventSchemaError> {
      GoalProgressOutcome.fromWire("not-an-outcome")
    }
    assertFailsWith<InvalidWorkflowStateSchemaError> {
      GoalAttemptLedgerAction.fromWire("not-an-action")
    }
  }

  @Test
  fun `goal observability decoder rejects malformed list elements with a typed error`() {
    val malformed =
      event(1)
        .toArtifactMap(includeHeavyFields = true)
        .toMutableMap()
        .apply { this["changed_files"] = listOf(7) }

    assertFailsWith<InvalidGoalObservabilityEventSchemaError> {
      goalObservabilityEventFromArtifact(
        raw = malformed,
        sourceLabel = "goal_observability_latest_event",
        validator =
          object : GoalObservabilityEventValidator {
            override fun validate(
              kind: FeatureTaskRuntimeWireArtifactKind,
              payload: Any,
              sourceLabel: String,
            ) = Unit
          },
      )
    }
  }

  @Test
  fun `default artifact rendering omits optional heavy fields`() {
    val rendered = event(1, changedFiles = listOf("src/Main.kt")).toArtifactMap()

    assertFalse(rendered.containsKey("changed_files"))
    assertEquals("implement", rendered["workflow_phase"])
    assertEquals("durable_progress", rendered["liveness_class"])
  }

  @Test
  fun `goal observability timestamps decode to instants`() {
    val decoded =
      goalObservabilityEventFromArtifact(
        raw = event(1).toArtifactMap(),
        sourceLabel = "goal_observability_latest_event",
        validator =
          object : GoalObservabilityEventValidator {
            override fun validate(
              kind: FeatureTaskRuntimeWireArtifactKind,
              payload: Any,
              sourceLabel: String,
            ) = Unit
          },
      )

    assertEquals(Instant.parse("2026-06-01T00:00:00Z"), decoded.timestamp)
  }

  private fun event(
    sequence: Int,
    changedFiles: List<String> = emptyList(),
  ): GoalObservabilityEvent =
    GoalObservabilityEvent(
      issueKey = "SKILL-61",
      subtaskId = 1,
      workflowPhase = "implement",
      workerRole = "phase_subagent",
      livenessClass = "durable_progress",
      activitySummary = "working",
      timestamp = "2026-06-01T00:00:00Z",
      sequenceNumber = sequence,
      changedFiles = changedFiles,
    )
}
