package skillbill.infrastructure.fs.contracts.workflow

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import skillbill.contracts.logSchemaLoadFailure
import skillbill.contracts.workflow.WORKFLOW_STATE_CONTRACT_VERSION
import skillbill.contracts.workflow.WorkflowStateSchemaPaths
import skillbill.error.InvalidWorkflowStateSchemaError
import skillbill.infrastructure.fs.contracts.LOCALE_STABLE_SCHEMA_CONFIG
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Level
import java.util.logging.Logger

private val log: Logger = Logger.getLogger("skillbill.contracts.workflow.WorkflowStateSchemaValidator")

class WorkflowStateSchemaValidator {

  private val schema: JsonSchema by lazy { loadSchema() }
  private val mapper: ObjectMapper by lazy { ObjectMapper() }

  fun validate(parsedYaml: Map<String, Any?>, slug: String) {
    val instance: JsonNode = mapper.valueToTree(parsedYaml)
    val errors: Set<ValidationMessage> = schema.validate(instance)
    if (errors.isEmpty()) {
      return
    }

    log.log(Level.WARNING, buildWorkflowStateSchemaDriftLog(slug, errors, instance))
    throw InvalidWorkflowStateSchemaError(formatWorkflowStateValidationMessage(slug, errors, instance))
  }
}

internal const val WORKFLOW_STATE_SCHEMA_CLASSPATH_RESOURCE: String =
  WorkflowStateSchemaPaths.CLASSPATH_RESOURCE

internal const val WORKFLOW_STATE_SCHEMA_REPO_RELATIVE_PATH: String =
  WorkflowStateSchemaPaths.REPO_RELATIVE_PATH

private fun loadSchema(): JsonSchema {
  var failure: Throwable? = null
  try {
    val yamlText = readSchemaText()
    val yamlNode = YAMLMapper().readTree(yamlText)
    assertWorkflowStateSchemaIdentity(yamlNode)
    val jsonText = ObjectMapper().writeValueAsString(yamlNode)
    val factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
    return factory.getSchema(jsonText, LOCALE_STABLE_SCHEMA_CONFIG)
  } catch (error: InvalidWorkflowStateSchemaError) {
    logSchemaLoadFailure(
      log,
      "workflow-state",
      WORKFLOW_STATE_SCHEMA_CLASSPATH_RESOURCE,
      WORKFLOW_STATE_SCHEMA_REPO_RELATIVE_PATH,
      error,
    )
    failure = error
  } catch (error: IOException) {
    logSchemaLoadFailure(
      log,
      "workflow-state",
      WORKFLOW_STATE_SCHEMA_CLASSPATH_RESOURCE,
      WORKFLOW_STATE_SCHEMA_REPO_RELATIVE_PATH,
      error,
    )
    failure = error
  } catch (error: JsonProcessingException) {
    logSchemaLoadFailure(
      log,
      "workflow-state",
      WORKFLOW_STATE_SCHEMA_CLASSPATH_RESOURCE,
      WORKFLOW_STATE_SCHEMA_REPO_RELATIVE_PATH,
      error,
    )
    failure = error
  }
  throw failure
}

fun assertWorkflowStateSchemaIdentity(yamlNode: JsonNode) {
  val loadedId = yamlNode.path("\$id").asText("")
  if (loadedId != WorkflowStateSchemaPaths.EXPECTED_SCHEMA_ID) {
    throw InvalidWorkflowStateSchemaError(
      "Canonical workflow-state schema identity mismatch: loaded '\$id' is '$loadedId' but expected " +
        "'${WorkflowStateSchemaPaths.EXPECTED_SCHEMA_ID}'. A stale or shadowed copy of the schema is on " +
        "the classpath.",
    )
  }
  val loadedConst = yamlNode.path("properties").path("contract_version").path("const").asText("")
  if (loadedConst != WORKFLOW_STATE_CONTRACT_VERSION) {
    throw InvalidWorkflowStateSchemaError(
      "Canonical workflow-state schema contract_version.const mismatch: loaded '$loadedConst' but the " +
        "runtime expects '$WORKFLOW_STATE_CONTRACT_VERSION'. The schema on the classpath is out of date " +
        "relative to the running runtime-contracts.",
    )
  }
}

private fun readSchemaText(): String {
  WorkflowStateSchemaValidator::class.java.classLoader
    .getResourceAsStream(WORKFLOW_STATE_SCHEMA_CLASSPATH_RESOURCE)
    ?.use { return it.readBytes().toString(Charsets.UTF_8) }

  val walkAnchor: Path = Path.of("").toAbsolutePath()
  val resolved = walkForSchemaFile(walkAnchor)
  if (resolved != null) {
    return Files.readString(resolved)
  }
  throw InvalidWorkflowStateSchemaError(
    "Canonical workflow-state schema is missing. Expected to find it on the JVM classpath at " +
      "'$WORKFLOW_STATE_SCHEMA_CLASSPATH_RESOURCE' or on disk under " +
      "'$WORKFLOW_STATE_SCHEMA_REPO_RELATIVE_PATH' walked up from: $walkAnchor.",
  )
}

fun extractOffendingValueFromInstance(instance: JsonNode, instanceLocation: String): String {
  val dotted = workflowStateSchemaDottedFieldPath(instanceLocation)
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

fun workflowStateSchemaDottedFieldPath(instanceLocation: String): String = when {
  instanceLocation.isBlank() || instanceLocation == "/" || instanceLocation == "$" -> ""
  instanceLocation.startsWith("$.") -> instanceLocation.removePrefix("$.")
  instanceLocation.startsWith("$") -> instanceLocation.removePrefix("$").trimStart('.')
  else -> instanceLocation.trimStart('/').replace('/', '.')
}

private fun walkForSchemaFile(hint: Path): Path? {
  var current: Path? = hint.toAbsolutePath().normalize()
  while (current != null) {
    val candidate = current.resolve(WORKFLOW_STATE_SCHEMA_REPO_RELATIVE_PATH)
    if (Files.isRegularFile(candidate)) {
      return candidate
    }
    current = current.parent
  }
  return null
}
