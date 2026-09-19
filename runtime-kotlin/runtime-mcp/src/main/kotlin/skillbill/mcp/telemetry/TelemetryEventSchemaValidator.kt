package skillbill.mcp.telemetry

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.PathType
import com.networknt.schema.SchemaValidatorsConfig
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import skillbill.contracts.mcp.McpToolPayloadKeys
import skillbill.contracts.telemetry.LifecycleSessionCompletion
import skillbill.contracts.telemetry.LifecycleTelemetryPayloadKeys
import skillbill.error.shellcontent.InvalidTelemetryEventSchemaError
import java.io.IOException
import java.util.Locale
internal const val TELEMETRY_EVENT_CONTRACT_VERSION: String = "1.12.0"

private val LOCALE_STABLE_SCHEMA_CONFIG: SchemaValidatorsConfig =
  SchemaValidatorsConfig.builder().locale(Locale.ENGLISH).pathType(PathType.LEGACY).build()

internal object TelemetryEventSchemaValidator {
  private val schemaDocument: JsonNode by lazy { loadSchemaDocument() }
  private val schema: JsonSchema by lazy { compileSchema(schemaDocument) }
  private val mapper: ObjectMapper by lazy { ObjectMapper() }

  fun canonicalSchemaDocument(): JsonNode = schemaDocument

  fun validate(envelope: Map<String, Any?>, eventName: String? = null) {
    val instance: JsonNode = mapper.valueToTree(envelope)
    val resolvedEventName: String? = eventName ?: (envelope["event_name"] as? String)
    val errors: Set<ValidationMessage> = schema.validate(instance)
    if (errors.isEmpty()) {
      validateCoherence(envelope, resolvedEventName)
      return
    }

    val sorted = errors.sortedWith(violationOrdering)
    val firstError = sorted.first()
    val instanceLocation = firstError.instanceLocation?.toString().orEmpty()
    val fieldPath = telemetryEventSchemaDottedFieldPath(instanceLocation)
    val reason = formatValidationReason(sorted, instance)
    throw InvalidTelemetryEventSchemaError(
      fieldPath = fieldPath,
      eventName = resolvedEventName,
      reason = reason,
    )
  }

  fun assertIdentity(yamlText: String) {
    val yamlNode = YAMLMapper().readTree(yamlText)
    assertIdentity(yamlNode)
  }

  fun assertIdentity(yamlNode: JsonNode) {
    val loadedId = yamlNode.path("\$id").asText("")
    if (loadedId != EXPECTED_SCHEMA_ID) {
      throw InvalidTelemetryEventSchemaError(
        fieldPath = "\$id",
        eventName = null,
        reason = "Canonical telemetry-event schema identity mismatch: loaded '\$id' is '$loadedId' but " +
          "expected '$EXPECTED_SCHEMA_ID'. A stale or shadowed copy of the " +
          "schema is on the classpath.",
      )
    }
    val loadedConst = yamlNode.path("properties").path("contract_version").path("const").asText("")
    if (loadedConst != TELEMETRY_EVENT_CONTRACT_VERSION) {
      throw InvalidTelemetryEventSchemaError(
        fieldPath = "properties.contract_version.const",
        eventName = null,
        reason = "Canonical telemetry-event schema contract_version.const mismatch: loaded '$loadedConst' " +
          "but the runtime expects '$TELEMETRY_EVENT_CONTRACT_VERSION'. The schema on the classpath is out " +
          "of date relative to the running runtime-mcp.",
      )
    }
  }

  private fun formatValidationReason(sorted: List<ValidationMessage>, instance: JsonNode): String {
    val firstError = sorted.first()
    val instanceLocation = firstError.instanceLocation?.toString().orEmpty()
    val detail = firstError.message
    val offendingValue = extractOffendingValueFromTelemetryInstance(instance, instanceLocation)
    return buildString {
      append(detail)
      if (offendingValue.isNotBlank()) {
        append(" — offending value: ")
        append(offendingValue)
      }
      sorted.drop(1).forEach { other ->
        val otherLocation = other.instanceLocation?.toString().orEmpty()
        val otherPath = telemetryEventSchemaDottedFieldPath(otherLocation).ifBlank { "<root>" }
        val otherValue = extractOffendingValueFromTelemetryInstance(instance, otherLocation)
        append(" | ")
        append(otherPath)
        append(": ")
        append(other.message)
        if (otherValue.isNotBlank()) {
          append(" — offending value: ")
          append(otherValue)
        }
      }
    }
  }

  private val violationOrdering: Comparator<ValidationMessage> = compareBy(
    { it.instanceLocation?.toString().orEmpty().let { loc -> loc.isBlank() || loc == "$" || loc == "/" } },
    { it.instanceLocation?.toString().orEmpty() },
    { it.message.orEmpty() },
  )

  private fun validateCoherence(envelope: Map<String, Any?>, resolvedEventName: String?) {
    validateQualityCheckFailureCountCoherence(envelope, resolvedEventName)
    if (resolvedEventName != "skillbill_review_finished") return
    val platformSlug = envelope["platform_slug"] as? String
    val reviewPlatform = envelope["review_platform"] as? String
    val detectedStack = envelope["detected_stack"] as? String
    if (platformSlug == reviewPlatform && platformSlug == detectedStack) return
    throw InvalidTelemetryEventSchemaError(
      fieldPath = "platform_slug",
      eventName = resolvedEventName,
      reason = "skillbill_review_finished requires review_platform, detected_stack, and platform_slug to be equal " +
        "normalized slugs.",
    )
  }

