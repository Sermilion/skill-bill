package skillbill.infrastructure.fs.scaffold.platformpack

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import skillbill.error.ContractVersionMismatchError
import skillbill.error.InvalidManifestSchemaError
import skillbill.infrastructure.fs.contracts.LOCALE_STABLE_SCHEMA_CONFIG
import skillbill.infrastructure.fs.scaffold.runtime.SHELL_CONTRACT_VERSION
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Logger

internal val platformPackSchemaLog: Logger =
  Logger.getLogger("skillbill.scaffold.platformpack.PlatformPackSchemaValidator")

internal class PlatformPackSchemaValidator {

  private val schema: JsonSchema by lazy { loadSchema() }
  private val mapper: ObjectMapper by lazy { ObjectMapper() }

  fun validate(parsedYaml: Map<String, Any?>, slug: String, enforceContractVersion: Boolean = true) {
    val instance: JsonNode = mapper.valueToTree(parsedYaml)
    val errors: Set<ValidationMessage> = schema.validate(instance)

    val contractVersionConst = errors.firstOrNull { it.isContractVersionConstMismatch() }
    if (contractVersionConst != null && enforceContractVersion) {
      throw ContractVersionMismatchError(
        buildContractVersionMismatchMessage(slug, instance, contractVersionConst),
      )
    }
    val remainingErrors = if (contractVersionConst != null) {
      recordToleratedContractVersion(slug, instance, contractVersionConst)
      errors - contractVersionConst
    } else {
      errors
    }
    if (remainingErrors.isEmpty()) {
      return
    }
    throw InvalidManifestSchemaError(formatValidationMessage(slug, remainingErrors, instance))
  }

  private fun recordToleratedContractVersion(slug: String, instance: JsonNode, error: ValidationMessage) {
    val actual = extractOffendingValue(instance, error.instanceLocation?.toString().orEmpty())
    platformPackSchemaLog.warning(
      "platform pack contract_version enforcement degraded: " +
        "seam=PlatformPackSchemaValidator.validate pack=$slug " +
        "used=${actual.ifBlank { "<unreadable>" }} expected=$SHELL_CONTRACT_VERSION " +
        "cause=caller enumerated with enforceContractVersion=false, so a preserved local " +
        "manifest pinned to an unsupported contract stays enumerable and is replaced by " +
        "upstream on adopt",
    )
  }

  private fun ValidationMessage.isContractVersionConstMismatch(): Boolean {
    val dotted = dottedFieldPath(instanceLocation?.toString().orEmpty())
    return dotted == "contract_version" && type == "const"
  }

  private fun buildContractVersionMismatchMessage(slug: String, instance: JsonNode, error: ValidationMessage): String {
    val actual = extractOffendingValue(instance, error.instanceLocation?.toString().orEmpty())
    return buildString {
      append("Platform pack '")
      append(slug)
      append("': declares contract_version")
      if (actual.isNotBlank()) {
        append(" '")
        append(actual)
        append("'")
      }
      append(" but the shell expects '")
      append(SHELL_CONTRACT_VERSION)
      append("'.")
    }
  }

  private fun formatValidationMessage(slug: String, errors: Set<ValidationMessage>, instance: JsonNode): String {
    val sorted = errors.sortedBy { it.instanceLocation?.toString().orEmpty() }
    val firstError = sorted.first()
    val instanceLocation = firstError.instanceLocation?.toString().orEmpty()
    val fieldPath = dottedFieldPath(instanceLocation)
    val detail = firstError.message
    val hint = humanReadableHintFor(firstError, fieldPath)
    val offendingValue = extractOffendingValue(instance, instanceLocation)
    val others = if (errors.size > 1) " (+ ${errors.size - 1} more)" else ""
    return buildString {
      append("Platform pack '")
      append(slug)
      append("': manifest fails schema validation at '")
      append(fieldPath.ifBlank { "<root>" })
      append("': ")
      append(detail)
      if (offendingValue.isNotBlank()) {
        append(" — offending value: ")
        append(offendingValue)
      }
      if (hint.isNotBlank()) {
        append(" — ")
        append(hint)
      }
      append(others)
    }
  }

  private fun extractOffendingValue(instance: JsonNode, instanceLocation: String): String {
    val dotted = dottedFieldPath(instanceLocation)
    if (dotted.isBlank()) return ""
    var node: JsonNode = instance

    dotted.split('.').forEach { rawSegment ->
      if (rawSegment.isBlank()) return@forEach
      val arrayMatch = Regex("^([^\\[]*)\\[(\\d+)]$").matchEntire(rawSegment)
      if (arrayMatch != null) {
        val (keyPart, indexPart) = arrayMatch.destructured
        if (keyPart.isNotBlank()) {
          node = node.path(keyPart)
        }
        node = node.path(indexPart.toInt())
      } else {
        node = node.path(rawSegment)
      }
    }
    return when {
      node.isMissingNode -> ""
      node.isValueNode -> node.asText()
      else -> ""
    }
  }

