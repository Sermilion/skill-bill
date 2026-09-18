package skillbill.infrastructure.skills.nativeagent

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import skillbill.infrastructure.skills.nativeagent.composition.NATIVE_AGENT_COMPOSITION_CONTRACT_VERSION
import skillbill.infrastructure.skills.nativeagent.composition.NativeAgentCompositionSchemaPaths
import skillbill.infrastructure.skills.nativeagent.composition.NativeAgentCompositionSchemaValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NativeAgentCompositionSchemaContractVersionTest {
  @Test
  fun `schema contract_version const matches NATIVE_AGENT_COMPOSITION_CONTRACT_VERSION`() {
    val resourceStream = NativeAgentCompositionSchemaValidator::class.java.classLoader
      .getResourceAsStream(NativeAgentCompositionSchemaPaths.CLASSPATH_RESOURCE)
    assertNotNull(
      resourceStream,
      "Canonical native-agent composition schema is missing from the classpath at " +
        "'${NativeAgentCompositionSchemaPaths.CLASSPATH_RESOURCE}'. " +
        "Ensure `copyNativeAgentCompositionSchema` ran before this test.",
    )
    val yamlText = resourceStream.use { it.readBytes().toString(Charsets.UTF_8) }
    val schema: JsonNode = YAMLMapper().readTree(yamlText)
    val contractVersionNode = schema.path("\$defs").path("contractVersion").path("const")
    assertTrue(
      !contractVersionNode.isMissingNode && contractVersionNode.isTextual,
      "Schema must pin \$defs.contractVersion.const as a string; found: $contractVersionNode",
    )
    assertEquals(
      NATIVE_AGENT_COMPOSITION_CONTRACT_VERSION,
      contractVersionNode.asText(),
      "Schema contract_version.const must equal NATIVE_AGENT_COMPOSITION_CONTRACT_VERSION " +
        "($NATIVE_AGENT_COMPOSITION_CONTRACT_VERSION).",
    )
  }

  @Test
  fun `schema id matches NativeAgentCompositionSchemaPaths EXPECTED_SCHEMA_ID`() {
    val resourceStream = NativeAgentCompositionSchemaValidator::class.java.classLoader
      .getResourceAsStream(NativeAgentCompositionSchemaPaths.CLASSPATH_RESOURCE)
    assertNotNull(
      resourceStream,
      "Canonical native-agent composition schema is missing from the classpath at " +
        "'${NativeAgentCompositionSchemaPaths.CLASSPATH_RESOURCE}'.",
    )
    val yamlText = resourceStream.use { it.readBytes().toString(Charsets.UTF_8) }
    val schema: JsonNode = YAMLMapper().readTree(yamlText)
    val idNode = schema.path("\$id")
    assertTrue(!idNode.isMissingNode && idNode.isTextual, "Schema must declare a textual `\$id`.")
    assertEquals(
      NativeAgentCompositionSchemaPaths.EXPECTED_SCHEMA_ID,
      idNode.asText(),
      "Schema `\$id` must equal NativeAgentCompositionSchemaPaths.EXPECTED_SCHEMA_ID.",
    )
  }
}
