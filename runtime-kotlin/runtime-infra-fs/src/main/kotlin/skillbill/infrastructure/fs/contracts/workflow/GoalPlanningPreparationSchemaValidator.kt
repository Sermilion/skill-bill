package skillbill.infrastructure.fs.contracts.workflow

import com.fasterxml.jackson.databind.JsonNode
import com.networknt.schema.JsonSchema
import com.networknt.schema.ValidationMessage
import skillbill.contracts.logSchemaLoadFailure
import skillbill.contracts.workflow.GOAL_PLANNING_PREPARATION_CONTRACT_VERSION
import skillbill.contracts.workflow.GoalPlanningPreparationSchemaPaths
import skillbill.error.InvalidGoalPlanningPreparationSchemaError
import skillbill.infrastructure.fs.contracts.ClasspathContractSchemaLoader
import skillbill.infrastructure.fs.contracts.CompiledSchemaRequest
import java.util.logging.Level
import java.util.logging.Logger

private val goalPlanningPreparationLog: Logger =
  Logger.getLogger("skillbill.contracts.workflow.GoalPlanningPreparationSchemaValidator")

object GoalPlanningPreparationSchemaValidator {
  fun validate(envelope: Map<String, Any?>, sourceLabel: String) {
    val instance: JsonNode = ClasspathContractSchemaLoader.valueToTree(envelope)
    val errors: Set<ValidationMessage> =
      ClasspathContractSchemaLoader.validate(goalPlanningPreparationSchema(), instance)
    if (errors.isNotEmpty()) {
      val sorted = errors.sortedWith(violationOrdering)
      val fieldPath = dottedFieldPath(sorted.first().instanceLocation?.toString().orEmpty())
      val validationReason = formatValidationReason(sorted, instance)
      val reason = if (fieldPath == "provenance.phase_output_contract_version") {
        "$validationReason Existing workflow state is incompatible; hard-reset it with " +
          "'skill-bill goal reset <issue-key> --hard --yes'."
      } else {
        validationReason
      }
      goalPlanningPreparationLog.log(Level.WARNING, buildSchemaDriftLog(sourceLabel, sorted, instance))
      throw InvalidGoalPlanningPreparationSchemaError(
        sourceLabel = sourceLabel,
        fieldPath = fieldPath,
        reason = reason,
      )
    }
  }

  private fun buildSchemaDriftLog(sourceLabel: String, sorted: List<ValidationMessage>, instance: JsonNode): String {
    val parts = sorted.take(2).map { error ->
      val location = error.instanceLocation?.toString().orEmpty()
      val fieldPath = dottedFieldPath(location).ifBlank { "<root>" }
      val offendingValue = offendingValue(instance, location)
      if (offendingValue.isNotBlank()) "$fieldPath=$offendingValue" else fieldPath
    }
    return "Goal planning preparation failed schema validation: source='$sourceLabel' " +
      "violations=${parts.joinToString(", ")} totalViolations=${sorted.size}"
  }

  private fun formatValidationReason(sorted: List<ValidationMessage>, instance: JsonNode): String {
    val firstError = sorted.first()
    val instanceLocation = firstError.instanceLocation?.toString().orEmpty()
    val offendingValue = offendingValue(instance, instanceLocation)
    return buildString {
      append(firstError.message)
      if (offendingValue.isNotBlank()) {
        append(" — offending value: ")
        append(offendingValue)
      }
      sorted.drop(1).forEach { other ->
        val otherLocation = other.instanceLocation?.toString().orEmpty()
        val otherValue = offendingValue(instance, otherLocation)
        append(" | ")
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

  private fun offendingValue(instance: JsonNode, instanceLocation: String): String {
    val dotted = dottedFieldPath(instanceLocation)
    if (dotted.isBlank()) return ""
    var node: JsonNode = instance
    dotted.split('.').forEach { segment ->
      if (segment.isBlank()) return@forEach
      node = node.path(segment)
    }
    return when {
      node.isMissingNode -> ""
      node.isValueNode -> node.asText()
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

internal const val GOAL_PLANNING_PREPARATION_SCHEMA_CLASSPATH_RESOURCE: String =
  GoalPlanningPreparationSchemaPaths.CLASSPATH_RESOURCE

internal const val GOAL_PLANNING_PREPARATION_SCHEMA_REPO_RELATIVE_PATH: String =
  GoalPlanningPreparationSchemaPaths.REPO_RELATIVE_PATH

private fun goalPlanningPreparationSchema(): JsonSchema = ClasspathContractSchemaLoader.compiledSchema(
  CompiledSchemaRequest(
    cacheKey = GOAL_PLANNING_PREPARATION_SCHEMA_CLASSPATH_RESOURCE,
    classLoader = GoalPlanningPreparationSchemaValidator::class.java.classLoader,
    classpathResource = GOAL_PLANNING_PREPARATION_SCHEMA_CLASSPATH_RESOURCE,
    missingResource = {
      InvalidGoalPlanningPreparationSchemaError(
        sourceLabel = GOAL_PLANNING_PREPARATION_SCHEMA_CLASSPATH_RESOURCE,
        fieldPath = "",
        reason = "Canonical goal planning preparation schema is missing. Expected classpath resource " +
          "'$GOAL_PLANNING_PREPARATION_SCHEMA_CLASSPATH_RESOURCE'.",
      )
    },
    processingFailure = { cause ->
      InvalidGoalPlanningPreparationSchemaError(
        sourceLabel = GOAL_PLANNING_PREPARATION_SCHEMA_CLASSPATH_RESOURCE,
        fieldPath = "",
        reason = cause.message ?: cause::class.simpleName.orEmpty(),
        cause = cause,
      )
    },
    loadFailureLogger = { error ->
      logSchemaLoadFailure(
        goalPlanningPreparationLog,
        "goal planning preparation",
        GOAL_PLANNING_PREPARATION_SCHEMA_CLASSPATH_RESOURCE,
        GOAL_PLANNING_PREPARATION_SCHEMA_REPO_RELATIVE_PATH,
        error,
      )
    },
    expectedSchemaId = GoalPlanningPreparationSchemaPaths.EXPECTED_SCHEMA_ID,
    expectedContractVersion = GOAL_PLANNING_PREPARATION_CONTRACT_VERSION,
    identityFailure = { reason ->
      InvalidGoalPlanningPreparationSchemaError(
        sourceLabel = GOAL_PLANNING_PREPARATION_SCHEMA_CLASSPATH_RESOURCE,
        fieldPath = "<schema>",
        reason = reason,
      )
    },
    prepareSchemaDocument = { yamlNode ->
      yamlNode.inlineIssueKeySchemaRefs()
    },
  ),
)
