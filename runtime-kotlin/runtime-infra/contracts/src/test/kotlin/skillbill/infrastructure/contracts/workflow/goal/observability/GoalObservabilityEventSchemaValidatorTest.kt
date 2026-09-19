package skillbill.infrastructure.contracts.workflow.goal.observability
import skillbill.contracts.workflow.goal.GOAL_OBSERVABILITY_EVENT_CONTRACT_VERSION
import skillbill.error.shellcontent.InvalidGoalObservabilityEventSchemaError
import skillbill.infrastructure.contracts.workflow.decomposition.validate
import skillbill.infrastructure.contracts.workflow.featuretask.handoff.reason
import skillbill.infrastructure.contracts.workflow.featuretask.handoff.validate
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.checkpoint.validate
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.implementation.validate
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.phase.validate
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.planning.message
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.planning.validate
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.quarantine.validate
import skillbill.infrastructure.contracts.workflow.goal.planning.reason
import skillbill.infrastructure.contracts.workflow.goal.planning.validate
import skillbill.infrastructure.contracts.workflow.goal.progress.validate
import skillbill.infrastructure.contracts.workflow.goal.status.validate
import skillbill.infrastructure.contracts.workflow.workflow.validate
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class GoalObservabilityEventSchemaValidatorTest {
  @Test
  fun `missing required event fields fail with typed schema error`() {
    val error = assertFailsWith<InvalidGoalObservabilityEventSchemaError> {
      GoalObservabilityEventSchemaValidator.validate(
        mapOf(
          "contract_version" to GOAL_OBSERVABILITY_EVENT_CONTRACT_VERSION,
          "issue_key" to "SKILL-61",
        ),
        "goal_observability_latest_event",
      )
    }

    assertContains(error.message.orEmpty(), "Goal observability event")
    assertContains(error.reason, "required")
  }

  @Test
  fun `valid compact event passes without heavy fields`() {
    GoalObservabilityEventSchemaValidator.validate(
      mapOf(
        "contract_version" to GOAL_OBSERVABILITY_EVENT_CONTRACT_VERSION,
        "issue_key" to "SKILL-61",
        "subtask_id" to 1,
        "workflow_phase" to "implement",
        "worker_role" to "phase_subagent",
        "liveness_class" to "durable_progress",
        "activity_summary" to "edited files",
        "timestamp" to "2026-06-01T00:00:00Z",
        "sequence_number" to 1,
      ),
      "goal_observability_latest_event",
    )
  }
}
