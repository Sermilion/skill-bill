package skillbill.infrastructure.fs.contracts.workflow

import com.fasterxml.jackson.databind.JsonNode
import com.networknt.schema.JsonSchema
import com.networknt.schema.ValidationMessage
import skillbill.contracts.workflow.FEATURE_TASK_RUNTIME_PLANNING_PROJECTIONS_CONTRACT_VERSION
import skillbill.contracts.workflow.FeatureTaskRuntimePlanningProjectionsSchemaPaths
import skillbill.error.InvalidFeatureTaskRuntimePlanningProjectionSchemaError
import skillbill.infrastructure.fs.contracts.ClasspathContractSchemaLoader
import skillbill.infrastructure.fs.contracts.CompiledSchemaRequest

object FeatureTaskRuntimePlanningProjectionSchemaValidator {
  fun validate(payload: Map<String, Any?>, sourceLabel: String) {
    val instance: JsonNode = ClasspathContractSchemaLoader.valueToTree(payload)

    val effective = schema()
    val errors: Set<ValidationMessage> = ClasspathContractSchemaLoader.validate(effective, instance)
    if (errors.isNotEmpty()) {
      throw InvalidFeatureTaskRuntimePlanningProjectionSchemaError(
        sourceLabel = sourceLabel,
        reason = formatReason(errors),
      )
    }
  }

  private fun formatReason(errors: Set<ValidationMessage>): String = errors.sortedBy { instanceLocationOf(it) }
    .take(MAX_REPORTED_VIOLATIONS)
    .joinToString(separator = " | ") { locatedMessage(it) } +
    if (errors.size > MAX_REPORTED_VIOLATIONS) " (+${errors.size - MAX_REPORTED_VIOLATIONS} more)" else ""

  private fun locatedMessage(error: ValidationMessage): String {
    val message = error.message.orEmpty()
    if (instanceLocationOf(error).isNotBlank()) return message
    return "$ROOT_INSTANCE_LOCATION: ${message.removePrefix(":").trim()}"
  }

  private fun instanceLocationOf(error: ValidationMessage): String = error.instanceLocation?.toString().orEmpty()

  private const val MAX_REPORTED_VIOLATIONS: Int = 3

  private const val ROOT_INSTANCE_LOCATION: String = "<root>"
}

private fun schema(): JsonSchema = ClasspathContractSchemaLoader.compiledSchema(
  CompiledSchemaRequest(
    cacheKey = FeatureTaskRuntimePlanningProjectionsSchemaPaths.CLASSPATH_RESOURCE,
    classLoader = FeatureTaskRuntimePlanningProjectionSchemaValidator::class.java.classLoader,
    classpathResource = FeatureTaskRuntimePlanningProjectionsSchemaPaths.CLASSPATH_RESOURCE,
    missingResource = {
      InvalidFeatureTaskRuntimePlanningProjectionSchemaError(
        sourceLabel = FeatureTaskRuntimePlanningProjectionsSchemaPaths.CLASSPATH_RESOURCE,
        reason = "Canonical feature-task-runtime planning-projections schema is missing. Expected classpath resource " +
          "'${FeatureTaskRuntimePlanningProjectionsSchemaPaths.CLASSPATH_RESOURCE}'.",
      )
    },
    processingFailure = { cause ->
      InvalidFeatureTaskRuntimePlanningProjectionSchemaError(
        sourceLabel = FeatureTaskRuntimePlanningProjectionsSchemaPaths.CLASSPATH_RESOURCE,
        reason = cause.message ?: cause::class.simpleName.orEmpty(),
        cause = cause,
      )
    },
    loadFailureLogger = {},
    expectedSchemaId = FeatureTaskRuntimePlanningProjectionsSchemaPaths.EXPECTED_SCHEMA_ID,
    expectedContractVersion = FEATURE_TASK_RUNTIME_PLANNING_PROJECTIONS_CONTRACT_VERSION,
    contractVersionPath = listOf("\$defs", "contractVersion", "const"),
    identityFailure = { reason ->
      InvalidFeatureTaskRuntimePlanningProjectionSchemaError(
        sourceLabel = FeatureTaskRuntimePlanningProjectionsSchemaPaths.CLASSPATH_RESOURCE,
        reason = reason,
      )
    },
  ),
)
