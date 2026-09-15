package skillbill.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import skillbill.contracts.JsonCodec
import skillbill.mcp.core.McpToolRegistry
import skillbill.mcp.telemetry.TelemetryEventSchemaPaths
import skillbill.testing.repoRootFromTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class TelemetryEventInputSchemaParityTest {

  private val runtimeInternalEmissionEvents =
    setOf(
      "goal_started",
      "goal_subtask_finished",
      "goal_finished",
      "goal_issue_finished",
      "skillbill_review_finished",
      "skillbill_review_stage_degradation",
      "skillbill_review_finished_legacy_regenerated",
    )

  private val schemaNode: JsonNode by lazy {
    val schemaFile = repoRootFromTest().resolve(TelemetryEventSchemaPaths.REPO_RELATIVE_PATH)
    assertTrue(Files.isRegularFile(schemaFile), "Canonical schema file is missing at $schemaFile.")
    YAMLMapper().readTree(Files.readString(schemaFile))
  }

  @Test
  fun `every McpToolRegistry tool has a matching schema branch`() {
    val defs = schemaNode.path("\$defs")
    assertTrue(defs.isObject, "Schema \$defs must be an object.")

    McpToolRegistry.tools.forEach { tool ->
      val branchName = branchNameFor(tool.name)
      val branch = defs.path(branchName)
      assertTrue(
        !branch.isMissingNode,
        "Telemetry parity: event '${tool.name}' has no branch '\$defs/$branchName' in the canonical schema. " +
          "Add a branch keyed on event_name='${tool.name}'.",
      )

      val branchEventName = branch.path("properties").path("event_name").path("const").asText("")
      assertEquals(
        tool.name,
        branchEventName,
        "Telemetry parity: branch '$branchName' must pin event_name.const to '${tool.name}', " +
          "found '$branchEventName'.",
      )

      val expectedAdditionalProps = expectedAdditionalPropertiesFor(tool.inputSchema)
      val actualAdditionalProps = additionalPropertiesFlag(branch)
      assertEquals(
        expectedAdditionalProps,
        actualAdditionalProps,
        "Telemetry parity: branch '$branchName' additionalProperties=$actualAdditionalProps " +
          "but McpToolRegistry inputSchema for event '${tool.name}' implies additionalProperties=" +
          "$expectedAdditionalProps.",
      )

      if (!expectedAdditionalProps) {
        val kotlinKeys = inputSchemaPropertyKeys(tool.inputSchema)
        val yamlKeys = branchPropertyKeys(branch) - setOf("event_name", "contract_version")
        assertEquals(
          kotlinKeys,
          yamlKeys,
          "Telemetry parity: branch '$branchName' property keys do not match McpToolRegistry inputSchema. " +
            "kotlin=$kotlinKeys yaml=$yamlKeys",
        )

        val kotlinRequired = inputSchemaRequiredKeys(tool.inputSchema)
        val yamlRequired = branchRequiredKeys(branch) - setOf("event_name", "contract_version")
        assertEquals(
          kotlinRequired,
          yamlRequired,
          "Telemetry parity: branch '$branchName' required[] does not match McpToolRegistry inputSchema. " +
            "kotlin=$kotlinRequired yaml=$yamlRequired",
        )
      }
    }
  }

  @Test
  fun `every schema event branch has a matching McpToolRegistry tool`() {
    val defs = schemaNode.path("\$defs")
    val knownEvents = McpToolRegistry.tools.map { it.name }.toSet()

    defs.fields().forEach { (defName, defNode) ->
      if (!defName.endsWith("Event")) {
        return@forEach
      }
      val branchEventName = defNode.path("properties").path("event_name").path("const").asText("")
      assertTrue(
        branchEventName in knownEvents || branchEventName in runtimeInternalEmissionEvents,
        "Telemetry parity: branch '$defName' pins event_name='$branchEventName' which is not in " +
          "McpToolRegistry.tools. Remove the branch or add the event name to McpToolRegistry.toolNames.",
      )
    }
  }

  private fun branchNameFor(eventName: String): String {
    val parts = eventName.split('_')
    return parts.first() + parts.drop(1).joinToString("") { segment ->
      segment.replaceFirstChar { it.uppercase() }
    } + "Event"
  }
  private fun expectedAdditionalPropertiesFor(inputSchema: Map<String, Any?>): Boolean {
    val raw = inputSchema["additionalProperties"]
    return when (raw) {
      true -> true
      false -> false
      null -> {
        true
      }
      else -> fail(
        "Telemetry parity: inputSchema has non-boolean additionalProperties=$raw " +
          "(${raw::class.qualifiedName}); fix McpToolSpec.openObjectSchema/strictObjectSchema.",
      )
    }
  }
  private fun inputSchemaPropertyKeys(inputSchema: Map<String, Any?>): Set<String> {
    val properties = JsonCodec.anyToStringAnyMap(inputSchema["properties"]) ?: return emptySet()
    return properties.keys.toSet()
  }
  private fun inputSchemaRequiredKeys(inputSchema: Map<String, Any?>): Set<String> {
    val required = inputSchema["required"] as? List<*> ?: return emptySet()
    return required.mapNotNull { entry -> entry as? String }.toSet()
  }

  private fun additionalPropertiesFlag(branch: JsonNode): Boolean {
    val node = branch.path("additionalProperties")
    return when {
      node.isMissingNode -> true
      node.isBoolean -> node.asBoolean()
      else -> fail("Telemetry parity: branch additionalProperties is not a boolean (got $node).")
    }
  }

  private fun branchPropertyKeys(branch: JsonNode): Set<String> {
    val properties = branch.path("properties")
    if (properties.isMissingNode || !properties.isObject) return emptySet()
    return properties.fieldNames().asSequence().toSet()
  }

  private fun branchRequiredKeys(branch: JsonNode): Set<String> {
    val required = branch.path("required")
    if (required.isMissingNode || !required.isArray) return emptySet()
    return required.elements().asSequence().map { it.asText() }.toSet()
  }
}
