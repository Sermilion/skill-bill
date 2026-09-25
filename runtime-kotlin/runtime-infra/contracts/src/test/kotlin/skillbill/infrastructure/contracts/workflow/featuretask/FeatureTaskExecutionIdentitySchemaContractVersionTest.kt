package skillbill.infrastructure.contracts.workflow.featuretask

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import skillbill.contracts.workflow.identity.task.FEATURE_TASK_EXECUTION_IDENTITY_CONTRACT_VERSION
import skillbill.contracts.workflow.identity.task.FeatureTaskExecutionIdentitySchemaPaths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FeatureTaskExecutionIdentitySchemaContractVersionTest {
  @Test
  fun `execution identity schema version and id match runtime constants`() {
    val stream =
      assertNotNull(
        javaClass.classLoader.getResourceAsStream(FeatureTaskExecutionIdentitySchemaPaths.CLASSPATH_RESOURCE),
      )
    val schema = stream.use { YAMLMapper().readTree(it) }
    assertEquals(
      FEATURE_TASK_EXECUTION_IDENTITY_CONTRACT_VERSION,
      schema.path("properties").path("contract_version").path("const").asText(),
    )
    assertEquals(FeatureTaskExecutionIdentitySchemaPaths.EXPECTED_SCHEMA_ID, schema.path("\$id").asText())
  }
}
