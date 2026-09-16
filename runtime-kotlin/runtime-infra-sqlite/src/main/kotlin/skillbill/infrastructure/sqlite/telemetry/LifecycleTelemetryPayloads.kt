package skillbill.infrastructure.sqlite.telemetry

import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.telemetry.AGENT_CONTEXT_MEASUREMENT_GRAIN_DISTINCT_PER_RUN
import skillbill.contracts.telemetry.AUDIT_GAP_MEASUREMENT_GRAIN_PER_RUN
import skillbill.contracts.telemetry.LifecycleSessionCompletion
import skillbill.contracts.telemetry.LifecycleTelemetryPayloadKeys
import skillbill.contracts.telemetry.TelemetryMeasurementAvailability
import skillbill.error.ShellContentContractException
import skillbill.review.normalizeRoutedSkill
import skillbill.review.normalizeStackLabel
import skillbill.telemetry.model.PrDescriptionGeneratedRecord
import java.util.logging.Logger

private val lifecycleTelemetryPayloadLog: Logger =
  Logger.getLogger("skillbill.telemetry.lifecycle.payload")

fun featureTaskRuntimeStartedPayload(row: Map<String, Any?>, level: String, salt: String): Map<String, Any?> =
  linkedMapOf<String, Any?>(
    "session_id" to row.stringOrEmpty("session_id"),
    "feature_size" to row.stringOrEmpty("feature_size"),
    SharedPayloadKeys.ISSUE_KEY to redactIssueKey(row.stringOrEmpty("issue_key"), level, salt),
  ).apply {
    putAll(correlationFields(row, level, salt))
    if (level == "full") {
      put("feature_name", row.stringOrEmpty("feature_name"))
    }
  }

