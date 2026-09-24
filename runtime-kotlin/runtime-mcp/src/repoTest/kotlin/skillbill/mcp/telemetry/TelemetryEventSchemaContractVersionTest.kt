package skillbill.mcp.telemetry

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import skillbill.testing.repoRootFromTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TelemetryEventSchemaContractVersionTest {
  @Test
  fun `schema contract_version const matches TELEMETRY_EVENT_CONTRACT_VERSION`() {
    val schemaFile = repoRootFromTest().resolve(TelemetryEventSchemaPaths.REPO_RELATIVE_PATH)
    assertTrue(Files.isRegularFile(schemaFile), "Canonical schema file is missing at $schemaFile.")

    val schema: JsonNode = YAMLMapper().readTree(Files.readString(schemaFile))
    val contractVersionNode = schema.path("properties").path("contract_version").path("const")
    assertNotNull(
      contractVersionNode.takeIf { !it.isMissingNode && it.isTextual },
      "Schema must pin properties.contract_version.const as a string; found: $contractVersionNode",
    )
    assertEquals(
      TELEMETRY_EVENT_CONTRACT_VERSION,
      contractVersionNode.asText(),
      "Schema contract_version.const must equal TELEMETRY_EVENT_CONTRACT_VERSION " +
        "($TELEMETRY_EVENT_CONTRACT_VERSION).",
    )
  }

  @Test
  fun `every per-event branch pins contract_version to TELEMETRY_EVENT_CONTRACT_VERSION`() {
    val schemaFile = repoRootFromTest().resolve(TelemetryEventSchemaPaths.REPO_RELATIVE_PATH)
    val schema: JsonNode = YAMLMapper().readTree(Files.readString(schemaFile))
    val defs = schema.path("\$defs")
    assertTrue(defs.isObject, "Schema \$defs must be an object.")

    defs.fields().forEach { (defName, defNode) ->
      if (!defName.endsWith("Event")) {
        return@forEach
      }
      val branchConst = defNode.path("properties").path("contract_version").path("const")
      assertTrue(
        !branchConst.isMissingNode && branchConst.isTextual,
        "Branch '$defName' must pin properties.contract_version.const as a string; found: $branchConst",
      )
      assertEquals(
        TELEMETRY_EVENT_CONTRACT_VERSION,
        branchConst.asText(),
        "Branch '$defName' contract_version.const must equal TELEMETRY_EVENT_CONTRACT_VERSION " +
          "($TELEMETRY_EVENT_CONTRACT_VERSION).",
      )
    }
  }
}
