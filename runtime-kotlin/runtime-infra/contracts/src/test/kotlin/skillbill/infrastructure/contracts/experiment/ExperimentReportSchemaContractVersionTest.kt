package skillbill.infrastructure.contracts.experiment
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import skillbill.contracts.experiment.EXPERIMENT_REPORT_CONTRACT_VERSION
import skillbill.contracts.experiment.ExperimentReportSchemaPaths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExperimentReportSchemaContractVersionTest {
  @Test
  fun `schema contract_version const matches EXPERIMENT_REPORT_CONTRACT_VERSION`() {
    val schema = classpathSchema()
    val contractVersionNode = schema.path("properties").path("contract_version").path("const")
    assertTrue(!contractVersionNode.isMissingNode && contractVersionNode.isTextual)
    assertEquals(EXPERIMENT_REPORT_CONTRACT_VERSION, contractVersionNode.asText())
  }

  @Test
  fun `schema id matches ExperimentReportSchemaPaths EXPECTED_SCHEMA_ID`() {
    assertEquals(ExperimentReportSchemaPaths.EXPECTED_SCHEMA_ID, classpathSchema().path("\$id").asText())
  }

  private fun classpathSchema(): JsonNode {
    val stream = ExperimentReportSchemaValidator::class.java.classLoader
      .getResourceAsStream(ExperimentReportSchemaPaths.CLASSPATH_RESOURCE)
    assertNotNull(stream)
    return YAMLMapper().readTree(stream.use { it.readBytes().toString(Charsets.UTF_8) })
  }
}
