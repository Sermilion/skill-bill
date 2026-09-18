package skillbill.infrastructure.sqlite.telemetry
import skillbill.contracts.telemetry.GoalTelemetryPayloadKeys
import skillbill.contracts.telemetry.SqliteLifecycleTelemetryMaterializationPayloadKeys
import skillbill.contracts.review.SqliteReviewTelemetryPayloadKeys

import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.telemetry.AGENT_CONTEXT_MEASUREMENT_GRAIN_DISTINCT_PER_RUN
import skillbill.contracts.telemetry.AUDIT_GAP_MEASUREMENT_GRAIN_PER_RUN
import skillbill.contracts.telemetry.LifecycleSessionCompletion
import skillbill.contracts.telemetry.LifecycleTelemetryPayloadKeys
import skillbill.contracts.telemetry.TelemetryMeasurementAvailability
import skillbill.error.ShellContentContractException
import skillbill.infrastructure.sqlite.core.InternalSqliteDiagnostics
import skillbill.infrastructure.sqlite.core.recordDegradedValue
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.review.normalizeRoutedSkill
import skillbill.review.normalizeStackLabel
import skillbill.telemetry.model.PrDescriptionGeneratedRecord

internal fun featureTaskRuntimeStartedPayload(row: Map<String, Any?>, level: String, salt: String): Map<String, Any?> =
  linkedMapOf<String, Any?>(
    LifecycleTelemetryPayloadKeys.SESSION_ID to row.stringOrEmpty(LifecycleTelemetryPayloadKeys.SESSION_ID),
    SqliteLifecycleTelemetryMaterializationPayloadKeys.FEATURE_SIZE to row.stringOrEmpty(SqliteLifecycleTelemetryMaterializationPayloadKeys.FEATURE_SIZE),
    SharedPayloadKeys.ISSUE_KEY to redactIssueKey(row.stringOrEmpty(SharedPayloadKeys.ISSUE_KEY), level, salt),
  ).apply {
    putAll(correlationFields(row, level, salt))
    if (level == "full") {
      put(GoalTelemetryPayloadKeys.FEATURE_NAME, row.stringOrEmpty(GoalTelemetryPayloadKeys.FEATURE_NAME))
    }
  }

private fun correlationFields(row: Map<String, Any?>, level: String, salt: String): Map<String, Any?> {
  val workflowId = row.stringOrEmpty(SharedPayloadKeys.WORKFLOW_ID)
  val issueKey = row.stringOrEmpty(SharedPayloadKeys.ISSUE_KEY)
  val parentWorkflowId = row.stringOrEmpty(LifecycleTelemetryPayloadKeys.GOAL_PARENT_WORKFLOW_ID)
  val availability = if (workflowId.isBlank()) {
    TelemetryMeasurementAvailability.UNKNOWN
  } else {
    TelemetryMeasurementAvailability.MEASURED
  }
  return linkedMapOf(
    LifecycleTelemetryPayloadKeys.CORRELATION_AVAILABILITY to availability.wireValue,
    LifecycleTelemetryPayloadKeys.REDACTED_WORKFLOW_ID to
      workflowId.takeIf { it.isNotBlank() }?.let { redactIssueKeyReferences(it, issueKey, level, salt) },
    LifecycleTelemetryPayloadKeys.GOAL_PARENT_WORKFLOW_ID to
      parentWorkflowId.takeIf { it.isNotBlank() }?.let { redactIssueKeyReferences(it, issueKey, level, salt) },
    LifecycleTelemetryPayloadKeys.GOAL_SUBTASK_ID to row.nullableInt(LifecycleTelemetryPayloadKeys.GOAL_SUBTASK_ID),
  )
}

