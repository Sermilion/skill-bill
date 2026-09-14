package skillbill.workflow.taskruntime.model

import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.workflow.FEATURE_TASK_RUNTIME_PERSISTENCE_CONTRACT_VERSION
import skillbill.contracts.workflow.ValidationEvidencePayloadKeys
import skillbill.error.InvalidWorkflowStateSchemaError

const val FEATURE_TASK_RUNTIME_VALIDATION_GATE_PROGRESS_ARTIFACT_KEY: String =
  "feature_task_runtime_validation_gate_progress"

const val FEATURE_TASK_RUNTIME_BUILD_GATE_PROGRESS_ARTIFACT_KEY: String =
  "feature_task_runtime_build_gate_progress"

enum class FeatureTaskRuntimeValidationGateRepairWindowPhase(val wireValue: String) {
  NONE("none"),
  FINDINGS_OPEN("findings_open"),
  ;

  companion object {
    fun fromWire(value: String?): FeatureTaskRuntimeValidationGateRepairWindowPhase = when (value) {
      null, NONE.wireValue -> NONE
      FINDINGS_OPEN.wireValue -> FINDINGS_OPEN
      else -> throw InvalidWorkflowStateSchemaError(
        "FeatureTaskRuntimeValidationGateProgress.repair_window_phase must be 'none' or 'findings_open'.",
      )
    }
  }
}

data class FeatureTaskRuntimeValidationGateRunRecord(
  val durationMs: Long,
  val outcome: ValidationGateRunOutcome,
  val cacheMode: ValidationGateCacheMode,
  val executedWorkUnits: Int,
  val executedChecks: List<String> = emptyList(),
  val command: String? = null,
  val exitCode: Int? = null,
  val executedChecksRecorded: Boolean = true,
) {
  constructor(
    durationMs: Long,
    outcome: String,
    cacheMode: String,
    executedWorkUnits: Int,
    executedChecks: List<String> = emptyList(),
    command: String? = null,
    exitCode: Int? = null,
    executedChecksRecorded: Boolean = true,
  ) : this(
    durationMs = durationMs,
    outcome = requireNotNull(ValidationGateRunOutcome.fromWire(outcome)) {
      "Unknown validation gate outcome '$outcome'."
    },
    cacheMode = requireNotNull(ValidationGateCacheMode.fromWire(cacheMode)) {
      "Unknown validation gate cache mode '$cacheMode'."
    },
    executedWorkUnits = executedWorkUnits,
    executedChecks = executedChecks,
    command = command,
    exitCode = exitCode,
    executedChecksRecorded = executedChecksRecorded,
  )

  init {
    require(durationMs >= 0) {
      "Validation gate duration_ms must be >= 0, was $durationMs."
    }
    require(executedWorkUnits >= 0) {
      "Validation gate executed_work_units must be >= 0, was $executedWorkUnits."
    }
    require((command == null) == (exitCode == null)) {
      "Validation gate command and exit_code must be present together."
    }
    require(executedChecks.all { it.isNotBlank() }) {
      "Validation gate executed check identities must be non-blank."
    }
  }
  internal fun toArtifactMap(): Map<String, Any?> = linkedMapOf<String, Any?>(
    ValidationEvidencePayloadKeys.DURATION_MS to durationMs,
    ValidationEvidencePayloadKeys.OUTCOME to outcome.wireValue,
    ValidationEvidencePayloadKeys.CACHE_MODE to cacheMode.wireValue,
    ValidationEvidencePayloadKeys.EXECUTED_WORK_UNITS to executedWorkUnits,
  ).apply {
    if (executedChecksRecorded) {
      put(ValidationEvidencePayloadKeys.EXECUTED_CHECKS, executedChecks)
    }
    command?.let { put(ValidationEvidencePayloadKeys.COMMAND, it) }
    exitCode?.let { put(ValidationEvidencePayloadKeys.EXIT_CODE, it) }
  }
}

