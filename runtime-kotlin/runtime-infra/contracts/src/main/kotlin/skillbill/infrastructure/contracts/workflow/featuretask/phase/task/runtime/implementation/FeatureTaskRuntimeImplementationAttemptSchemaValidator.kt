package skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.implementation
import com.fasterxml.jackson.databind.JsonNode
import com.networknt.schema.JsonSchema
import com.networknt.schema.ValidationMessage
import skillbill.contracts.workflow.featuretask.FEATURE_TASK_RUNTIME_IMPLEMENTATION_ATTEMPT_CONTRACT_VERSION
import skillbill.contracts.workflow.featuretask.FeatureTaskRuntimeImplementationAttemptSchemaPaths
import skillbill.error.shellcontent.InvalidFeatureTaskRuntimeImplementationAttemptSchemaError
import skillbill.infrastructure.contracts.ClasspathContractSchemaLoader
import skillbill.infrastructure.contracts.CompiledSchemaRequest
import skillbill.infrastructure.contracts.workflow.decomposition.error
import skillbill.infrastructure.contracts.workflow.decomposition.instanceLocation
import skillbill.infrastructure.contracts.workflow.featuretask.handoff.classpathResource
import skillbill.infrastructure.contracts.workflow.featuretask.handoff.error
import skillbill.infrastructure.contracts.workflow.featuretask.handoff.expectedContractVersion
import skillbill.infrastructure.contracts.workflow.featuretask.handoff.payload
import skillbill.infrastructure.contracts.workflow.featuretask.handoff.reason
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.buildreceipt.payload
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.checkpoint.error
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.phase.error
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.planning.error
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.planning.message
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.projection.error
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.shared.error
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.shared.payload
import skillbill.infrastructure.contracts.workflow.goal.observability.error
import skillbill.infrastructure.contracts.workflow.goal.observability.instanceLocation
import skillbill.infrastructure.contracts.workflow.goal.planning.error
import skillbill.infrastructure.contracts.workflow.goal.planning.instanceLocation
import skillbill.infrastructure.contracts.workflow.goal.planning.reason
import skillbill.infrastructure.contracts.workflow.goal.progress.instanceLocation
import skillbill.infrastructure.contracts.workflow.goal.status.instanceLocation
import skillbill.infrastructure.contracts.workflow.schema.error
import skillbill.infrastructure.contracts.workflow.workflow.instanceLocation

object FeatureTaskRuntimeImplementationAttemptSchemaValidator {
  fun validate(payload: Map<String, Any?>, sourceLabel: String) {
    val instance: JsonNode = ClasspathContractSchemaLoader.valueToTree(payload)
    val errors: Set<ValidationMessage> = ClasspathContractSchemaLoader.validate(schema(), instance)
    if (errors.isNotEmpty()) {
      throw InvalidFeatureTaskRuntimeImplementationAttemptSchemaError(
        sourceLabel = sourceLabel,
        reason = formatReason(errors),
      )
    }
  }

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
    cacheKey = FeatureTaskRuntimeImplementationAttemptSchemaPaths.CLASSPATH_RESOURCE,
    classLoader = FeatureTaskRuntimeImplementationAttemptSchemaValidator::class.java.classLoader,
    classpathResource = FeatureTaskRuntimeImplementationAttemptSchemaPaths.CLASSPATH_RESOURCE,
    missingResource = {
      InvalidFeatureTaskRuntimeImplementationAttemptSchemaError(
        sourceLabel = FeatureTaskRuntimeImplementationAttemptSchemaPaths.CLASSPATH_RESOURCE,
        reason = "Canonical feature-task-runtime implementation-attempt schema is missing. Expected classpath " +
          "resource '${FeatureTaskRuntimeImplementationAttemptSchemaPaths.CLASSPATH_RESOURCE}'.",
      )
    },
    processingFailure = { cause ->
      InvalidFeatureTaskRuntimeImplementationAttemptSchemaError(
        sourceLabel = FeatureTaskRuntimeImplementationAttemptSchemaPaths.CLASSPATH_RESOURCE,
        reason = cause.message ?: cause::class.simpleName.orEmpty(),
        cause = cause,
      )
    },
    loadFailureLogger = {},
    expectedSchemaId = FeatureTaskRuntimeImplementationAttemptSchemaPaths.EXPECTED_SCHEMA_ID,
    expectedContractVersion = FEATURE_TASK_RUNTIME_IMPLEMENTATION_ATTEMPT_CONTRACT_VERSION,
    identityFailure = { reason ->
      InvalidFeatureTaskRuntimeImplementationAttemptSchemaError(
        sourceLabel = FeatureTaskRuntimeImplementationAttemptSchemaPaths.CLASSPATH_RESOURCE,
        reason = reason,
      )
    },
  ),
)
