package skillbill.infrastructure.fs.contracts.install

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import skillbill.contracts.install.INSTALL_PLAN_CONTRACT_VERSION
import skillbill.contracts.install.InstallPlanSchemaPaths
import skillbill.contracts.logSchemaLoadFailure
import skillbill.error.InvalidInstallPlanSchemaError
import skillbill.infrastructure.fs.contracts.LOCALE_STABLE_SCHEMA_CONFIG
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Level
import java.util.logging.Logger

private val log: Logger = Logger.getLogger("skillbill.contracts.install.InstallPlanSchemaValidator")

object InstallPlanSchemaValidator {
  private val schema: JsonSchema by lazy { loadSchema() }
  private val mapper: ObjectMapper by lazy { ObjectMapper() }

  fun validate(plan: Map<String, Any?>) {
    val instance: JsonNode = mapper.valueToTree(plan)
    val errors: Set<ValidationMessage> = schema.validate(instance)
    if (errors.isEmpty()) {
      return
    }

    log.log(Level.WARNING, buildSchemaDriftLog(errors, instance))
    val sorted = errors.sortedWith(violationOrdering)
    val firstError = sorted.first()
    val instanceLocation = firstError.instanceLocation?.toString().orEmpty()
    val fieldPath = installPlanSchemaDottedFieldPath(instanceLocation)
    val reason = formatValidationReason(sorted, instance)
    throw InvalidInstallPlanSchemaError(fieldPath = fieldPath, reason = reason)
  }

  fun assertIdentity(yamlText: String) {
    val yamlNode = YAMLMapper().readTree(yamlText)
    assertIdentity(yamlNode)
  }

  fun assertIdentity(yamlNode: JsonNode) {
    val loadedId = yamlNode.path("\$id").asText("")
    if (loadedId != InstallPlanSchemaPaths.EXPECTED_SCHEMA_ID) {
      throw InvalidInstallPlanSchemaError(
        fieldPath = "\$id",
        reason = "Canonical install-plan schema identity mismatch: loaded '\$id' is '$loadedId' but " +
          "expected '${InstallPlanSchemaPaths.EXPECTED_SCHEMA_ID}'. A stale or shadowed copy of the " +
          "schema is on the classpath.",
      )
    }
    val loadedConst = yamlNode.path("properties").path("contract_version").path("const").asText("")
    if (loadedConst != INSTALL_PLAN_CONTRACT_VERSION) {
      throw InvalidInstallPlanSchemaError(
        fieldPath = "properties.contract_version.const",
        reason = "Canonical install-plan schema contract_version.const mismatch: loaded '$loadedConst' " +
          "but the runtime expects '$INSTALL_PLAN_CONTRACT_VERSION'. The schema on the classpath is out " +
          "of date relative to the running runtime-contracts.",
      )
    }
  }

  private fun buildSchemaDriftLog(errors: Set<ValidationMessage>, instance: JsonNode): String {
    val sorted = errors.sortedWith(violationOrdering)
    val topTwo = sorted.take(2)
    val parts = topTwo.map { error ->
      val location = error.instanceLocation?.toString().orEmpty()
      val fieldPath = installPlanSchemaDottedFieldPath(location).ifBlank { "<root>" }
      val offendingValue = extractOffendingValueFromInstance(instance, location)
      if (offendingValue.isNotBlank()) "$fieldPath=$offendingValue" else fieldPath
    }
    return "Install plan failed schema validation: violations=${parts.joinToString(", ")} " +
      "totalViolations=${errors.size}"
  }

