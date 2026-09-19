package skillbill.infrastructure.contracts.workflow.featuretask.handoff
import skillbill.error.shellcontent.InvalidFeatureTaskRuntimePersistenceSchemaError
import skillbill.error.shellcontent.InvalidFeatureTaskRuntimePhaseHandoffSchemaError
import skillbill.infrastructure.contracts.workflow.decomposition.validate
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.checkpoint.validate
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.implementation.validate
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.phase.validate
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.planning.validate
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.quarantine.validate
import skillbill.infrastructure.contracts.workflow.goal.observability.validate
import skillbill.infrastructure.contracts.workflow.goal.planning.validate
import skillbill.infrastructure.contracts.workflow.goal.progress.validate
import skillbill.infrastructure.contracts.workflow.goal.status.validate
import skillbill.infrastructure.contracts.workflow.workflow.validate
import kotlin.test.Test
import kotlin.test.assertFailsWith

class FeatureTaskRuntimeHandoffFoundationSchemaValidatorTest {
  @Test
  fun `phase handoff validator rejects the legacy flat source shape`() {
    assertFailsWith<InvalidFeatureTaskRuntimePhaseHandoffSchemaError> {
      FeatureTaskRuntimePhaseHandoffSchemaValidator.validate(
        mapOf("contract_version" to "0.2", "source_ref" to "upstream_phase_output:plan"),
        "implement.plan_receipt",
      )
    }
  }

  @Test
  fun `persistence validator rejects consumer delivery count posing as producer iteration`() {
    assertFailsWith<InvalidFeatureTaskRuntimePersistenceSchemaError> {
      FeatureTaskRuntimePersistenceSchemaValidator.validate(
        mapOf(
          "contract_version" to "0.2",
          "record_kind" to "delivered_projection",
          "workflow_id" to "wftr-1",
          "consumer_phase_id" to "audit",
          "producer_iteration" to 4,
          "repository_checkpoint" to mapOf("fingerprint" to "checkpoint"),
          "handoff_envelope" to emptyMap<String, Any?>(),
        ),
        "audit.delivery",
      )
    }
  }
}
