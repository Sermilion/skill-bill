package skillbill.workflow.taskruntime.model.validation
import skillbill.contracts.review.ReviewVerificationSignalKeys
import skillbill.contracts.workflow.identity.evidence.ValidationEvidencePayloadKeys
import skillbill.error.shellcontent.InvalidFeatureTaskRuntimeValidationEvidenceSchemaError
import skillbill.workflow.taskruntime.model.persistence.artifact.asExactIntOrNull

data class FeatureTaskRuntimeValidationGateExecutionEvidence(
  val validationStatus: String,
  val checks: List<String>,
  val gateRunCount: Int,
  val gateRuns: List<FeatureTaskRuntimeValidationGateRunRecord>,
) {
  init {
    require(validationStatus.isNotBlank()) {
      "FeatureTaskRuntimeValidationGateExecutionEvidence.validationStatus must be non-blank."
    }
    require(gateRunCount >= 0) {
      "FeatureTaskRuntimeValidationGateExecutionEvidence.gateRunCount must be >= 0."
    }
    require(gateRuns.size == gateRunCount) {
      "FeatureTaskRuntimeValidationGateExecutionEvidence.gateRuns size ${gateRuns.size} " +
        "must equal gateRunCount $gateRunCount."
    }
    require(checks.all { it.isNotBlank() }) {
      "FeatureTaskRuntimeValidationGateExecutionEvidence.checks must be non-blank strings."
    }
  }

  val zeroWork: Boolean
    get() =
      gateRuns.isNotEmpty() &&
        gateRuns.all { it.executedWorkUnits == 0 && it.executedChecks.isEmpty() }

  val evidenceRecorded: Boolean
    get() = gateRuns.isNotEmpty() && gateRuns.all { it.executedChecksRecorded }

  internal fun toArtifactMap(repositoryCheckpoint: String): Map<String, Any?> {
    require(repositoryCheckpoint.isNotBlank()) {
      "FeatureTaskRuntimeValidationGateExecutionEvidence.repositoryCheckpoint must be non-blank."
    }
    return linkedMapOf(
      ValidationEvidencePayloadKeys.VALIDATION_STATUS to validationStatus,
      ValidationEvidencePayloadKeys.CHECKS to checks,
      ReviewVerificationSignalKeys.REPOSITORY_CHECKPOINT to
        mapOf(ReviewVerificationSignalKeys.REPOSITORY_CHECKPOINT_FINGERPRINT to repositoryCheckpoint),
      ValidationEvidencePayloadKeys.GATE_RUN_COUNT to gateRunCount,
      ValidationEvidencePayloadKeys.GATE_RUNS to gateRuns.map { it.toArtifactMap() },
    )
  }

  companion object {
    fun fromGateMeasurements(
      measurements: List<FeatureTaskRuntimeValidationGateRunRecord>,
      validationStatus: String = "passed",
    ): FeatureTaskRuntimeValidationGateExecutionEvidence =
      FeatureTaskRuntimeValidationGateExecutionEvidence(
        validationStatus = validationStatus,
        checks = aggregateChecks(measurements),
        gateRunCount = measurements.size,
        gateRuns = measurements,
      )

    fun aggregateChecks(measurements: List<FeatureTaskRuntimeValidationGateRunRecord>): List<String> =
      measurements.flatMap { it.executedChecks }.distinct().sorted()

    internal fun fromArtifactMap(
      raw: Map<String, Any?>,
      sourceLabel: String,
    ): FeatureTaskRuntimeValidationGateExecutionEvidence {
      val validationStatus =
        raw[ValidationEvidencePayloadKeys.VALIDATION_STATUS] as? String
          ?: invalid(sourceLabel, "validation_status is missing.")
      val checks = decodeChecks(raw, sourceLabel)
      decodeRepositoryCheckpoint(raw[ReviewVerificationSignalKeys.REPOSITORY_CHECKPOINT], sourceLabel)
      val gateRunCount =
        raw[ValidationEvidencePayloadKeys.GATE_RUN_COUNT].asExactIntOrNull()
          ?: invalid(sourceLabel, "gate_run_count must be an integer.")
      val gateRuns =
        try {
          decodeGateRuns(raw[ValidationEvidencePayloadKeys.GATE_RUNS], sourceLabel)
        } catch (error: InvalidFeatureTaskRuntimeValidationEvidenceSchemaError) {
          throw error
        } catch (error: IllegalArgumentException) {
          invalid(sourceLabel, error.message.orEmpty())
        }
      val aggregateChecks = aggregateChecks(gateRuns)
      if (checks != aggregateChecks) {
        invalid(
          sourceLabel,
          "checks must equal the distinct sorted executed check identities from gate_runs.",
        )
      }
      return try {
        FeatureTaskRuntimeValidationGateExecutionEvidence(
          validationStatus = validationStatus,
          checks = checks,
          gateRunCount = gateRunCount,
          gateRuns = gateRuns,
        )
      } catch (error: IllegalArgumentException) {
        invalid(sourceLabel, error.message.orEmpty())
      }
    }

    private fun decodeChecks(
      raw: Map<String, Any?>,
      sourceLabel: String,
    ): List<String> {
      if (!raw.containsKey(ValidationEvidencePayloadKeys.CHECKS)) {
        invalid(sourceLabel, "checks is missing.")
      }
      val checksRaw = raw[ValidationEvidencePayloadKeys.CHECKS]
      val list =
        checksRaw as? List<*>
          ?: invalid(sourceLabel, "checks must be a list.")
      return list.mapIndexed { index, entry ->
        entry as? String ?: invalid(sourceLabel, "checks[$index] must be a string.")
      }
    }

    private fun decodeGateRuns(
      raw: Any?,
      sourceLabel: String,
    ): List<FeatureTaskRuntimeValidationGateRunRecord> {
      val runsRaw =
        raw as? List<*>
          ?: invalid(sourceLabel, "gate_runs must be a list.")
      return runsRaw.mapIndexed { index, entry ->
        val map =
          entry as? Map<*, *>
            ?: invalid(sourceLabel, "gate_runs[$index] must be a mapping.")
        FeatureTaskRuntimeValidationGateRunRecord(
          durationMs = map.gateProgressLong(ValidationEvidencePayloadKeys.DURATION_MS),
          outcome =
            requireNotNull(
              ValidationGateRunOutcome.fromWire(map.gateProgressString(ValidationEvidencePayloadKeys.OUTCOME)),
            ) {
              "Unknown validation gate outcome."
            },
          cacheMode =
            requireNotNull(
              ValidationGateCacheMode.fromWire(map.gateProgressString(ValidationEvidencePayloadKeys.CACHE_MODE)),
            ) {
              "Unknown validation gate cache mode."
            },
          executedWorkUnits = map.gateProgressInt(ValidationEvidencePayloadKeys.EXECUTED_WORK_UNITS),
          executedChecks = decodeGateRunExecutedChecks(map, sourceLabel, index),
          command = map.gateProgressOptionalString(ValidationEvidencePayloadKeys.COMMAND),
          exitCode = map.gateProgressOptionalInt(ValidationEvidencePayloadKeys.EXIT_CODE),
          executedChecksRecorded = map.containsKey(ValidationEvidencePayloadKeys.EXECUTED_CHECKS),
        )
      }
    }

    private fun decodeGateRunExecutedChecks(
      map: Map<*, *>,
      sourceLabel: String,
      index: Int,
    ): List<String> {
      if (!map.containsKey(ValidationEvidencePayloadKeys.EXECUTED_CHECKS)) return emptyList()
      val raw = map[ValidationEvidencePayloadKeys.EXECUTED_CHECKS]
      val list =
        raw as? List<*>
          ?: invalid(sourceLabel, "gate_runs[$index].executed_checks must be a list.")
      return list.mapIndexed { checkIndex, entry ->
        entry as? String ?: invalid(
          sourceLabel,
          "gate_runs[$index].executed_checks[$checkIndex] must be a string.",
        )
      }
    }

    private fun decodeRepositoryCheckpoint(
      raw: Any?,
      sourceLabel: String,
    ) {
      val checkpoint =
        raw as? Map<*, *>
          ?: invalid(sourceLabel, "repository_checkpoint must be a mapping.")
      val fingerprint = checkpoint[ReviewVerificationSignalKeys.REPOSITORY_CHECKPOINT_FINGERPRINT] as? String
      if (fingerprint.isNullOrBlank()) {
        invalid(sourceLabel, "repository_checkpoint.fingerprint must be non-blank.")
      }
    }

    private fun invalid(
      sourceLabel: String,
      reason: String,
    ): Nothing = throw InvalidFeatureTaskRuntimeValidationEvidenceSchemaError(sourceLabel, reason)
  }
}

