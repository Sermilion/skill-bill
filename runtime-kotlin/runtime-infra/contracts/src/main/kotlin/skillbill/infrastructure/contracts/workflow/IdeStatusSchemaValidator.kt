package skillbill.infrastructure.contracts.workflow

import com.fasterxml.jackson.databind.JsonNode
import com.networknt.schema.JsonSchema
import com.networknt.schema.ValidationMessage
import me.tatarka.inject.annotations.Inject
import skillbill.contracts.logSchemaLoadFailure
import skillbill.contracts.workflow.IDE_STATUS_CONTRACT_VERSION
import skillbill.contracts.workflow.IdeStatusSchemaPaths
import skillbill.error.InvalidIdeStatusSchemaError
import skillbill.infrastructure.contracts.ClasspathContractSchemaLoader
import skillbill.infrastructure.contracts.CompiledSchemaRequest
import skillbill.ports.idestatus.IdeStatusValidator
import skillbill.ports.idestatus.IdeStatusWireMap
import java.util.logging.Level
import java.util.logging.Logger

private val ideStatusLog: Logger =
  Logger.getLogger("skillbill.contracts.workflow.IdeStatusSchemaValidator")

@Inject
class IdeStatusSchemaValidator : IdeStatusValidator {
  override fun validate(snapshot: IdeStatusWireMap, sourceLabel: String) {
    validate(snapshot as Map<String, Any?>, sourceLabel)
  }

  fun validate(snapshot: Map<String, Any?>, sourceLabel: String) {
    val instance: JsonNode = ClasspathContractSchemaLoader.valueToTree(snapshot)
    val errors = ClasspathContractSchemaLoader.validate(ideStatusSchema(), instance)
    if (errors.isEmpty()) return
    ideStatusLog.log(Level.WARNING, buildSchemaDriftLog(sourceLabel, errors, instance))
    val sortedErrors = errors.sortedWith(violationOrdering)
    throw InvalidIdeStatusSchemaError(
      sourceLabel = sourceLabel,
      fieldPath = goalObservabilityDottedFieldPath(
        sortedErrors.first().instanceLocation?.toString().orEmpty(),
      ),
      reason = formatValidationReason(sortedErrors, instance),
    )
  }

  companion object {
    private val canonical: IdeStatusSchemaValidator by lazy(::IdeStatusSchemaValidator)

    fun validate(snapshot: Map<String, Any?>, sourceLabel: String) = canonical.validate(snapshot, sourceLabel)
  }

  private fun buildSchemaDriftLog(sourceLabel: String, errors: Set<ValidationMessage>, instance: JsonNode): String {
    val parts = errors.sortedWith(violationOrdering).take(2).map { error ->
      val location = error.instanceLocation?.toString().orEmpty()
      val fieldPath = goalObservabilityDottedFieldPath(location).ifBlank { "<root>" }
      val offendingValue = extractGoalObservabilityOffendingValue(instance, location)
      if (offendingValue.isNotBlank()) "$fieldPath=$offendingValue" else fieldPath
    }
    return "IDE status failed schema validation: source='$sourceLabel' " +
      "violations=${parts.joinToString(", ")} totalViolations=${errors.size}"
  }

  private fun formatValidationReason(sorted: List<ValidationMessage>, instance: JsonNode): String =
    sorted.joinToString(" | ") { error ->
      val instanceLocation = error.instanceLocation?.toString().orEmpty()
      val fieldPath = goalObservabilityDottedFieldPath(instanceLocation).ifBlank { "<root>" }
      val offendingValue = extractGoalObservabilityOffendingValue(instance, instanceLocation)
      buildString {
        append(fieldPath)
        append(": ")
        append(error.message)
        if (offendingValue.isNotBlank()) {
          append(" — offending value: ")
          append(offendingValue)
        }
      }
    }

  private val violationOrdering: Comparator<ValidationMessage> = compareBy(
    { it.instanceLocation?.toString().orEmpty().let { loc -> loc.isBlank() || loc == "$" || loc == "/" } },
    { it.instanceLocation?.toString().orEmpty() },
    { it.message.orEmpty() },
  )
}

internal const val IDE_STATUS_SCHEMA_CLASSPATH_RESOURCE: String =
  IdeStatusSchemaPaths.CLASSPATH_RESOURCE

internal const val IDE_STATUS_SCHEMA_REPO_RELATIVE_PATH: String =
  IdeStatusSchemaPaths.REPO_RELATIVE_PATH

private fun ideStatusSchema(): JsonSchema = ClasspathContractSchemaLoader.compiledSchema(
  CompiledSchemaRequest(
    cacheKey = IDE_STATUS_SCHEMA_CLASSPATH_RESOURCE,
    classLoader = IdeStatusSchemaValidator::class.java.classLoader,
    classpathResource = IDE_STATUS_SCHEMA_CLASSPATH_RESOURCE,
    missingResource = {
      InvalidIdeStatusSchemaError(
        sourceLabel = IDE_STATUS_SCHEMA_CLASSPATH_RESOURCE,
        fieldPath = "",
        reason = "Canonical IDE status schema is missing. Expected classpath resource " +
          "'$IDE_STATUS_SCHEMA_CLASSPATH_RESOURCE'.",
      )
    },
    processingFailure = { cause ->
      InvalidIdeStatusSchemaError(
        sourceLabel = IDE_STATUS_SCHEMA_CLASSPATH_RESOURCE,
        fieldPath = "",
        reason = cause.message ?: cause::class.simpleName.orEmpty(),
        cause = cause,
      )
    },
    loadFailureLogger = { error ->
      logSchemaLoadFailure(
        ideStatusLog,
        "IDE status",
        IDE_STATUS_SCHEMA_CLASSPATH_RESOURCE,
        IDE_STATUS_SCHEMA_REPO_RELATIVE_PATH,
        error,
      )
    },
    expectedSchemaId = IdeStatusSchemaPaths.EXPECTED_SCHEMA_ID,
    expectedContractVersion = IDE_STATUS_CONTRACT_VERSION,
    identityFailure = { reason ->
      InvalidIdeStatusSchemaError(
        sourceLabel = IDE_STATUS_SCHEMA_CLASSPATH_RESOURCE,
        fieldPath = "<schema>",
        reason = reason,
      )
    },
  ),
)
