package skillbill.infrastructure.fs.contracts.workflow

import com.fasterxml.jackson.databind.JsonNode
import com.networknt.schema.JsonSchema
import com.networknt.schema.ValidationMessage
import skillbill.contracts.workflow.FEATURE_TASK_RUNTIME_BUILD_RECEIPT_CONTRACT_VERSION
import skillbill.contracts.workflow.FEATURE_TASK_RUNTIME_PHASE_HANDOFF_CONTRACT_VERSION
import skillbill.contracts.workflow.FEATURE_TASK_RUNTIME_PERSISTENCE_CONTRACT_VERSION
import skillbill.contracts.workflow.FEATURE_TASK_RUNTIME_PROJECTION_MEASUREMENT_CONTRACT_VERSION
import skillbill.contracts.workflow.FEATURE_TASK_RUNTIME_SHARED_EVIDENCE_PROJECTION_CONTRACT_VERSION
import skillbill.contracts.workflow.FEATURE_TASK_RUNTIME_VALIDATION_EVIDENCE_CONTRACT_VERSION
import skillbill.contracts.workflow.FeatureTaskRuntimeBuildReceiptSchemaPaths
import skillbill.contracts.workflow.FeatureTaskRuntimePersistenceSchemaPaths
import skillbill.contracts.workflow.FeatureTaskRuntimePhaseHandoffSchemaPaths
import skillbill.contracts.workflow.FeatureTaskRuntimeProjectionMeasurementSchemaPaths
import skillbill.contracts.workflow.FeatureTaskRuntimeSharedEvidenceProjectionSchemaPaths
import skillbill.contracts.workflow.FeatureTaskRuntimeValidationEvidenceSchemaPaths
import skillbill.error.InvalidFeatureTaskRuntimeBuildReceiptSchemaError
import skillbill.error.InvalidFeatureTaskRuntimePersistenceSchemaError
import skillbill.error.InvalidFeatureTaskRuntimePhaseHandoffSchemaError
import skillbill.error.InvalidFeatureTaskRuntimeProjectionMeasurementSchemaError
import skillbill.error.InvalidFeatureTaskRuntimeSharedEvidenceProjectionSchemaError
import skillbill.error.InvalidFeatureTaskRuntimeValidationEvidenceSchemaError
import skillbill.error.ShellContentContractException
import skillbill.infrastructure.fs.contracts.ClasspathContractSchemaLoader
import java.nio.file.Files
import java.nio.file.Path

private const val MAX_REPORTED_SCHEMA_FAILURES = 3

object FeatureTaskRuntimePhaseHandoffSchemaValidator {
  fun validate(payload: Map<String, Any?>, sourceLabel: String) = validateAgainst(
    payload,
    FeatureTaskRuntimePhaseHandoffSchemaPaths.REPO_RELATIVE_PATH,
    FeatureTaskRuntimePhaseHandoffSchemaPaths.CLASSPATH_RESOURCE,
    FeatureTaskRuntimePhaseHandoffSchemaPaths.EXPECTED_SCHEMA_ID,
    FEATURE_TASK_RUNTIME_PHASE_HANDOFF_CONTRACT_VERSION,
  ) { reason -> InvalidFeatureTaskRuntimePhaseHandoffSchemaError(sourceLabel, reason) }
}

object FeatureTaskRuntimePersistenceSchemaValidator {
  fun validate(payload: Map<String, Any?>, sourceLabel: String) = validateAgainst(
    payload,
    FeatureTaskRuntimePersistenceSchemaPaths.REPO_RELATIVE_PATH,
    FeatureTaskRuntimePersistenceSchemaPaths.CLASSPATH_RESOURCE,
    FeatureTaskRuntimePersistenceSchemaPaths.EXPECTED_SCHEMA_ID,
    FEATURE_TASK_RUNTIME_PERSISTENCE_CONTRACT_VERSION,
    contractVersionMatches = ::persistenceContractVersionMatches,
    error = { reason -> InvalidFeatureTaskRuntimePersistenceSchemaError(sourceLabel, reason) },
  )
}

