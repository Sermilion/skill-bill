package skillbill.infrastructure.contracts.experiment
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import skillbill.contracts.experiment.EXPERIMENT_PAIR_CONTRACT_VERSION
import skillbill.contracts.experiment.ExperimentPairSchemaPaths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExperimentPairSchemaContractVersionTest {
  @Test
  fun `schema contract_version const matches EXPERIMENT_PAIR_CONTRACT_VERSION`() {
    val schema = classpathSchema()
    val contractVersionNode = schema.path("properties").path("contract_version").path("const")
    assertTrue(!contractVersionNode.isMissingNode && contractVersionNode.isTextual)
    assertEquals(EXPERIMENT_PAIR_CONTRACT_VERSION, contractVersionNode.asText())
  }

  @Test
  fun `schema id matches ExperimentPairSchemaPaths EXPECTED_SCHEMA_ID`() {
    assertEquals(ExperimentPairSchemaPaths.EXPECTED_SCHEMA_ID, classpathSchema().path("\$id").asText())
  }

  private fun classpathSchema(): JsonNode {
    val stream = ExperimentPairSchemaValidator::class.java.classLoader
      .getResourceAsStream(ExperimentPairSchemaPaths.CLASSPATH_RESOURCE)
    assertNotNull(stream)
    return YAMLMapper().readTree(stream.use { it.readBytes().toString(Charsets.UTF_8) })
  }
}
