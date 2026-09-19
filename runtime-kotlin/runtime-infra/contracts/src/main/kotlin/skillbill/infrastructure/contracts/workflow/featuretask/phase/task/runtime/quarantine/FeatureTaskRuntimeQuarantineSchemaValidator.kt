package skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.quarantine
import com.fasterxml.jackson.databind.JsonNode
import com.networknt.schema.JsonSchema
import com.networknt.schema.ValidationMessage
import skillbill.contracts.workflow.featuretask.FEATURE_TASK_RUNTIME_QUARANTINE_CONTRACT_VERSION
import skillbill.contracts.workflow.featuretask.FeatureTaskRuntimeQuarantineSchemaPaths
import skillbill.error.shellcontent.InvalidFeatureTaskRuntimeQuarantineSchemaError
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
import skillbill.infrastructure.contracts.workflow.featuretask.phase.task.runtime.implementation.error
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

object FeatureTaskRuntimeQuarantineSchemaValidator {
  fun validate(payload: Map<String, Any?>, sourceLabel: String) {
    val instance: JsonNode = ClasspathContractSchemaLoader.valueToTree(payload)
    val errors: Set<ValidationMessage> = ClasspathContractSchemaLoader.validate(schema(), instance)
    if (errors.isNotEmpty()) {
      throw InvalidFeatureTaskRuntimeQuarantineSchemaError(
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
    cacheKey = FeatureTaskRuntimeQuarantineSchemaPaths.CLASSPATH_RESOURCE,
    classLoader = FeatureTaskRuntimeQuarantineSchemaValidator::class.java.classLoader,
    classpathResource = FeatureTaskRuntimeQuarantineSchemaPaths.CLASSPATH_RESOURCE,
    missingResource = {
      InvalidFeatureTaskRuntimeQuarantineSchemaError(
        sourceLabel = FeatureTaskRuntimeQuarantineSchemaPaths.CLASSPATH_RESOURCE,
        reason = "Canonical feature-task-runtime quarantine schema is missing. Expected classpath resource " +
          "'${FeatureTaskRuntimeQuarantineSchemaPaths.CLASSPATH_RESOURCE}'.",
      )
    },
    processingFailure = { cause ->
      InvalidFeatureTaskRuntimeQuarantineSchemaError(
        sourceLabel = FeatureTaskRuntimeQuarantineSchemaPaths.CLASSPATH_RESOURCE,
        reason = cause.message ?: cause::class.simpleName.orEmpty(),
        cause = cause,
      )
    },
    loadFailureLogger = {},
    expectedSchemaId = FeatureTaskRuntimeQuarantineSchemaPaths.EXPECTED_SCHEMA_ID,
    expectedContractVersion = FEATURE_TASK_RUNTIME_QUARANTINE_CONTRACT_VERSION,
    identityFailure = { reason ->
      InvalidFeatureTaskRuntimeQuarantineSchemaError(
        sourceLabel = FeatureTaskRuntimeQuarantineSchemaPaths.CLASSPATH_RESOURCE,
        reason = reason,
      )
    },
  ),
)