private fun correlationFields(row: Map<String, Any?>, level: String, salt: String): Map<String, Any?> {
  val workflowId = row.stringOrEmpty("workflow_id")
  val issueKey = row.stringOrEmpty("issue_key")
  val parentWorkflowId = row.stringOrEmpty("goal_parent_workflow_id")
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

fun featureTaskRuntimeFinishedPayload(row: Map<String, Any?>, level: String, salt: String): Map<String, Any?> =
  linkedMapOf<String, Any?>("session_id" to row.stringOrEmpty("session_id")).apply {
    putAll(correlationFields(row, level, salt))
    put("completion_status", row.stringOrEmpty("completion_status"))
    put(
      "completed_phase_ids",
      parseStoredJsonArray(row.stringOrEmpty("completed_phase_ids"), "completed_phase_ids"),
    )
    put("phase_outcomes", parsePhaseOutcomes(row.stringOrEmpty("phase_outcomes")))
    put("review_fix_iteration_count", row.intOrZero("review_fix_iteration_count"))
    put("finding_verification_verified_count", row.intOrZero("finding_verification_verified_count"))
    put("finding_verification_rejected_count", row.intOrZero("finding_verification_rejected_count"))
    putAll(reviewFixCapExhaustionFields(row))
    putAll(auditGapFields(row))
    putAll(agentContextFields(row))
    put("regeneration_activation_count", row.intOrZero("regeneration_activation_count"))
    put("regeneration_attempt_count", row.intOrZero("regeneration_attempt_count"))
    put("regeneration_outcome_counts", parsePhaseOutcomes(row.stringOrEmpty("regeneration_outcome_counts_json")))
    put("crash_reconciliation_count", row.intOrZero("crash_reconciliation_count"))
    put(
      "crash_reconciliation_reason_counts",
      parsePhaseOutcomes(row.stringOrEmpty("crash_reconciliation_reason_counts_json")),
    )
    put("last_incomplete_phase", row.stringOrEmpty("last_incomplete_phase"))
    put("blocked_reason", row.stringOrEmpty("blocked_reason"))
    put("duration_seconds", durationSeconds(row))
    row.stringOrEmpty(LifecycleTelemetryPayloadKeys.STALE_REASON).takeIf(String::isNotBlank)?.let {
      put(LifecycleTelemetryPayloadKeys.STALE_REASON, it)
    }
    if (level == "full") {
      put("resolved_branch", row.stringOrEmpty("resolved_branch"))
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

private fun parseStoredJsonArray(rawValue: String, fieldName: String): List<Any?> {
  if (rawValue.isBlank()) {
    return emptyList()
  }
  return try {
    JsonCodec.parseJsonArrayStrict(rawValue.trim())
  } catch (_: ShellContentContractException) {
    lifecycleTelemetryPayloadLog.warning(
      "skillbill telemetry: degraded malformed JSON array in $fieldName",
    )
    emptyList()
  }
}

fun qualityCheckStartedPayload(row: Map<String, Any?>): Map<String, Any?> {
  val normalizedStack = normalizeStackLabel(row.stringOrEmpty("detected_stack"))
  val fallback = row.booleanFromInt("fallback") || normalizedStack.fallback
  return linkedMapOf<String, Any?>(
    "session_id" to row.stringOrEmpty("session_id"),
    "routed_skill" to normalizeRoutedSkill(row.stringOrEmpty("routed_skill")),
    "detected_stack" to normalizedStack.stack,
    "fallback" to fallback,
    "scope_type" to row.stringOrEmpty("scope_type"),
    "initial_failure_count" to row.intOrZero("initial_failure_count"),
    "orchestrated" to false,
  ).apply {
    val fallbackReason = row.stringOrEmpty("fallback_reason").ifBlank { normalizedStack.fallbackReason.orEmpty() }
    if (fallback && fallbackReason.isNotBlank()) {
      put("fallback_reason", fallbackReason)
    }
  }
}

fun qualityCheckFinishedPayload(row: Map<String, Any?>, level: String): Map<String, Any?> {
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
    put(LifecycleTelemetryPayloadKeys.DURATION_SECONDS, durationSeconds(row))
    row.stringOrEmpty(LifecycleTelemetryPayloadKeys.STALE_REASON).takeIf(String::isNotBlank)?.let {
      put(LifecycleTelemetryPayloadKeys.STALE_REASON, it)
    }
    if (level == "full") {
      put(
        LifecycleTelemetryPayloadKeys.FAILING_CHECK_NAMES,
        parseStoredJsonArray(
          row.stringOrEmpty(LifecycleTelemetryPayloadKeys.FAILING_CHECK_NAMES),
          LifecycleTelemetryPayloadKeys.FAILING_CHECK_NAMES,
        ),
      )
      put(
        LifecycleTelemetryPayloadKeys.UNSUPPORTED_REASON,
        row.stringOrEmpty(LifecycleTelemetryPayloadKeys.UNSUPPORTED_REASON),
      )
    }
  }
}

const val STALE_RESULT: String = "stale"

fun featureVerifyStartedPayload(row: Map<String, Any?>, level: String): Map<String, Any?> = linkedMapOf<String, Any?>(
  "session_id" to row.stringOrEmpty("session_id"),
  "acceptance_criteria_count" to row.intOrZero("acceptance_criteria_count"),
  "rollout_relevant" to row.booleanFromInt("rollout_relevant"),
  "orchestrated" to false,
).apply {
  if (level == "full") {
    put("spec_summary", row.stringOrEmpty("spec_summary"))
  }
}

fun featureVerifyFinishedPayload(row: Map<String, Any?>, level: String): Map<String, Any?> =
  featureVerifyStartedPayload(row, level).toMutableMap().apply {
    put("feature_flag_audit_performed", row.booleanFromInt("feature_flag_audit_performed"))
    put("review_iterations", row.intOrZero("review_iterations"))
    put("audit_result", row.stringOrEmpty("audit_result").ifBlank { "skipped" })
    put("completion_status", row.stringOrEmpty("completion_status"))
    put("history_relevance", row.stringOrEmpty("history_relevance").ifBlank { "none" })
    put("history_helpfulness", row.stringOrEmpty("history_helpfulness").ifBlank { "none" })
    put("duration_seconds", durationSeconds(row))
    if (level == "full") {
      put(
        "gaps_found",
        parseStoredJsonArray(row.stringOrEmpty("gaps_found"), "gaps_found"),
      )
    }
  }

fun prDescriptionPayload(record: PrDescriptionGeneratedRecord, level: String): Map<String, Any?> =
  linkedMapOf<String, Any?>(
    "session_id" to record.sessionId,
    "commit_count" to record.commitCount,
    "files_changed_count" to record.filesChangedCount,
    "was_edited_by_user" to record.wasEditedByUser,
    "pr_created" to record.prCreated,
  ).apply {
    if (level == "full") {
      put("pr_title", record.prTitle)
    }
  }