data class FeatureTaskRuntimeValidationGateProgress(
  val gateRunCount: Int,
  val gateRuns: List<FeatureTaskRuntimeValidationGateRunRecord>,
  val remainingFindings: List<Map<String, String?>> = emptyList(),
  val completeFindings: List<Map<String, String?>> = emptyList(),
  val repairWindowPhase: FeatureTaskRuntimeValidationGateRepairWindowPhase =
    FeatureTaskRuntimeValidationGateRepairWindowPhase.NONE,
  val repairsUsed: Int = 0,
  val capturedTriagePlan: String? = null,
) {
  init {
    require(gateRunCount >= 0) {
      "FeatureTaskRuntimeValidationGateProgress.gateRunCount must be >= 0, was $gateRunCount."
    }
    require(gateRuns.size <= gateRunCount) {
      "FeatureTaskRuntimeValidationGateProgress.gateRuns size ${gateRuns.size} exceeds gateRunCount $gateRunCount."
    }
    require(repairsUsed >= 0) {
      "FeatureTaskRuntimeValidationGateProgress.repairsUsed must be >= 0, was $repairsUsed."
    }
  }
  internal fun toArtifactMap(): Map<String, Any?> = linkedMapOf(
    SharedPayloadKeys.CONTRACT_VERSION to FEATURE_TASK_RUNTIME_PERSISTENCE_CONTRACT_VERSION,
    "gate_run_count" to gateRunCount,
    "gate_runs" to gateRuns.map { it.toArtifactMap() },
    "remaining_findings" to remainingFindings,
    "complete_findings" to completeFindings,
    "repair_window_phase" to repairWindowPhase.wireValue,
    "repairs_used" to repairsUsed,
    "captured_triage_plan" to capturedTriagePlan,
  )

  companion object {
    internal fun fromArtifactMap(raw: Map<String, Any?>): FeatureTaskRuntimeValidationGateProgress =
      FeatureTaskRuntimeValidationGateProgress(
        gateRunCount = raw.asStarMap().gateProgressInt("gate_run_count"),
        gateRuns = decodeGateRuns(raw["gate_runs"]),
        remainingFindings = decodeFindings(raw["remaining_findings"], "remaining_findings"),
        completeFindings = decodeFindings(raw["complete_findings"], "complete_findings"),
        repairWindowPhase = FeatureTaskRuntimeValidationGateRepairWindowPhase.fromWire(
          raw["repair_window_phase"] as? String,
        ),
        repairsUsed = raw.asStarMap().gateProgressOptionalInt("repairs_used") ?: 0,
        capturedTriagePlan = raw["captured_triage_plan"] as? String,
      )

    private fun decodeGateRuns(raw: Any?): List<FeatureTaskRuntimeValidationGateRunRecord> {
      val runsRaw = raw as? List<*>
        ?: throw InvalidWorkflowStateSchemaError(
          "FeatureTaskRuntimeValidationGateProgress is missing gate_runs.",
        )
      return runsRaw.mapIndexed { index, entry ->
        val map = entry as? Map<*, *>
          ?: throw InvalidWorkflowStateSchemaError(
            "FeatureTaskRuntimeValidationGateProgress.gate_runs[$index] must be a mapping.",
          )
        FeatureTaskRuntimeValidationGateRunRecord(
          durationMs = map.gateProgressLong(ValidationEvidencePayloadKeys.DURATION_MS),
          outcome = requireNotNull(
            ValidationGateRunOutcome.fromWire(map.gateProgressString(ValidationEvidencePayloadKeys.OUTCOME)),
          ) {
            "Unknown validation gate outcome."
          },
          cacheMode = requireNotNull(
            ValidationGateCacheMode.fromWire(map.gateProgressString(ValidationEvidencePayloadKeys.CACHE_MODE)),
          ) {
            "Unknown validation gate cache mode."
          },
          executedWorkUnits = map.gateProgressInt(ValidationEvidencePayloadKeys.EXECUTED_WORK_UNITS),
          executedChecks = decodeExecutedChecks(map),
          command = map.gateProgressOptionalString(ValidationEvidencePayloadKeys.COMMAND),
          exitCode = map.gateProgressOptionalInt(ValidationEvidencePayloadKeys.EXIT_CODE),
          executedChecksRecorded = map.containsKey(ValidationEvidencePayloadKeys.EXECUTED_CHECKS),
        )
      }
    }

    private fun decodeExecutedChecks(map: Map<*, *>): List<String> {
      if (!map.containsKey(ValidationEvidencePayloadKeys.EXECUTED_CHECKS)) return emptyList()
      val raw = map[ValidationEvidencePayloadKeys.EXECUTED_CHECKS]
      val list = raw as? List<*>
        ?: throw InvalidWorkflowStateSchemaError(
          "FeatureTaskRuntimeValidationGateProgress gate run executed_checks must be a list.",
        )
      return list.mapIndexed { index, entry ->
        entry as? String ?: throw InvalidWorkflowStateSchemaError(
          "FeatureTaskRuntimeValidationGateProgress gate run executed_checks[$index] must be a string.",
        )
      }
    }

    private fun decodeFindings(raw: Any?, field: String): List<Map<String, String?>> {
      if (raw == null) return emptyList()
      val list = raw as? List<*>
        ?: throw InvalidWorkflowStateSchemaError(
          "FeatureTaskRuntimeValidationGateProgress.$field must be a list.",
        )
      return list.mapIndexed { index, entry ->
        val map = entry as? Map<*, *>
          ?: throw InvalidWorkflowStateSchemaError(
            "FeatureTaskRuntimeValidationGateProgress.$field[$index] must be a mapping.",
          )
        linkedMapOf(
          "module" to (map["module"] as? String),
          "rule_or_test_id" to (map["rule_or_test_id"] as? String),
          "message" to (map["message"] as? String),
          "location" to (map["location"] as? String),
        )
      }
    }
  }
}

internal fun Map<String, Any?>.asStarMap(): Map<*, *> = this

internal fun Map<*, *>.gateProgressString(key: String): String =
  this[key] as? String ?: throw InvalidWorkflowStateSchemaError("Missing required string field '$key'.")

internal fun Map<*, *>.gateProgressInt(key: String): Int = when (val value = this[key]) {
  is Int -> value
  is Long -> value.toInt()
  is Number -> value.toInt()
  else -> throw InvalidWorkflowStateSchemaError("Missing required int field '$key'.")
}

internal fun Map<*, *>.gateProgressLong(key: String): Long = when (val value = this[key]) {
  is Long -> value
  is Int -> value.toLong()
  is Number -> value.toLong()
  else -> throw InvalidWorkflowStateSchemaError("Missing required long field '$key'.")
}

internal fun Map<*, *>.gateProgressOptionalInt(key: String): Int? {
  if (!containsKey(key) || this[key] == null) {
    return null
  }
  return gateProgressInt(key)
}

internal fun Map<*, *>.gateProgressOptionalString(key: String): String? {
  if (!containsKey(key) || this[key] == null) return null
  return gateProgressString(key)
}
