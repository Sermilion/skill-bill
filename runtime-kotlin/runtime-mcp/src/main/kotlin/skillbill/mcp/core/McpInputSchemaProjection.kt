package skillbill.mcp.core

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import skillbill.application.telemetry.validation.featureVerifyCompletionStatuses
import skillbill.application.telemetry.validation.qualityCheckResults
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.mcp.McpToolPayloadKeys
import skillbill.contracts.telemetry.LifecycleTelemetryPayloadKeys
import skillbill.error.shellcontent.InvalidTelemetryEventSchemaError
import skillbill.mcp.shared.McpProtocolFramer
import skillbill.mcp.telemetry.TelemetryEventSchemaValidator

internal object McpInputSchemaProjection {
  private val mapper: ObjectMapper = ObjectMapper()

  fun projectedInputSchema(toolName: String): Map<String, Any?> {
    val branch = branchForTool(toolName)
    val defs =
      TelemetryEventSchemaValidator.canonicalSchemaDocument()
        .path(McpProtocolFramer.SCHEMA_DEFS_KEY)
    val inlined = inlineLocalRefs(branch.deepCopy(), defs)
    val stripped = stripAdvertisementEnvelope(inlined, toolName)
    return jsonObjectToMap(stripRuntimeOnlyEnumValues(stripped, toolName))
  }

  private fun branchForTool(toolName: String): JsonNode {
    val defs =
      TelemetryEventSchemaValidator.canonicalSchemaDocument()
        .path(McpProtocolFramer.SCHEMA_DEFS_KEY)
    if (!defs.isObject) {
      throw InvalidTelemetryEventSchemaError(
        fieldPath = McpProtocolFramer.SCHEMA_DEFS_KEY,
        eventName = toolName,
        reason = "Canonical telemetry-event schema is missing a \$defs object.",
      )
    }
    defs.fields().forEach { (_, defNode) ->
      val eventName =
        defNode.path(McpProtocolFramer.SCHEMA_PROPERTIES_KEY)
          .path(McpToolPayloadKeys.EVENT_NAME)
          .path(McpProtocolFramer.SCHEMA_CONST_KEY)
          .asText("")
      if (eventName == toolName) {
        return defNode
      }
    }
    throw InvalidTelemetryEventSchemaError(
      fieldPath = McpToolPayloadKeys.EVENT_NAME,
      eventName = toolName,
      reason = "No \$defs branch pins event_name.const to '$toolName'.",
    )
  }

  private fun stripAdvertisementEnvelope(
    branch: JsonNode,
    toolName: String,
  ): ObjectNode {
    val copy = branch.deepCopy() as ObjectNode
    val properties = copy.path(McpProtocolFramer.SCHEMA_PROPERTIES_KEY)
    if (!properties.isObject) {
      return copy
    }
    val propertiesCopy = properties.deepCopy() as ObjectNode
    val removedPropertyKeys = advertisementRemovedPropertyKeys(toolName)
    removedPropertyKeys.forEach(propertiesCopy::remove)
    copy.set<JsonNode>(McpProtocolFramer.SCHEMA_PROPERTIES_KEY, propertiesCopy)

    val required = copy.path(McpProtocolFramer.SCHEMA_REQUIRED_KEY)
    if (required.isArray) {
      val requiredCopy = mapper.createArrayNode()
      required.forEach { element ->
        val key = element.asText("")
        if (key !in removedPropertyKeys) {
          requiredCopy.add(key)
        }
      }
      copy.set<JsonNode>(McpProtocolFramer.SCHEMA_REQUIRED_KEY, requiredCopy)
    }
    return copy
  }

  private fun advertisementRemovedPropertyKeys(toolName: String): Set<String> {
    val envelopeKeys = linkedSetOf(McpToolPayloadKeys.EVENT_NAME, SharedPayloadKeys.CONTRACT_VERSION)
    if (toolName == McpToolPayloadKeys.QUALITY_CHECK_FINISHED) {
      envelopeKeys += LifecycleTelemetryPayloadKeys.COMPLETION
      envelopeKeys += LifecycleTelemetryPayloadKeys.FINAL_FAILURE_COUNT_AVAILABILITY
      envelopeKeys += LifecycleTelemetryPayloadKeys.STALE_REASON
    }
    return envelopeKeys
  }

  private fun stripRuntimeOnlyEnumValues(
    branch: ObjectNode,
    toolName: String,
  ): ObjectNode {
    val propertyName: String
    val allowedValues: List<String>
    when (toolName) {
      McpToolPayloadKeys.FEATURE_VERIFY_FINISHED -> {
        propertyName = McpToolPayloadKeys.COMPLETION_STATUS
        allowedValues = featureVerifyCompletionStatuses
      }
      McpToolPayloadKeys.QUALITY_CHECK_FINISHED -> {
        propertyName = McpToolPayloadKeys.RESULT
        allowedValues = qualityCheckResults
      }
      else -> return branch
    }
    val properties = branch.path(McpProtocolFramer.SCHEMA_PROPERTIES_KEY)
    val property = properties.path(propertyName)
    val enum = property.path(McpProtocolFramer.SCHEMA_ENUM_KEY)
    if (!properties.isObject || !property.isObject || !enum.isArray) {
      return branch
    }
    val enumCopy = mapper.createArrayNode()
    enum.forEach { value ->
      if (value.asText("") in allowedValues) {
        enumCopy.add(value)
      }
    }
    val propertyCopy = property.deepCopy() as ObjectNode
    propertyCopy.set<JsonNode>(McpProtocolFramer.SCHEMA_ENUM_KEY, enumCopy)
    val propertiesCopy = properties.deepCopy() as ObjectNode
    propertiesCopy.set<JsonNode>(propertyName, propertyCopy)
    branch.set<JsonNode>(McpProtocolFramer.SCHEMA_PROPERTIES_KEY, propertiesCopy)
    return branch
  }

  private fun inlineLocalRefs(
    node: JsonNode,
    defs: JsonNode,
  ): JsonNode {
    if (node.isObject) {
      val ref = node.path(McpProtocolFramer.SCHEMA_REF_KEY)
      if (!ref.isMissingNode && ref.isTextual) {
        val refText = ref.asText("")
        val defsPrefix = "#/${McpProtocolFramer.SCHEMA_DEFS_KEY}/"
        if (refText.startsWith(defsPrefix)) {
          val defName = refText.removePrefix(defsPrefix)
          val resolved = defs.path(defName)
          if (!resolved.isMissingNode) {
            return inlineLocalRefs(resolved.deepCopy(), defs)
          }
        }
      }
      val copy = node.deepCopy() as ObjectNode
      val fieldNames = copy.fieldNames().asSequence().toList()
      fieldNames.forEach { fieldName ->
        copy.set<JsonNode>(fieldName, inlineLocalRefs(copy.get(fieldName), defs))
      }
      return copy
    }
    if (node.isArray) {
      val copy = mapper.createArrayNode()
      node.forEach { element -> copy.add(inlineLocalRefs(element, defs)) }
      return copy
    }
    return node
  }

  private fun jsonObjectToMap(node: ObjectNode): Map<String, Any?> =
    JsonCodec.anyToStringAnyMap(mapper.convertValue(node, Any::class.java))
      ?: emptyMap()
}
