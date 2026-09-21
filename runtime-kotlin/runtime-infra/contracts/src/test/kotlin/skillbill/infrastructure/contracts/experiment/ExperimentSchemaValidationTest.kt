package skillbill.infrastructure.contracts.experiment
import skillbill.contracts.experiment.EXPERIMENT_DESCRIPTOR_CONTRACT_VERSION
import skillbill.contracts.experiment.EXPERIMENT_OBSERVATION_CONTRACT_VERSION
import skillbill.contracts.experiment.ExperimentDescriptorPayloadKeys
import skillbill.contracts.experiment.ExperimentObservationPayloadKeys
import skillbill.error.shellcontent.InvalidExperimentDescriptorSchemaError
import skillbill.error.shellcontent.InvalidExperimentObservationSchemaError
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ExperimentSchemaValidationTest {
  @Test
  fun `malformed experiment descriptor loud fails with typed error`() {
    assertFailsWith<InvalidExperimentDescriptorSchemaError> {
      ExperimentDescriptorSchemaValidator.validate(mapOf("contract_version" to "0.1"), "malformed")
    }
  }

  @Test
  fun `minimal valid descriptor parses`() {
    ExperimentDescriptorSchemaValidator.validate(
      mapOf(
        ExperimentDescriptorPayloadKeys.CONTRACT_VERSION to EXPERIMENT_DESCRIPTOR_CONTRACT_VERSION,
        ExperimentDescriptorPayloadKeys.NAME to "fixture-goal",
        ExperimentDescriptorPayloadKeys.DESCRIPTOR_VERSION to "1",
        ExperimentDescriptorPayloadKeys.EXECUTION_MODE to "goal_pair",
        ExperimentDescriptorPayloadKeys.REQUIRED_LAUNCHER_CAPABILITIES to emptyList<String>(),
        ExperimentDescriptorPayloadKeys.TREATMENT_CAPABILITY to "treatment-enabled",
      ),
      "valid",
    )
  }

  @Test
  fun `unavailable measurement requires a reason and measured requires a quantity`() {
    val base = mapOf<String, Any?>(
      ExperimentObservationPayloadKeys.CONTRACT_VERSION to EXPERIMENT_OBSERVATION_CONTRACT_VERSION,
      ExperimentObservationPayloadKeys.OBSERVATION_ID to "observation-1",
      ExperimentObservationPayloadKeys.PAIR_ID to "pair-1",
      ExperimentObservationPayloadKeys.ARM_ID to "control",
      ExperimentObservationPayloadKeys.EVENT_IDENTITY to mapOf(
        ExperimentObservationPayloadKeys.WORKFLOW_ID to "workflow-1",
        ExperimentObservationPayloadKeys.PHASE_ID to "implement",
        ExperimentObservationPayloadKeys.ATTEMPT to 1,
        ExperimentObservationPayloadKeys.EVENT_KIND to "usage",
      ),
      ExperimentObservationPayloadKeys.RECORDED_AT to "2026-09-21T00:00:00Z",
    )

    assertFailsWith<InvalidExperimentObservationSchemaError> {
      ExperimentObservationSchemaValidator.validate(
        base + (
          ExperimentObservationPayloadKeys.MEASUREMENTS to listOf(
            mapOf(
              ExperimentObservationPayloadKeys.METRIC_ID to "cost",
              ExperimentObservationPayloadKeys.AVAILABILITY to "unavailable_incomplete",
            ),
          )
          ),
        "missing-reason",
      )
    }
    assertFailsWith<InvalidExperimentObservationSchemaError> {
      ExperimentObservationSchemaValidator.validate(
        base + (
          ExperimentObservationPayloadKeys.MEASUREMENTS to listOf(
            mapOf(
              ExperimentObservationPayloadKeys.METRIC_ID to "cost",
              ExperimentObservationPayloadKeys.AVAILABILITY to "measured",
            ),
          )
          ),
        "missing-quantity",
      )
    }
  }
}