  private fun formatValidationReason(sorted: List<ValidationMessage>, instance: JsonNode): String {
    val firstError = sorted.first()
    val instanceLocation = firstError.instanceLocation?.toString().orEmpty()
    val detail = firstError.message
    val offendingValue = extractOffendingValueFromInstance(instance, instanceLocation)
    return buildString {
      append(detail)
      if (offendingValue.isNotBlank()) {
        append(" — offending value: ")
        append(offendingValue)
      }
      sorted.drop(1).forEach { other ->
        val otherLocation = other.instanceLocation?.toString().orEmpty()
        val otherPath = installPlanSchemaDottedFieldPath(otherLocation).ifBlank { "<root>" }
        val otherValue = extractOffendingValueFromInstance(instance, otherLocation)
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
}

internal const val INSTALL_PLAN_SCHEMA_CLASSPATH_RESOURCE: String =
  InstallPlanSchemaPaths.CLASSPATH_RESOURCE

internal const val INSTALL_PLAN_SCHEMA_REPO_RELATIVE_PATH: String =
  InstallPlanSchemaPaths.REPO_RELATIVE_PATH

private fun loadSchema(): JsonSchema {
  var failure: Throwable? = null
  try {
    val yamlText = readSchemaText()
    val yamlNode = YAMLMapper().readTree(yamlText)
    InstallPlanSchemaValidator.assertIdentity(yamlNode)
    val jsonText = ObjectMapper().writeValueAsString(yamlNode)
    val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
    return factory.getSchema(jsonText, LOCALE_STABLE_SCHEMA_CONFIG)
  } catch (error: InvalidInstallPlanSchemaError) {
    logSchemaLoadFailure(
      log,
      "install-plan",
      INSTALL_PLAN_SCHEMA_CLASSPATH_RESOURCE,
      INSTALL_PLAN_SCHEMA_REPO_RELATIVE_PATH,
      error,
    )
    failure = error
  } catch (error: IOException) {
    logSchemaLoadFailure(
      log,
      "install-plan",
      INSTALL_PLAN_SCHEMA_CLASSPATH_RESOURCE,
      INSTALL_PLAN_SCHEMA_REPO_RELATIVE_PATH,
      error,
    )
    failure = error
  } catch (error: JsonProcessingException) {
    logSchemaLoadFailure(
      log,
      "install-plan",
      INSTALL_PLAN_SCHEMA_CLASSPATH_RESOURCE,
      INSTALL_PLAN_SCHEMA_REPO_RELATIVE_PATH,
      error,
    )
    failure = error
  }
  throw failure
}

private fun readSchemaText(): String {
  InstallPlanSchemaValidator::class.java.classLoader
    .getResourceAsStream(INSTALL_PLAN_SCHEMA_CLASSPATH_RESOURCE)
    ?.use { return it.readBytes().toString(Charsets.UTF_8) }

  val walkAnchor: Path = Path.of("").toAbsolutePath()
  val resolved = walkForSchemaFile(walkAnchor)
  if (resolved != null) {
    return Files.readString(resolved)
  }
  throw InvalidInstallPlanSchemaError(
    fieldPath = "",
    reason = "Canonical install-plan schema is missing. Expected to find it on the JVM classpath at " +
      "'$INSTALL_PLAN_SCHEMA_CLASSPATH_RESOURCE' or on disk under " +
      "'$INSTALL_PLAN_SCHEMA_REPO_RELATIVE_PATH' walked up from: $walkAnchor.",
  )
}

fun extractOffendingValueFromInstance(instance: JsonNode, instanceLocation: String): String {
  val dotted = installPlanSchemaDottedFieldPath(instanceLocation)
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

fun installPlanSchemaDottedFieldPath(instanceLocation: String): String = when {
  instanceLocation.isBlank() || instanceLocation == "/" || instanceLocation == "$" -> ""
  instanceLocation.startsWith("$.") -> instanceLocation.removePrefix("$.")
  instanceLocation.startsWith("$") -> instanceLocation.removePrefix("$").trimStart('.')
  else -> instanceLocation.trimStart('/').replace('/', '.')
}

private fun walkForSchemaFile(hint: Path): Path? {
  var current: Path? = hint.toAbsolutePath().normalize()
  while (current != null) {
    val candidate = current.resolve(INSTALL_PLAN_SCHEMA_REPO_RELATIVE_PATH)
    if (Files.isRegularFile(candidate)) {
      return candidate
    }
    current = current.parent
  }
  return null
}