internal fun featureTaskRuntimeFinishedPayload(
  row: Map<String, Any?>,
  level: String,
  salt: String,
  diagnostics: RuntimeDiagnostics = InternalSqliteDiagnostics,
): Map<String, Any?> =
  linkedMapOf<String, Any?>(LifecycleTelemetryPayloadKeys.SESSION_ID to row.stringOrEmpty(LifecycleTelemetryPayloadKeys.SESSION_ID)).apply {
    putAll(correlationFields(row, level, salt))
    put(LifecycleTelemetryPayloadKeys.COMPLETION_STATUS, row.stringOrEmpty(LifecycleTelemetryPayloadKeys.COMPLETION_STATUS))
    put(
      SqliteReviewTelemetryPayloadKeys.COMPLETED_PHASE_IDS,
      parseStoredJsonArray(
        row.stringOrEmpty(SqliteReviewTelemetryPayloadKeys.COMPLETED_PHASE_IDS),
        SqliteReviewTelemetryPayloadKeys.COMPLETED_PHASE_IDS,
        diagnostics,
      ),
    )
    put(SqliteLifecycleTelemetryMaterializationPayloadKeys.PHASE_OUTCOMES, parsePhaseOutcomes(row.stringOrEmpty(SqliteLifecycleTelemetryMaterializationPayloadKeys.PHASE_OUTCOMES)))
    put(SqliteLifecycleTelemetryMaterializationPayloadKeys.REVIEW_FIX_ITERATION_COUNT, row.intOrZero(SqliteLifecycleTelemetryMaterializationPayloadKeys.REVIEW_FIX_ITERATION_COUNT))
    put(SqliteLifecycleTelemetryMaterializationPayloadKeys.FINDING_VERIFICATION_VERIFIED_COUNT, row.intOrZero(SqliteLifecycleTelemetryMaterializationPayloadKeys.FINDING_VERIFICATION_VERIFIED_COUNT))
    put(SqliteLifecycleTelemetryMaterializationPayloadKeys.FINDING_VERIFICATION_REJECTED_COUNT, row.intOrZero(SqliteLifecycleTelemetryMaterializationPayloadKeys.FINDING_VERIFICATION_REJECTED_COUNT))
    putAll(reviewFixCapExhaustionFields(row))
    putAll(auditGapFields(row))
    putAll(agentContextFields(row))
    put(SqliteLifecycleTelemetryMaterializationPayloadKeys.REGENERATION_ACTIVATION_COUNT, row.intOrZero(SqliteLifecycleTelemetryMaterializationPayloadKeys.REGENERATION_ACTIVATION_COUNT))
    put(SqliteLifecycleTelemetryMaterializationPayloadKeys.REGENERATION_ATTEMPT_COUNT, row.intOrZero(SqliteLifecycleTelemetryMaterializationPayloadKeys.REGENERATION_ATTEMPT_COUNT))
    put(SqliteLifecycleTelemetryMaterializationPayloadKeys.REGENERATION_OUTCOME_COUNTS, parsePhaseOutcomes(row.stringOrEmpty("regeneration_outcome_counts_json")))
    put(SqliteLifecycleTelemetryMaterializationPayloadKeys.CRASH_RECONCILIATION_COUNT, row.intOrZero(SqliteLifecycleTelemetryMaterializationPayloadKeys.CRASH_RECONCILIATION_COUNT))
    put(
      "crash_reconciliation_reason_counts",
      parsePhaseOutcomes(row.stringOrEmpty("crash_reconciliation_reason_counts_json")),
    )
    put(SqliteLifecycleTelemetryMaterializationPayloadKeys.LAST_INCOMPLETE_PHASE, row.stringOrEmpty(SqliteLifecycleTelemetryMaterializationPayloadKeys.LAST_INCOMPLETE_PHASE))
    put(GoalTelemetryPayloadKeys.BLOCKED_REASON, row.stringOrEmpty(GoalTelemetryPayloadKeys.BLOCKED_REASON))
    put(LifecycleTelemetryPayloadKeys.DURATION_SECONDS, durationSeconds(row, diagnostics))
    row.stringOrEmpty(LifecycleTelemetryPayloadKeys.STALE_REASON).takeIf(String::isNotBlank)?.let {
      put(LifecycleTelemetryPayloadKeys.STALE_REASON, it)
    }
    if (level == "full") {
      put(SqliteLifecycleTelemetryMaterializationPayloadKeys.RESOLVED_BRANCH, row.stringOrEmpty(SqliteLifecycleTelemetryMaterializationPayloadKeys.RESOLVED_BRANCH))
    }
  }

private fun reviewFixCapExhaustionFields(row: Map<String, Any?>): Map<String, Any?> {
  val availability = row.availability(LifecycleTelemetryPayloadKeys.REVIEW_FIX_CAP_EXHAUSTED_AVAILABILITY)
  val exhausted = if (availability.measured) {
    row.intOrZero(LifecycleTelemetryPayloadKeys.REVIEW_FIX_CAP_EXHAUSTED) == 1
  } else {
    null
  }
  return linkedMapOf(
    LifecycleTelemetryPayloadKeys.REVIEW_FIX_CAP_EXHAUSTED_AVAILABILITY to availability.wireValue,
    LifecycleTelemetryPayloadKeys.REVIEW_FIX_CAP_EXHAUSTED to exhausted,
  )
}

