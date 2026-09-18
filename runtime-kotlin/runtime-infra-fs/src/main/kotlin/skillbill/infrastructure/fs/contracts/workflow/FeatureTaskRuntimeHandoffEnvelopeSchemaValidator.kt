package skillbill.infrastructure.fs.contracts.workflow

import com.fasterxml.jackson.databind.JsonNode
import com.networknt.schema.JsonSchema
import com.networknt.schema.ValidationMessage
import skillbill.contracts.workflow.FEATURE_TASK_RUNTIME_HANDOFF_ENVELOPE_CONTRACT_VERSION
import skillbill.contracts.workflow.FeatureTaskRuntimeHandoffEnvelopeSchemaPaths
import skillbill.error.FeatureTaskRuntimeHandoffProjectionFailureKind
import skillbill.error.InvalidFeatureTaskRuntimeHandoffProjectionContext
import skillbill.error.InvalidFeatureTaskRuntimeHandoffProjectionError
import skillbill.infrastructure.fs.contracts.ClasspathContractSchemaLoader
import skillbill.infrastructure.fs.contracts.CompiledSchemaRequest

object FeatureTaskRuntimeHandoffEnvelopeSchemaValidator {
  fun validate(envelope: Map<String, Any?>, workflowId: String? = null) {
    val consumerPhaseId = envelope["consumer_phase_id"] as? String ?: ""
    val instance: JsonNode = ClasspathContractSchemaLoader.valueToTree(envelope)
    val errors: Set<ValidationMessage> = ClasspathContractSchemaLoader.validate(schema(), instance)
    if (errors.isNotEmpty()) {
      throw InvalidFeatureTaskRuntimeHandoffProjectionError(
        context = InvalidFeatureTaskRuntimeHandoffProjectionContext(
          workflowId = workflowId,
          consumerPhaseId = consumerPhaseId,
          projectionName = firstProjectionLocation(errors),
          projectionContractId = FeatureTaskRuntimeHandoffEnvelopeSchemaPaths.EXPECTED_SCHEMA_ID,
          projectionContractVersion = FEATURE_TASK_RUNTIME_HANDOFF_ENVELOPE_CONTRACT_VERSION,
          failureKind = FeatureTaskRuntimeHandoffProjectionFailureKind.SCHEMA_INVALID,
          reason = formatReason(errors),
        ),
      )
    }
  }

  private fun firstProjectionLocation(errors: Set<ValidationMessage>): String =
    errors.minByOrNull { it.instanceLocation?.toString().orEmpty() }
      ?.instanceLocation?.toString()?.ifBlank { "<root>" }
      ?: "<root>"

  private fun formatReason(errors: Set<ValidationMessage>): String =
    errors.sortedBy { it.instanceLocation?.toString().orEmpty() }
      .take(MAX_REPORTED_VIOLATIONS)
      .joinToString(separator = " | ") { error ->
        "${error.instanceLocation?.toString()?.ifBlank { "<root>" } ?: "<root>"}: ${error.message.orEmpty()}"
      } + if (errors.size > MAX_REPORTED_VIOLATIONS) " (+${errors.size - MAX_REPORTED_VIOLATIONS} more)" else ""

  private const val MAX_REPORTED_VIOLATIONS: Int = 3
}

private fun schema(): JsonSchema = ClasspathContractSchemaLoader.compiledSchema(
  CompiledSchemaRequest(
    cacheKey = FeatureTaskRuntimeHandoffEnvelopeSchemaPaths.CLASSPATH_RESOURCE,
    classLoader = FeatureTaskRuntimeHandoffEnvelopeSchemaValidator::class.java.classLoader,
    classpathResource = FeatureTaskRuntimeHandoffEnvelopeSchemaPaths.CLASSPATH_RESOURCE,
    missingResource = {
      InvalidFeatureTaskRuntimeHandoffProjectionError(
        context = InvalidFeatureTaskRuntimeHandoffProjectionContext(
          workflowId = null,
          consumerPhaseId = "<schema-load>",
          projectionName = FeatureTaskRuntimeHandoffEnvelopeSchemaPaths.CLASSPATH_RESOURCE,
          projectionContractId = FeatureTaskRuntimeHandoffEnvelopeSchemaPaths.EXPECTED_SCHEMA_ID,
          projectionContractVersion = FEATURE_TASK_RUNTIME_HANDOFF_ENVELOPE_CONTRACT_VERSION,
          failureKind = FeatureTaskRuntimeHandoffProjectionFailureKind.SCHEMA_INVALID,
          reason = "Canonical feature-task-runtime handoff envelope schema is missing on the classpath.",
        ),
      )
    },
    processingFailure = { cause ->
      InvalidFeatureTaskRuntimeHandoffProjectionError(
        context = InvalidFeatureTaskRuntimeHandoffProjectionContext(
          workflowId = null,
          consumerPhaseId = "<schema-load>",
          projectionName = FeatureTaskRuntimeHandoffEnvelopeSchemaPaths.CLASSPATH_RESOURCE,
          projectionContractId = FeatureTaskRuntimeHandoffEnvelopeSchemaPaths.EXPECTED_SCHEMA_ID,
          projectionContractVersion = FEATURE_TASK_RUNTIME_HANDOFF_ENVELOPE_CONTRACT_VERSION,
          failureKind = FeatureTaskRuntimeHandoffProjectionFailureKind.SCHEMA_INVALID,
          reason = cause.message ?: cause::class.simpleName.orEmpty(),
        ),
      )
    },
    loadFailureLogger = {},
    expectedSchemaId = FeatureTaskRuntimeHandoffEnvelopeSchemaPaths.EXPECTED_SCHEMA_ID,
    expectedContractVersion = FEATURE_TASK_RUNTIME_HANDOFF_ENVELOPE_CONTRACT_VERSION,
    identityFailure = ::featureTaskRuntimeHandoffEnvelopeIdentityMismatchError,
  ),
)

private fun featureTaskRuntimeHandoffEnvelopeIdentityMismatchError(
  reason: String,
): InvalidFeatureTaskRuntimeHandoffProjectionError = InvalidFeatureTaskRuntimeHandoffProjectionError(
  context = InvalidFeatureTaskRuntimeHandoffProjectionContext(
    workflowId = null,
    consumerPhaseId = "<schema-load>",
    projectionName = FeatureTaskRuntimeHandoffEnvelopeSchemaPaths.CLASSPATH_RESOURCE,
    projectionContractId = FeatureTaskRuntimeHandoffEnvelopeSchemaPaths.EXPECTED_SCHEMA_ID,
    projectionContractVersion = FEATURE_TASK_RUNTIME_HANDOFF_ENVELOPE_CONTRACT_VERSION,
    failureKind = FeatureTaskRuntimeHandoffProjectionFailureKind.SCHEMA_INVALID,
    reason = reason,
  ),
)
