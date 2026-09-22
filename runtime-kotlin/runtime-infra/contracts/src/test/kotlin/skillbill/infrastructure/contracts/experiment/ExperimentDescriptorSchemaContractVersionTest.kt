package skillbill.infrastructure.contracts.experiment
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import skillbill.contracts.experiment.EXPERIMENT_DESCRIPTOR_CONTRACT_VERSION
import skillbill.contracts.experiment.ExperimentDescriptorSchemaPaths
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExperimentDescriptorSchemaContractVersionTest {
  @Test
  fun `schema contract_version const matches EXPERIMENT_DESCRIPTOR_CONTRACT_VERSION`() {
    val schema = classpathSchema()
    val contractVersionNode = schema.path("properties").path("contract_version").path("const")
    assertTrue(!contractVersionNode.isMissingNode && contractVersionNode.isTextual)
    assertEquals(EXPERIMENT_DESCRIPTOR_CONTRACT_VERSION, contractVersionNode.asText())
  }

  @Test
  fun `schema content hash matches known hash for current contract version`() {
    val resourceStream =
      ExperimentDescriptorSchemaValidator::class.java.classLoader
        .getResourceAsStream(ExperimentDescriptorSchemaPaths.CLASSPATH_RESOURCE)
    assertNotNull(resourceStream)
    val yamlText = resourceStream.use { it.readBytes().toString(Charsets.UTF_8) }
    val contentWithoutVersionLine =
      yamlText.lines()
        .filter { !it.trimStart().startsWith("const:") }
        .joinToString("\n")
    val digest = MessageDigest.getInstance("SHA-256")
    val actualHash =
      digest.digest(contentWithoutVersionLine.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    assertEquals(KNOWN_SCHEMA_CONTENT_HASH, actualHash)
  }

  private fun classpathSchema(): JsonNode {
    val resourceStream =
      ExperimentDescriptorSchemaValidator::class.java.classLoader
        .getResourceAsStream(ExperimentDescriptorSchemaPaths.CLASSPATH_RESOURCE)
    assertNotNull(resourceStream)
    val yamlText = resourceStream.use { it.readBytes().toString(Charsets.UTF_8) }
    return YAMLMapper().readTree(yamlText)
  }

  private companion object {
    const val KNOWN_SCHEMA_CONTENT_HASH: String =
      "cca5367f00a5cf6c0447463a08c1ffabdea9d7e60e9d3a6456f26382984fb4e4"
  }
}