private fun auditGapFields(row: Map<String, Any?>): Map<String, Any?> {
  val availability = row.availability(LifecycleTelemetryPayloadKeys.AUDIT_GAP_AVAILABILITY)
  val iterations = row.nullableInt(LifecycleTelemetryPayloadKeys.AUDIT_GAP_ITERATION_COUNT)
    .takeIf { availability.measured }
  return linkedMapOf(
    LifecycleTelemetryPayloadKeys.AUDIT_GAP_AVAILABILITY to availability.wireValue,
    LifecycleTelemetryPayloadKeys.AUDIT_GAP_MEASUREMENT_GRAIN to AUDIT_GAP_MEASUREMENT_GRAIN_PER_RUN,
    LifecycleTelemetryPayloadKeys.AUDIT_GAP_ITERATION_COUNT to iterations,
    LifecycleTelemetryPayloadKeys.AUDIT_FIRST_PASS_CONVERGENCE to iterations?.let { it == 0 },
    LifecycleTelemetryPayloadKeys.AUDIT_REPAIR_ITEM_AVAILABILITY to
      TelemetryMeasurementAvailability.UNAVAILABLE_UNSUPPORTED.wireValue,
  )
}

private fun agentContextFields(row: Map<String, Any?>): Map<String, Any?> {
  val agents = row.nameList(LifecycleTelemetryPayloadKeys.RESOLVED_AGENT_IDS)
  val models = row.nameList(LifecycleTelemetryPayloadKeys.LAUNCHED_MODELS)
  return linkedMapOf(
    LifecycleTelemetryPayloadKeys.AGENT_CONTEXT_MEASUREMENT_GRAIN to AGENT_CONTEXT_MEASUREMENT_GRAIN_DISTINCT_PER_RUN,
    LifecycleTelemetryPayloadKeys.RESOLVED_AGENT_AVAILABILITY to agents.availability().wireValue,
    LifecycleTelemetryPayloadKeys.RESOLVED_AGENT_IDS to agents.values.takeIf { it.isNotEmpty() },
    LifecycleTelemetryPayloadKeys.LAUNCHED_MODEL_AVAILABILITY to models.availability().wireValue,
    LifecycleTelemetryPayloadKeys.LAUNCHED_MODELS to models.values.takeIf { it.isNotEmpty() },
  )
}

private data class ParsedNameList(val values: List<Any?>, val corrupt: Boolean)

private fun Map<String, Any?>.nameList(name: String): ParsedNameList {
  val raw = stringOrEmpty(name)
  if (raw.isBlank()) {
    return ParsedNameList(values = emptyList(), corrupt = false)
  }
  return try {
    ParsedNameList(values = JsonCodec.parseJsonArrayStrict(raw.trim()), corrupt = false)
  } catch (_: ShellContentContractException) {
    ParsedNameList(values = emptyList(), corrupt = true)
  }
}

private fun ParsedNameList.availability(): TelemetryMeasurementAvailability = when {
  corrupt -> TelemetryMeasurementAvailability.UNAVAILABLE_INCOMPLETE
  values.isEmpty() -> TelemetryMeasurementAvailability.UNAVAILABLE_NO_DURABLE_STATE
  else -> TelemetryMeasurementAvailability.MEASURED
}

private fun parsePhaseOutcomes(rawValue: String): Map<String, Any?> = JsonCodec.parseObjectOrNull(rawValue)
  ?.mapValues { (_, value) -> JsonCodec.jsonElementToValue(value) }
  .orEmpty()

private fun parseStoredJsonArray(
  rawValue: String,
  fieldName: String,
  diagnostics: RuntimeDiagnostics,
): List<Any?> {
  if (rawValue.isBlank()) {
    return emptyList()
  }
  return try {
    JsonCodec.parseJsonArrayStrict(rawValue.trim())
  } catch (error: ShellContentContractException) {
    diagnostics.recordDegradedValue(
      seam = "telemetry.json_array.$fieldName",
      expected = "strict JSON array",
      used = rawValue.take(120),
      error = error,
    )
    emptyList()
  }
}

