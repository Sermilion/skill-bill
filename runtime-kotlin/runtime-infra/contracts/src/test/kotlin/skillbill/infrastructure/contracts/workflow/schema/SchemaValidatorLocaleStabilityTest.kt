package skillbill.infrastructure.contracts.workflow.schema
import skillbill.contracts.workflow.featuretask.FEATURE_TASK_RUNTIME_PLANNING_PROJECTIONS_CONTRACT_VERSION
import skillbill.error.shellcontent.InvalidFeatureTaskRuntimePlanningProjectionSchemaError
import skillbill.infrastructure.contracts.workflow.decomposition.schema
import skillbill.infrastructure.contracts.workflow.decomposition.validate
import skillbill.infrastructure.contracts.workflow.featuretask.handoff.payload
import skillbill.infrastructure.contracts.workflow.featuretask.handoff.reason
import skillbill.infrastructure.contracts.workflow.featuretask.handoff.schema
import skillbill.infrastructure.contracts.workflow.featuretask.handoff.validate
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.buildreceipt.payload
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.buildreceipt.schema
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.checkpoint.schema
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.checkpoint.validate
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.implementation.schema
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.implementation.validate
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.phase.schema
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.phase.validate
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.planning.FeatureTaskRuntimePlanningProjectionSchemaValidator
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.planning.schema
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.planning.validate
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.quarantine.schema
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.quarantine.validate
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.shared.payload
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.validation.schema
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.worker.schema
import skillbill.infrastructure.contracts.workflow.featuretask.schema.schema
import skillbill.infrastructure.contracts.workflow.goal.observability.schema
import skillbill.infrastructure.contracts.workflow.goal.observability.validate
import skillbill.infrastructure.contracts.workflow.goal.planning.reason
import skillbill.infrastructure.contracts.workflow.goal.planning.schema
import skillbill.infrastructure.contracts.workflow.goal.planning.validate
import skillbill.infrastructure.contracts.workflow.goal.progress.schema
import skillbill.infrastructure.contracts.workflow.goal.progress.validate
import skillbill.infrastructure.contracts.workflow.goal.status.schema
import skillbill.infrastructure.contracts.workflow.goal.status.validate
import skillbill.infrastructure.contracts.workflow.goal.subtask.schema
import skillbill.infrastructure.contracts.workflow.phase.schema
import skillbill.infrastructure.contracts.workflow.workflow.validate
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SchemaValidatorLocaleStabilityTest {
  @Test
  fun `reject-all planning projection still yields English-ish non-blank reason under GERMANY locale`() {
    withDefaultLocale(Locale.GERMANY) {
      val error = assertFailsWith<InvalidFeatureTaskRuntimePlanningProjectionSchemaError> {
        FeatureTaskRuntimePlanningProjectionSchemaValidator.validate(
          payload = legacyReceiptPayload(),
          sourceLabel = "implement#produced_outputs",
        )
      }
      assertEquals("implement#produced_outputs", error.sourceLabel)
      assertTrue(error.reason.isNotBlank(), "reject-all reason must stay non-blank under host locale")
      assertTrue(
        error.reason.any { it in 'A'..'Z' || it in 'a'..'z' },
        "locale-stable validator must keep an English-ish reason: ${error.reason}",
      )
    }
  }

  @Test
  fun `reject-all sourceLabel is preserved under any host locale`() {
    withDefaultLocale(Locale.GERMANY) {
      val error = assertFailsWith<InvalidFeatureTaskRuntimePlanningProjectionSchemaError> {
        FeatureTaskRuntimePlanningProjectionSchemaValidator.validate(
          payload = legacyReceiptPayload(),
          sourceLabel = "implement#produced_outputs",
        )
      }
      assertEquals("implement#produced_outputs", error.sourceLabel)
      assertTrue(error.reason.isNotBlank())
    }
  }

  private fun legacyReceiptPayload(): Map<String, Any?> = linkedMapOf(
    "projection_kind" to "implementation_receipt",
    "contract_version" to FEATURE_TASK_RUNTIME_PLANNING_PROJECTIONS_CONTRACT_VERSION,
    "completed_task_ids" to listOf("task-01"),
    "changed_paths" to listOf("runtime-domain/model/X.kt"),
    "tests_executed" to listOf(linkedMapOf("name" to "XTest.kt", "outcome" to "passed")),
    "reconciliation_evidence" to linkedMapOf(
      "reconciled" to true,
      "evidence" to "Verified X.kt matches the plan commitment; no edit was required. ".repeat(80),
    ),
    "repository_checkpoint" to linkedMapOf("fingerprint" to "abc123"),
    "reconciled_state" to linkedMapOf("reconciled" to true),
    "deferred_repair_item_ids" to emptyList<String>(),
    "repair_item_results" to emptyList<Map<String, Any?>>(),
  )

  private fun withDefaultLocale(locale: Locale, block: () -> Unit) {
    val previous = Locale.getDefault()
    Locale.setDefault(locale)
    try {
      block()
    } finally {
      Locale.setDefault(previous)
    }
  }
}