object FeatureTaskRuntimeProjectionMeasurementSchemaValidator {
  fun validate(payload: Map<String, Any?>, sourceLabel: String) = validateAgainst(
    payload,
    FeatureTaskRuntimeProjectionMeasurementSchemaPaths.REPO_RELATIVE_PATH,
    FeatureTaskRuntimeProjectionMeasurementSchemaPaths.CLASSPATH_RESOURCE,
    FeatureTaskRuntimeProjectionMeasurementSchemaPaths.EXPECTED_SCHEMA_ID,
    FEATURE_TASK_RUNTIME_PROJECTION_MEASUREMENT_CONTRACT_VERSION,
  ) { reason -> InvalidFeatureTaskRuntimeProjectionMeasurementSchemaError(sourceLabel, reason) }
}

object FeatureTaskRuntimeSharedEvidenceProjectionSchemaValidator {
  fun validate(payload: Map<String, Any?>, sourceLabel: String) = validateAgainst(
    payload,
    FeatureTaskRuntimeSharedEvidenceProjectionSchemaPaths.REPO_RELATIVE_PATH,
    FeatureTaskRuntimeSharedEvidenceProjectionSchemaPaths.CLASSPATH_RESOURCE,
    FeatureTaskRuntimeSharedEvidenceProjectionSchemaPaths.EXPECTED_SCHEMA_ID,
    FEATURE_TASK_RUNTIME_SHARED_EVIDENCE_PROJECTION_CONTRACT_VERSION,
  ) { reason -> InvalidFeatureTaskRuntimeSharedEvidenceProjectionSchemaError(sourceLabel, reason) }
}

object FeatureTaskRuntimeValidationEvidenceSchemaValidator {
  fun validate(payload: Map<String, Any?>, sourceLabel: String) = validateAgainst(
    payload,
    FeatureTaskRuntimeValidationEvidenceSchemaPaths.REPO_RELATIVE_PATH,
    FeatureTaskRuntimeValidationEvidenceSchemaPaths.CLASSPATH_RESOURCE,
    FeatureTaskRuntimeValidationEvidenceSchemaPaths.EXPECTED_SCHEMA_ID,
    FEATURE_TASK_RUNTIME_VALIDATION_EVIDENCE_CONTRACT_VERSION,
  ) { reason -> InvalidFeatureTaskRuntimeValidationEvidenceSchemaError(sourceLabel, reason) }
}

object FeatureTaskRuntimeBuildReceiptSchemaValidator {
  fun validate(payload: Map<String, Any?>, sourceLabel: String) {
    val instance = ClasspathContractSchemaLoader.valueToTree(payload)
    val failures = ClasspathContractSchemaLoader.validate(buildReceiptSchema(), instance)
    if (failures.isNotEmpty()) {
      val sorted = failures.sortedBy { it.instanceLocation.toString() }
      val reasons = formatBuildReceiptViolationReasons(sorted.take(MAX_REPORTED_SCHEMA_FAILURES), instance)
      throw InvalidFeatureTaskRuntimeBuildReceiptSchemaError(
        sourceLabel = sourceLabel,
        reason = reasons.valueBearing,
        payloadFreeReason = reasons.payloadFree,
      )
    }
  }
}