internal fun qualityCheckStartedPayload(row: Map<String, Any?>): Map<String, Any?> {
  val normalizedStack = normalizeStackLabel(row.stringOrEmpty(LifecycleTelemetryPayloadKeys.DETECTED_STACK))
  val fallback = row.booleanFromInt(LifecycleTelemetryPayloadKeys.FALLBACK) || normalizedStack.fallback
  return linkedMapOf<String, Any?>(
    LifecycleTelemetryPayloadKeys.SESSION_ID to row.stringOrEmpty(LifecycleTelemetryPayloadKeys.SESSION_ID),
    LifecycleTelemetryPayloadKeys.ROUTED_SKILL to normalizeRoutedSkill(row.stringOrEmpty(LifecycleTelemetryPayloadKeys.ROUTED_SKILL)),
    LifecycleTelemetryPayloadKeys.DETECTED_STACK to normalizedStack.stack,
    LifecycleTelemetryPayloadKeys.FALLBACK to fallback,
    LifecycleTelemetryPayloadKeys.SCOPE_TYPE to row.stringOrEmpty(LifecycleTelemetryPayloadKeys.SCOPE_TYPE),
    LifecycleTelemetryPayloadKeys.INITIAL_FAILURE_COUNT to row.intOrZero(LifecycleTelemetryPayloadKeys.INITIAL_FAILURE_COUNT),
    SqliteLifecycleTelemetryMaterializationPayloadKeys.ORCHESTRATED to false,
  ).apply {
    val fallbackReason = row.stringOrEmpty(LifecycleTelemetryPayloadKeys.FALLBACK_REASON).ifBlank { normalizedStack.fallbackReason.orEmpty() }
    if (fallback && fallbackReason.isNotBlank()) {
      put(LifecycleTelemetryPayloadKeys.FALLBACK_REASON, fallbackReason)
    }
  }
}

internal fun qualityCheckFinishedPayload(
  row: Map<String, Any?>,
  level: String,
  diagnostics: RuntimeDiagnostics = InternalSqliteDiagnostics,
): Map<String, Any?> {
  val result = row.stringOrEmpty(LifecycleTelemetryPayloadKeys.RESULT).ifBlank { "skipped" }
  val reconcilerStale = result == STALE_RESULT
  val finalFailureCount = row.nullableInt(LifecycleTelemetryPayloadKeys.FINAL_FAILURE_COUNT)
    .takeUnless { reconcilerStale }
  return qualityCheckStartedPayload(row).toMutableMap().apply {
    put(LifecycleTelemetryPayloadKeys.FINAL_FAILURE_COUNT, finalFailureCount)
    put(
      LifecycleTelemetryPayloadKeys.FINAL_FAILURE_COUNT_AVAILABILITY,
      when {
        reconcilerStale -> TelemetryMeasurementAvailability.UNAVAILABLE_INCOMPLETE
        finalFailureCount == null -> TelemetryMeasurementAvailability.UNKNOWN
        else -> TelemetryMeasurementAvailability.MEASURED
      }.wireValue,
    )
    put(
      LifecycleTelemetryPayloadKeys.COMPLETION,
      when (reconcilerStale) {
        true -> LifecycleSessionCompletion.RECONCILER_STALE
        false -> LifecycleSessionCompletion.OPERATOR_COMPLETED
      }.wireValue,
    )
    put(LifecycleTelemetryPayloadKeys.ITERATIONS, row.intOrZero(LifecycleTelemetryPayloadKeys.ITERATIONS))
    put(LifecycleTelemetryPayloadKeys.RESULT, result)
    put(LifecycleTelemetryPayloadKeys.DURATION_SECONDS, durationSeconds(row, diagnostics))
    row.stringOrEmpty(LifecycleTelemetryPayloadKeys.STALE_REASON).takeIf(String::isNotBlank)?.let {
      put(LifecycleTelemetryPayloadKeys.STALE_REASON, it)
    }
    if (level == "full") {
      put(
        LifecycleTelemetryPayloadKeys.FAILING_CHECK_NAMES,
        parseStoredJsonArray(
          row.stringOrEmpty(LifecycleTelemetryPayloadKeys.FAILING_CHECK_NAMES),
          LifecycleTelemetryPayloadKeys.FAILING_CHECK_NAMES,
          diagnostics,
        ),
      )
      put(
        LifecycleTelemetryPayloadKeys.UNSUPPORTED_REASON,
        row.stringOrEmpty(LifecycleTelemetryPayloadKeys.UNSUPPORTED_REASON),
      )
    }
  }
}

