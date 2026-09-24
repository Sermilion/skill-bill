package skillbill.infrastructure.contracts.experiment

import skillbill.contracts.experiment.EXPERIMENT_DESCRIPTOR_CONTRACT_VERSION
import skillbill.contracts.experiment.ExperimentDescriptorPayloadKeys
import skillbill.error.shellcontent.InvalidExperimentDescriptorSchemaError
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ExperimentSchemaValidatorTest {
  @Test
  fun `malformed descriptor loud-fails with typed error`() {
    assertFailsWith<InvalidExperimentDescriptorSchemaError> {
      ExperimentDescriptorSchemaValidator.validate(
        mapOf(ExperimentDescriptorPayloadKeys.CONTRACT_VERSION to EXPERIMENT_DESCRIPTOR_CONTRACT_VERSION),
        "test",
      )
    }
  }
}