private fun buildReceiptSchema(): JsonSchema =
  ClasspathContractSchemaLoader.compiledSchema(
    cacheKey = FeatureTaskRuntimeBuildReceiptSchemaPaths.CLASSPATH_RESOURCE,
    classLoader = FeatureTaskRuntimeBuildReceiptSchemaValidator::class.java.classLoader,
    classpathResource = FeatureTaskRuntimeBuildReceiptSchemaPaths.CLASSPATH_RESOURCE,
    missingResource = {
      InvalidFeatureTaskRuntimeBuildReceiptSchemaError(
        sourceLabel = FeatureTaskRuntimeBuildReceiptSchemaPaths.CLASSPATH_RESOURCE,
        reason = "Canonical feature-task-runtime build receipt schema is missing. Expected it on the JVM " +
          "classpath at '${FeatureTaskRuntimeBuildReceiptSchemaPaths.CLASSPATH_RESOURCE}'.",
      )
    },
    processingFailure = { cause ->
      InvalidFeatureTaskRuntimeBuildReceiptSchemaError(
        sourceLabel = FeatureTaskRuntimeBuildReceiptSchemaPaths.CLASSPATH_RESOURCE,
        reason = cause.message ?: cause::class.simpleName.orEmpty(),
        cause = cause,
      )
    },
    loadFailureLogger = {},
    expectedSchemaId = FeatureTaskRuntimeBuildReceiptSchemaPaths.EXPECTED_SCHEMA_ID,
    expectedContractVersion = FEATURE_TASK_RUNTIME_BUILD_RECEIPT_CONTRACT_VERSION,
    identityFailure = { reason ->
      InvalidFeatureTaskRuntimeBuildReceiptSchemaError(
        sourceLabel = FeatureTaskRuntimeBuildReceiptSchemaPaths.CLASSPATH_RESOURCE,
        reason = reason,
      )
    },
  )

private data class BuildReceiptViolationReasons(val valueBearing: String, val payloadFree: String)

private fun formatBuildReceiptViolationReasons(
  sorted: List<ValidationMessage>,
  instance: JsonNode,
): BuildReceiptViolationReasons {
  val violations = sorted.map { error ->
    val location = error.instanceLocation?.toString().orEmpty()
    val fieldPath = featureTaskRuntimePhaseOutputDottedFieldPath(location).ifBlank { "<root>" }
    val head = "$fieldPath: ${error.message}"
    head to extractFeatureTaskRuntimePhaseOutputOffendingValue(instance, location)
  }
  fun render(includeOffendingValues: Boolean): String =
    violations.joinToString(separator = " | ") { (head, offendingValue) ->
      if (includeOffendingValues && offendingValue.isNotBlank()) {
        "$head — offending value: $offendingValue"
      } else {
        head
      }
    }
  return BuildReceiptViolationReasons(valueBearing = render(true), payloadFree = render(false))
}

private fun validateAgainst(
  payload: Map<String, Any?>,
  repoPath: String,
  classpathResource: String,
  expectedId: String,
  expectedContractVersion: String,
  contractVersionMatches: ((JsonNode, String) -> Boolean)? = null,
  error: (String) -> ShellContentContractException,
) {
  val schema = ClasspathContractSchemaLoader.compiledSchema(
    cacheKey = classpathResource,
    classLoader = FeatureTaskRuntimePhaseHandoffSchemaValidator::class.java.classLoader,
    classpathResource = classpathResource,
    missingResource = { error("Canonical runtime contract is missing at '$classpathResource'.") },
    processingFailure = { cause -> error(cause.message ?: cause::class.simpleName.orEmpty()) },
    loadFailureLogger = {},
    expectedSchemaId = expectedId,
    expectedContractVersion = expectedContractVersion,
    contractVersionMatches = contractVersionMatches,
    identityFailure = error,
  )
  val failures = ClasspathContractSchemaLoader.validate(schema, ClasspathContractSchemaLoader.valueToTree(payload))
  if (failures.isNotEmpty()) {
    val reason = failures
      .sortedBy { it.instanceLocation.toString() }
      .take(MAX_REPORTED_SCHEMA_FAILURES)
      .joinToString(" | ") { it.message }
    throw error(reason)
  }
}

private fun persistenceContractVersionMatches(node: JsonNode, expected: String): Boolean =
  listOf("private_phase_record", "delivered_projection").all { definitionName ->
    node.path("\$defs")
      .path(definitionName)
      .path("properties")
      .path("contract_version")
      .path("const")
      .asText("") == expected
  }
