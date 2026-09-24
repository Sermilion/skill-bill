package skillbill.infrastructure.contracts.experiment

import skillbill.contracts.JsonCodec
import skillbill.contracts.experiment.EXPERIMENT_DESCRIPTOR_CONTRACT_VERSION
import skillbill.contracts.experiment.EXPERIMENT_OBSERVATION_CONTRACT_VERSION
import skillbill.contracts.experiment.EXPERIMENT_PAIR_CONTRACT_VERSION
import skillbill.contracts.experiment.EXPERIMENT_REPORT_CONTRACT_VERSION
import skillbill.error.core.ShellContentContractException
import skillbill.error.shellcontent.InvalidExperimentDescriptorSchemaError
import skillbill.error.shellcontent.InvalidExperimentObservationSchemaError
import skillbill.error.shellcontent.InvalidExperimentPairSchemaError
import skillbill.error.shellcontent.InvalidExperimentReportSchemaError
import skillbill.infrastructure.contracts.ClasspathContractSchemaLoader
import skillbill.infrastructure.contracts.CompiledSchemaRequest
import skillbill.infrastructure.contracts.locator.ExperimentDescriptorSchemaPaths
import skillbill.infrastructure.contracts.locator.ExperimentObservationSchemaPaths
import skillbill.infrastructure.contracts.locator.ExperimentPairSchemaPaths
import skillbill.infrastructure.contracts.locator.ExperimentReportSchemaPaths
import skillbill.ports.experiment.pair.model.ExperimentPairPayload
import skillbill.ports.experiment.validation.ExperimentPayloadValidationPort

private const val MAX_REPORTED_SCHEMA_FAILURES = 3

private data class ExperimentSchemaValidationRequest(
  val payload: Map<String, Any?>,
  val classpathResource: String,
  val expectedId: String,
  val expectedContractVersion: String,
  val error: (String) -> ShellContentContractException,
)

object ExperimentDescriptorSchemaValidator {
  fun validate(
    payload: Map<String, Any?>,
    sourceLabel: String,
  ) = validateAgainst(
    ExperimentSchemaValidationRequest(
      payload = payload,
      classpathResource = ExperimentDescriptorSchemaPaths.CLASSPATH_RESOURCE,
      expectedId = ExperimentDescriptorSchemaPaths.EXPECTED_SCHEMA_ID,
      expectedContractVersion = EXPERIMENT_DESCRIPTOR_CONTRACT_VERSION,
      error = { reason -> InvalidExperimentDescriptorSchemaError(sourceLabel, reason) },
    ),
  )
}

object ExperimentPairSchemaValidator {
  fun validate(
    payload: Map<String, Any?>,
    sourceLabel: String,
  ) = validateAgainst(
    ExperimentSchemaValidationRequest(
      payload = payload,
      classpathResource = ExperimentPairSchemaPaths.CLASSPATH_RESOURCE,
      expectedId = ExperimentPairSchemaPaths.EXPECTED_SCHEMA_ID,
      expectedContractVersion = EXPERIMENT_PAIR_CONTRACT_VERSION,
      error = { reason -> InvalidExperimentPairSchemaError(sourceLabel, reason) },
    ),
  )
}

object ExperimentObservationSchemaValidator {
  fun validate(
    payload: Map<String, Any?>,
    sourceLabel: String,
  ) = validateAgainst(
    ExperimentSchemaValidationRequest(
      payload = payload,
      classpathResource = ExperimentObservationSchemaPaths.CLASSPATH_RESOURCE,
      expectedId = ExperimentObservationSchemaPaths.EXPECTED_SCHEMA_ID,
      expectedContractVersion = EXPERIMENT_OBSERVATION_CONTRACT_VERSION,
      error = { reason -> InvalidExperimentObservationSchemaError(sourceLabel, reason) },
    ),
  )
}

object ExperimentReportSchemaValidator {
  fun validate(
    payload: Map<String, Any?>,
    sourceLabel: String,
  ) = validateAgainst(
    ExperimentSchemaValidationRequest(
      payload = payload,
      classpathResource = ExperimentReportSchemaPaths.CLASSPATH_RESOURCE,
      expectedId = ExperimentReportSchemaPaths.EXPECTED_SCHEMA_ID,
      expectedContractVersion = EXPERIMENT_REPORT_CONTRACT_VERSION,
      error = { reason -> InvalidExperimentReportSchemaError(sourceLabel, reason) },
    ),
  )
}

class ExperimentPayloadSchemaValidator : ExperimentPayloadValidationPort {
  override fun validatePair(
    payload: ExperimentPairPayload,
    sourceLabel: String,
  ) = ExperimentPairSchemaValidator.validate(payload.toMap(), sourceLabel)

  override fun validateObservation(
    payload: ExperimentPairPayload,
    sourceLabel: String,
  ) = ExperimentObservationSchemaValidator.validate(payload.toMap(), sourceLabel)

  override fun validateReport(
    payload: ExperimentPairPayload,
    sourceLabel: String,
  ) = ExperimentReportSchemaValidator.validate(payload.toMap(), sourceLabel)
}

private fun ExperimentPairPayload.toMap(): Map<String, Any?> =
  JsonCodec.anyToStringAnyMap(
    JsonCodec.jsonElementToValue(requireNotNull(JsonCodec.parseObjectOrNull(toJson()))),
  ) ?: error("Experiment pair payload must decode to an object.")

private fun validateAgainst(request: ExperimentSchemaValidationRequest) {
  val schema =
    ClasspathContractSchemaLoader.compiledSchema(
      CompiledSchemaRequest(
        cacheKey = request.classpathResource,
        classLoader = ExperimentDescriptorSchemaValidator::class.java.classLoader,
        classpathResource = request.classpathResource,
        missingResource = { request.error("Canonical runtime contract is missing at '${request.classpathResource}'.") },
        processingFailure = { cause -> request.error(cause.message ?: cause::class.simpleName.orEmpty()) },
        loadFailureLogger = {},
        expectedSchemaId = request.expectedId,
        expectedContractVersion = request.expectedContractVersion,
        identityFailure = request.error,
      ),
    )
  val failures =
    ClasspathContractSchemaLoader.validate(
      schema,
      ClasspathContractSchemaLoader.valueToTree(request.payload),
    )
  if (failures.isNotEmpty()) {
    val reason =
      failures
        .sortedBy { it.instanceLocation.toString() }
        .take(MAX_REPORTED_SCHEMA_FAILURES)
        .joinToString(" | ") { it.message }
    throw request.error(reason)
  }
}
