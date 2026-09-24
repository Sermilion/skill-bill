package skillbill.mcp.telemetry

import skillbill.mcp.core.McpInputSchemaProjection
import skillbill.mcp.core.McpToolRegistry
import kotlin.test.Test
import kotlin.test.assertTrue

class TelemetryEventSchemaValidatesAllEventsTest {
  @Test
  fun `every McpToolRegistry tool emits a schema-clean representative envelope`() {
    val events = McpToolRegistry.tools
    assertTrue(events.isNotEmpty(), "McpToolRegistry.tools must not be empty.")

    events.forEach { tool ->
      val envelope =
        buildRepresentativeEnvelope(tool.name, McpInputSchemaProjection.projectedInputSchema(tool))

      TelemetryEventSchemaValidator.validate(envelope = envelope, eventName = tool.name)
    }
  }

  private fun buildRepresentativeEnvelope(
    eventName: String,
    inputSchema: Map<String, Any?>,
  ): Map<String, Any?> {
    val envelope =
      linkedMapOf<String, Any?>(
        "event_name" to eventName,
        "contract_version" to TELEMETRY_EVENT_CONTRACT_VERSION,
      )

    val required =
      (inputSchema["required"] as? List<*>)
        ?.mapNotNull { entry -> entry as? String }
        .orEmpty()
    val properties =
      (inputSchema["properties"] as? Map<*, *>)
        ?.mapNotNull { (key, value) ->
          val stringKey = key as? String ?: return@mapNotNull null
          val fieldSchema = value as? Map<*, *> ?: return@mapNotNull null
          stringKey to
            fieldSchema.entries
              .mapNotNull { (fieldKey, fieldValue) ->
                (fieldKey as? String)?.let { typedKey -> typedKey to fieldValue }
              }
              .toMap()
        }
        ?.toMap()
        .orEmpty()
    required.forEach { fieldName ->
      val fieldSchema = properties[fieldName] ?: mapOf("type" to "string")
      envelope[fieldName] = representativeValue(fieldSchema)
    }
    return envelope
  }

  private fun representativeValue(fieldSchema: Map<String, Any?>): Any? {
    val types =
      when (val type = fieldSchema["type"]) {
        is String -> listOf(type)
        is List<*> -> type.filterIsInstance<String>()
        else -> emptyList()
      }
    return when {
      "string" in types -> representativeString(fieldSchema)
      "integer" in types -> representativeInteger(fieldSchema)
      "number" in types -> 0
      "boolean" in types -> false
      "array" in types -> emptyList<Any?>()
      "object" in types -> emptyMap<String, Any?>()
      "null" in types -> null
      else -> ""
    }
  }

  private fun representativeString(fieldSchema: Map<String, Any?>): String {
    val enum = fieldSchema["enum"] as? List<*>
    if (enum != null && enum.isNotEmpty()) return enum.first().toString()
    val minLength = (fieldSchema["minLength"] as? Number)?.toInt()
    return if (minLength != null && minLength > 0) "x" else ""
  }

  private fun representativeInteger(fieldSchema: Map<String, Any?>): Int {
    val minimum = (fieldSchema["minimum"] as? Number)?.toInt()
    return if (minimum != null && minimum > 0) minimum else 0
  }
}