  private fun humanReadableHintFor(error: ValidationMessage, fieldPath: String): String {
    val keyword = error.type.orEmpty()
    val isPointerName = fieldPath.startsWith("pointers") && fieldPath.endsWith(".name")
    val isPointerTarget = fieldPath.startsWith("pointers") && fieldPath.endsWith(".target")
    return when {
      keyword == "pattern" && fieldPath.startsWith("declared_files") ->
        "schema requires this field to point directly at 'content.md'."
      keyword == "pattern" && fieldPath == "declared_quality_check_file" ->
        "schema requires this field to point directly at 'content.md'."
      keyword == "pattern" && isPointerName ->
        "schema requires pointer 'name' to be a bare '.md' filename (no path separators)."
      keyword == "minLength" && isPointerTarget ->
        "schema requires a non-empty 'target' on every pointer entry."
      keyword == "not" && isPointerName ->
        "schema forbids '..' in pointer 'name'."
      else -> ""
    }
  }

  private fun dottedFieldPath(instanceLocation: String): String = when {
    instanceLocation.isBlank() || instanceLocation == "/" || instanceLocation == "$" -> ""
    instanceLocation.startsWith("$.") -> instanceLocation.removePrefix("$.")
    instanceLocation.startsWith("$") -> instanceLocation.removePrefix("$").trimStart('.')

    else -> instanceLocation.trimStart('/').replace('/', '.')
  }
}

object PlatformPackSchemaPaths {

  const val REPO_RELATIVE_PATH: String =
    "orchestration/contracts/platform-pack-schema.yaml"

  const val CLASSPATH_RESOURCE: String =
    "skillbill/infrastructure/fs/contracts/platform-pack-schema.yaml"

  const val EXPECTED_SCHEMA_ID: String =
    "https://skill-bill.dev/contracts/platform-pack-schema.yaml"
}

internal const val PLATFORM_PACK_SCHEMA_CLASSPATH_RESOURCE: String =
  PlatformPackSchemaPaths.CLASSPATH_RESOURCE

internal const val PLATFORM_PACK_SCHEMA_REPO_RELATIVE_PATH: String =
  PlatformPackSchemaPaths.REPO_RELATIVE_PATH

private fun loadSchema(): JsonSchema {
  val yamlText = readSchemaText()
  val yamlNode = YAMLMapper().readTree(yamlText)
  assertSchemaIdentity(yamlNode)
  val jsonText = ObjectMapper().writeValueAsString(yamlNode)
  val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
  return factory.getSchema(jsonText, LOCALE_STABLE_SCHEMA_CONFIG)
}

internal fun anchoredTopLevelFieldNames(): Set<String> = ANCHORED_TOP_LEVEL_FIELD_NAMES

private val ANCHORED_TOP_LEVEL_FIELD_NAMES: Set<String> by lazy {
  val yamlText = readSchemaText()
  val yamlNode: JsonNode = YAMLMapper().readTree(yamlText)
  val properties = yamlNode.path("properties")
  if (properties.isMissingNode || !properties.isObject) {
    throw InvalidManifestSchemaError(
      "Canonical platform-pack schema is missing a top-level 'properties' object; cannot derive " +
        "the anchored top-level field set.",
    )
  }
  val anchored = linkedSetOf<String>()
  properties.fields().forEach { (name, definition) ->
    if (definition.path("x-runtime-anchored").asBoolean(false)) {
      anchored += name
    }
  }
  anchored
}

internal fun assertSchemaIdentity(yamlNode: JsonNode) {
  val loadedId = yamlNode.path("\$id").asText("")
  if (loadedId != PlatformPackSchemaPaths.EXPECTED_SCHEMA_ID) {
    throw InvalidManifestSchemaError(
      "Canonical platform-pack schema identity mismatch: loaded '\$id' is '$loadedId' but expected " +
        "'${PlatformPackSchemaPaths.EXPECTED_SCHEMA_ID}'. A stale or shadowed copy of the schema is on " +
        "the classpath.",
    )
  }
  val loadedConst = yamlNode.path("properties").path("contract_version").path("const").asText("")
  if (loadedConst != SHELL_CONTRACT_VERSION) {
    throw InvalidManifestSchemaError(
      "Canonical platform-pack schema contract_version.const mismatch: loaded '$loadedConst' but the " +
        "shell expects '$SHELL_CONTRACT_VERSION'. The schema on the classpath is out of date relative to " +
        "the running runtime-core.",
    )
  }
}

private fun readSchemaText(): String {
  PlatformPackSchemaValidator::class.java.classLoader
    .getResourceAsStream(PLATFORM_PACK_SCHEMA_CLASSPATH_RESOURCE)
    ?.use { return it.readBytes().toString(Charsets.UTF_8) }

  val walkAnchor: Path = Path.of("").toAbsolutePath()
  val resolved = walkForSchemaFile(walkAnchor)
  if (resolved != null) {
    return Files.readString(resolved)
  }
  throw InvalidManifestSchemaError(
    "Canonical platform-pack schema is missing. Expected to find it on the JVM classpath at " +
      "'$PLATFORM_PACK_SCHEMA_CLASSPATH_RESOURCE' or on disk under " +
      "'$PLATFORM_PACK_SCHEMA_REPO_RELATIVE_PATH' walked up from: $walkAnchor.",
  )
}

private fun walkForSchemaFile(hint: Path): Path? {
  var current: Path? = hint.toAbsolutePath().normalize()
  while (current != null) {
    val candidate = current.resolve(PLATFORM_PACK_SCHEMA_REPO_RELATIVE_PATH)
    if (Files.isRegularFile(candidate)) {
      return candidate
    }
    current = current.parent
  }
  return null
}
