
package skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.projection
import skillbill.error.shellcontent.InvalidFeatureTaskRuntimePlanningProjectionSchemaError
import skillbill.infrastructure.contracts.workflow.decomposition.validate
import skillbill.infrastructure.contracts.workflow.featuretask.handoff.reason
import skillbill.infrastructure.contracts.workflow.featuretask.handoff.validate
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.checkpoint.validate
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.implementation.validate
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.phase.validate
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.planning.FeatureTaskRuntimePlanningProjectionSchemaValidator
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.planning.validate
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.quarantine.validate
import skillbill.infrastructure.contracts.workflow.goal.observability.validate
import skillbill.infrastructure.contracts.workflow.goal.planning.reason
import skillbill.infrastructure.contracts.workflow.goal.planning.validate
import skillbill.infrastructure.contracts.workflow.goal.progress.validate
import skillbill.infrastructure.contracts.workflow.goal.status.validate
import skillbill.infrastructure.contracts.workflow.workflow.validate
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FeatureTaskRuntimeProjectionCanonicalizationSchemaTest {
  private val validator = FeatureTaskRuntimePlanningProjectionSchemaValidator

  @Test
  fun `legacy implementation_receipt payload fails the reject-all planning projections schema`() {
    val error = assertFailsWith<InvalidFeatureTaskRuntimePlanningProjectionSchemaError> {
      validator.validate(
        mapOf(
          "projection_kind" to "implementation_receipt",
          "contract_version" to "0.2",
          "completed_task_ids" to listOf("task-1"),
          "changed_paths" to listOf("src/Foo.kt"),
          "tests_executed" to listOf(mapOf("name" to "FooTest.kt", "outcome" to "passed")),
          "reconciliation_evidence" to mapOf("reconciled" to true, "evidence" to "ok"),
        ),
        "implement#produced_outputs",
      )
    }

    assertTrue(error.reason.isNotBlank(), "reject-all schema must surface a non-empty reason")
  }

  @Test
  fun `a prose value wrapper is not validated as a bounded planning projection`() {
    assertFailsWith<InvalidFeatureTaskRuntimePlanningProjectionSchemaError> {
      validator.validate(
        mapOf(
          "value" to "Dense implement prose stuffed with legacy receipt JSON.",
          "completed_task_ids" to listOf("task-1"),
        ),
        "implement#produced_outputs",
      )
    }
  }
}