internal const val STALE_RESULT: String = "stale"

internal fun featureVerifyStartedPayload(row: Map<String, Any?>, level: String): Map<String, Any?> = linkedMapOf<String, Any?>(
  LifecycleTelemetryPayloadKeys.SESSION_ID to row.stringOrEmpty(LifecycleTelemetryPayloadKeys.SESSION_ID),
  LifecycleTelemetryPayloadKeys.ACCEPTANCE_CRITERIA_COUNT to row.intOrZero(LifecycleTelemetryPayloadKeys.ACCEPTANCE_CRITERIA_COUNT),
  LifecycleTelemetryPayloadKeys.ROLLOUT_RELEVANT to row.booleanFromInt(LifecycleTelemetryPayloadKeys.ROLLOUT_RELEVANT),
  SqliteLifecycleTelemetryMaterializationPayloadKeys.ORCHESTRATED to false,
).apply {
  if (level == "full") {
    put(LifecycleTelemetryPayloadKeys.SPEC_SUMMARY, row.stringOrEmpty(LifecycleTelemetryPayloadKeys.SPEC_SUMMARY))
  }
}

internal fun featureVerifyFinishedPayload(
  row: Map<String, Any?>,
  level: String,
  diagnostics: RuntimeDiagnostics = InternalSqliteDiagnostics,
): Map<String, Any?> =
  featureVerifyStartedPayload(row, level).toMutableMap().apply {
    put(LifecycleTelemetryPayloadKeys.FEATURE_FLAG_AUDIT_PERFORMED, row.booleanFromInt(LifecycleTelemetryPayloadKeys.FEATURE_FLAG_AUDIT_PERFORMED))
    put(LifecycleTelemetryPayloadKeys.REVIEW_ITERATIONS, row.intOrZero(LifecycleTelemetryPayloadKeys.REVIEW_ITERATIONS))
    put(LifecycleTelemetryPayloadKeys.AUDIT_RESULT, row.stringOrEmpty(LifecycleTelemetryPayloadKeys.AUDIT_RESULT).ifBlank { "skipped" })
    put(LifecycleTelemetryPayloadKeys.COMPLETION_STATUS, row.stringOrEmpty(LifecycleTelemetryPayloadKeys.COMPLETION_STATUS))
    put(LifecycleTelemetryPayloadKeys.HISTORY_RELEVANCE, row.stringOrEmpty(LifecycleTelemetryPayloadKeys.HISTORY_RELEVANCE).ifBlank { "none" })
    put(LifecycleTelemetryPayloadKeys.HISTORY_HELPFULNESS, row.stringOrEmpty(LifecycleTelemetryPayloadKeys.HISTORY_HELPFULNESS).ifBlank { "none" })
    put(LifecycleTelemetryPayloadKeys.DURATION_SECONDS, durationSeconds(row, diagnostics))
    if (level == "full") {
      put(
        LifecycleTelemetryPayloadKeys.GAPS_FOUND,
        parseStoredJsonArray(
          row.stringOrEmpty(LifecycleTelemetryPayloadKeys.GAPS_FOUND),
          LifecycleTelemetryPayloadKeys.GAPS_FOUND,
          diagnostics,
        ),
      )
    }
  }

internal fun prDescriptionPayload(record: PrDescriptionGeneratedRecord, level: String): Map<String, Any?> =
  linkedMapOf<String, Any?>(
    LifecycleTelemetryPayloadKeys.SESSION_ID to record.sessionId,
    LifecycleTelemetryPayloadKeys.COMMIT_COUNT to record.commitCount,
    LifecycleTelemetryPayloadKeys.FILES_CHANGED_COUNT to record.filesChangedCount,
    LifecycleTelemetryPayloadKeys.WAS_EDITED_BY_USER to record.wasEditedByUser,
    LifecycleTelemetryPayloadKeys.PR_CREATED to record.prCreated,
  ).apply {
    if (level == "full") {
      put(LifecycleTelemetryPayloadKeys.PR_TITLE, record.prTitle)
    }
  }