  private fun validateQualityCheckFailureCountCoherence(envelope: Map<String, Any?>, resolvedEventName: String?) {
    if (resolvedEventName != McpToolPayloadKeys.QUALITY_CHECK_FINISHED) return
    if (!envelope.containsKey(LifecycleTelemetryPayloadKeys.FINAL_FAILURE_COUNT)) return
    if (envelope[LifecycleTelemetryPayloadKeys.FINAL_FAILURE_COUNT] != null) return
    if (envelope[LifecycleTelemetryPayloadKeys.COMPLETION] == LifecycleSessionCompletion.RECONCILER_STALE.wireValue) {
      return
    }
    throw InvalidTelemetryEventSchemaError(
      fieldPath = LifecycleTelemetryPayloadKeys.FINAL_FAILURE_COUNT,
      eventName = resolvedEventName,
      reason = "${McpToolPayloadKeys.QUALITY_CHECK_FINISHED} may omit final_failure_count only on a " +
        "${LifecycleSessionCompletion.RECONCILER_STALE.wireValue} terminal the runtime itself writes. A check " +
        "that reports its own terminal must carry the count it measured.",
    )
  }
}

private const val EXPECTED_SCHEMA_ID: String =
  "https://skill-bill.dev/contracts/telemetry-event-schema.yaml"
private const val SCHEMA_CLASSPATH_RESOURCE: String =
  "skillbill/mcp/contracts/telemetry-event-schema.yaml"
private const val SCHEMA_REPO_RELATIVE_PATH: String =
  "orchestration/contracts/telemetry-event-schema.yaml"

private fun loadSchemaDocument(): JsonNode {
  var failure: Throwable? = null
  try {
    val yamlText = readSchemaText()
    val yamlNode = YAMLMapper().readTree(yamlText)
    TelemetryEventSchemaValidator.assertIdentity(yamlNode)
    return yamlNode
  } catch (typed: InvalidTelemetryEventSchemaError) {
    failure = typed
  } catch (error: IOException) {
    failure = InvalidTelemetryEventSchemaError(
      fieldPath = "",
      eventName = null,
      reason = "Canonical telemetry-event schema document failed to load: ${error.message.orEmpty()}",
      cause = error,
    )
  } catch (error: JsonProcessingException) {
    failure = error.let {
      InvalidTelemetryEventSchemaError(
        fieldPath = "",
        eventName = null,
        reason = "Canonical telemetry-event schema document failed to load: ${error.message.orEmpty()}",
        cause = error,
      )
    }
  }
  throw failure
}

private fun compileSchema(yamlNode: JsonNode): JsonSchema {
  var failure: Throwable? = null
  try {
    val jsonText = ObjectMapper().writeValueAsString(yamlNode)
    val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
    return factory.getSchema(jsonText, LOCALE_STABLE_SCHEMA_CONFIG)
  } catch (typed: InvalidTelemetryEventSchemaError) {
    failure = typed
  } catch (error: IOException) {
    failure = InvalidTelemetryEventSchemaError(
      fieldPath = "",
      eventName = null,
      reason = "Canonical telemetry-event schema document failed to load: ${error.message.orEmpty()}",
      cause = error,
    )
  } catch (error: JsonProcessingException) {
    failure = error.let {
      InvalidTelemetryEventSchemaError(
        fieldPath = "",
        eventName = null,
        reason = "Canonical telemetry-event schema document failed to load: ${error.message.orEmpty()}",
        cause = error,
      )
    }
  }
  throw failure
}

private fun readSchemaText(): String {
  TelemetryEventSchemaValidator::class.java.classLoader
    .getResourceAsStream(SCHEMA_CLASSPATH_RESOURCE)
    ?.use { return it.readBytes().toString(Charsets.UTF_8) }

  throw InvalidTelemetryEventSchemaError(
    fieldPath = "",
    eventName = null,
    reason = "Canonical telemetry-event schema is missing from the runtime-mcp classpath at " +
      "'$SCHEMA_CLASSPATH_RESOURCE'. The on-disk source of truth lives at " +
      "'$SCHEMA_REPO_RELATIVE_PATH' — confirm `copyTelemetryEventSchema` ran during " +
      "`processResources` so the bytes were bundled into the runtime artifact.",
  )
}

internal fun extractOffendingValueFromTelemetryInstance(instance: JsonNode, instanceLocation: String): String {
  val dotted = telemetryEventSchemaDottedFieldPath(instanceLocation)
  if (dotted.isBlank()) return ""
  var node: JsonNode = instance
  dotted.split('.').forEach { rawSegment ->
    if (rawSegment.isBlank()) return@forEach
    val arrayMatch = Regex("^([^\\[]*)\\[(\\d+)]$").matchEntire(rawSegment)
    when {
      arrayMatch != null -> {
        val (keyPart, indexPart) = arrayMatch.destructured
        if (keyPart.isNotBlank()) {
          node = node.path(keyPart)
        }
        node = node.path(indexPart.toInt())
      }
      node.isArray && rawSegment.toIntOrNull() != null -> {
        node = node.path(rawSegment.toInt())
      }
      else -> {
        node = node.path(rawSegment)
      }
    }
  }
  return when {
    node.isMissingNode -> ""
    node.isValueNode -> node.asText()
    else -> ""
  }
}

internal fun telemetryEventSchemaDottedFieldPath(instanceLocation: String): String = when {
  instanceLocation.isBlank() || instanceLocation == "/" || instanceLocation == "$" -> ""
  instanceLocation.startsWith("$.") -> instanceLocation.removePrefix("$.")
  instanceLocation.startsWith("$") -> instanceLocation.removePrefix("$").trimStart('.')
  else -> instanceLocation.trimStart('/').replace('/', '.')
}
