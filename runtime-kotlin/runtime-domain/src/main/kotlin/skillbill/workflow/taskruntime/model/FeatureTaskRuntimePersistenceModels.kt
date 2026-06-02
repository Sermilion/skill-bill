package skillbill.workflow.taskruntime.model

import skillbill.boundary.OpenBoundaryMap
import skillbill.error.InvalidWorkflowStateSchemaError
import java.math.BigDecimal
import java.math.BigInteger

/**
 * SKILL-65 Subtask 2: effect-free per-phase persistence + append-only phase
 * attempt/event ledger domain models for the feature-task-runtime pipeline.
 *
 * These models ride inside the family workflow row's `artifacts_json` envelope
 * (the existing `WorkflowStateRecord` carries it), keyed by
 * [FEATURE_TASK_RUNTIME_PHASE_RECORDS_ARTIFACT_KEY] and
 * [FEATURE_TASK_RUNTIME_PHASE_LEDGER_ARTIFACT_KEY].
 *
 * Effect-purity (mirrors the SKILL-64 goal accounting/ledger models): there is
 * NO clock, random, or logging here. The runtime (application layer) mints every
 * timestamp/duration and passes it in, so the domain never sources time
 * (AC5). The map<->model (de)serialization conforms to the `artifacts_json`
 * key shape, loud-fails on malformed maps with a typed
 * [InvalidWorkflowStateSchemaError] consistent with the `WorkflowEngine` read
 * seam, and introduces NO best-effort parsing (AC6).
 *
 * Append/prune for the ledger reuses the single domain helper
 * `skillbill.workflow.model.appendBoundedHistoryBySequence` at the durable write
 * seam (the application layer), so this file does not duplicate that logic.
 */

const val FEATURE_TASK_RUNTIME_PHASE_RECORDS_ARTIFACT_KEY: String = "feature_task_runtime_phase_records"
const val FEATURE_TASK_RUNTIME_PHASE_LEDGER_ARTIFACT_KEY: String = "feature_task_runtime_phase_ledger"
const val FEATURE_TASK_RUNTIME_PHASE_LEDGER_LIMIT: Int = 200

/**
 * AC3: the durable per-phase record. One entry per phase id captures the
 * runtime-owned facts about that phase's latest persisted state: its status,
 * attempt count, the runtime-minted start/finish timestamps and duration, the
 * resolved agent id, and the validated phase output artifact (the JSON/YAML
 * text already passed the per-phase output validator before reaching here).
 *
 * Effect-free: every timestamp/duration is supplied by the caller, never read
 * from a clock. `finishedAt`/`durationMillis`/`outputArtifact` are nullable
 * because a phase may be persisted while still running (started but not yet
 * finished); a finished phase MUST carry all three.
 */
data class FeatureTaskRuntimePhaseRecord(
  val phaseId: String,
  val status: String,
  val attemptCount: Int,
  val startedAt: String,
  val finishedAt: String? = null,
  val durationMillis: Long? = null,
  val resolvedAgentId: String,
  val outputArtifact: String? = null,
) {
  init {
    require(phaseId.isNotBlank()) { "FeatureTaskRuntimePhaseRecord.phaseId must be non-blank." }
    require(status.isNotBlank()) { "FeatureTaskRuntimePhaseRecord.status must be non-blank." }
    require(attemptCount >= 1) {
      "FeatureTaskRuntimePhaseRecord.attemptCount must be >= 1, was $attemptCount."
    }
    require(startedAt.isNotBlank()) { "FeatureTaskRuntimePhaseRecord.startedAt must be non-blank." }
    require(resolvedAgentId.isNotBlank()) { "FeatureTaskRuntimePhaseRecord.resolvedAgentId must be non-blank." }
    durationMillis?.let { duration ->
      require(duration >= 0) { "FeatureTaskRuntimePhaseRecord.durationMillis must be non-negative, was $duration." }
    }
  }

  @OpenBoundaryMap("Feature-task-runtime per-phase record artifact map at the durable workflow-artifact seam")
  fun toArtifactMap(): Map<String, Any?> = linkedMapOf<String, Any?>(
    "phase_id" to phaseId,
    "status" to status,
    "attempt_count" to attemptCount,
    "started_at" to startedAt,
    "resolved_agent_id" to resolvedAgentId,
  ).apply {
    finishedAt?.let { put("finished_at", it) }
    durationMillis?.let { put("duration_millis", it) }
    outputArtifact?.let { put("output_artifact", it) }
  }

  companion object {
    /**
     * AC6: strict decode of one per-phase record map. Loud-fails with a typed
     * [InvalidWorkflowStateSchemaError] on any missing/malformed required field;
     * no best-effort parsing or silent defaulting of required values.
     */
    @OpenBoundaryMap("Feature-task-runtime per-phase record decode from the durable workflow-artifact map")
    fun fromArtifactMap(raw: Map<String, Any?>): FeatureTaskRuntimePhaseRecord = FeatureTaskRuntimePhaseRecord(
      phaseId = raw.requireStringField("phase_id"),
      status = raw.requireStringField("status"),
      attemptCount = raw.requireIntField("attempt_count"),
      startedAt = raw.requireStringField("started_at"),
      finishedAt = raw.optionalStringField("finished_at"),
      durationMillis = raw.optionalLongField("duration_millis"),
      resolvedAgentId = raw.requireStringField("resolved_agent_id"),
      outputArtifact = raw.optionalStringField("output_artifact"),
    )
  }
}

/**
 * AC4: actions for the append-only phase attempt/event ledger. The set covers
 * phase start, resume, retry, fix-loop iteration, blocked, and complete events
 * (consistent with the SKILL-64 goal attempt-ledger intent).
 */
enum class FeatureTaskRuntimePhaseLedgerAction(val wireValue: String) {
  START("start"),
  RESUME("resume"),
  RETRY("retry"),
  FIX_LOOP_ITERATION("fix_loop_iteration"),
  BLOCKED("blocked"),
  COMPLETE("complete"),
  ;

  companion object {
    fun fromWire(value: String): FeatureTaskRuntimePhaseLedgerAction = entries.firstOrNull { it.wireValue == value }
      ?: throw InvalidWorkflowStateSchemaError(
        "Unknown feature-task-runtime phase ledger action '$value'. " +
          "Allowed: ${entries.joinToString { it.wireValue }}.",
      )
  }
}

/**
 * AC4: one append-only phase ledger entry. Carries a monotonic
 * [sequenceNumber], a runtime-minted [timestamp], the [phaseId], the [action],
 * and runtime-owned facts (attempt count, resolved agent id, an optional
 * fix-loop iteration index, and an optional blocked reason). Effect-free:
 * timestamps are minted in the application layer (AC5), never here.
 */
data class FeatureTaskRuntimePhaseLedgerEntry(
  val action: FeatureTaskRuntimePhaseLedgerAction,
  val sequenceNumber: Int,
  val timestamp: String,
  val phaseId: String,
  val attemptCount: Int,
  val resolvedAgentId: String? = null,
  val fixLoopIteration: Int? = null,
  val blockedReason: String? = null,
) {
  init {
    require(sequenceNumber >= 0) {
      "FeatureTaskRuntimePhaseLedgerEntry.sequenceNumber must be non-negative, was $sequenceNumber."
    }
    require(timestamp.isNotBlank()) { "FeatureTaskRuntimePhaseLedgerEntry.timestamp must be non-blank." }
    require(phaseId.isNotBlank()) { "FeatureTaskRuntimePhaseLedgerEntry.phaseId must be non-blank." }
    require(attemptCount >= 1) {
      "FeatureTaskRuntimePhaseLedgerEntry.attemptCount must be >= 1, was $attemptCount."
    }
    fixLoopIteration?.let { iteration ->
      require(iteration >= 1) {
        "FeatureTaskRuntimePhaseLedgerEntry.fixLoopIteration must be >= 1 when present, was $iteration."
      }
    }
  }

  @OpenBoundaryMap("Feature-task-runtime phase ledger entry artifact map at the durable workflow-artifact seam")
  fun toArtifactMap(): Map<String, Any?> = linkedMapOf<String, Any?>(
    "action" to action.wireValue,
    "sequence_number" to sequenceNumber,
    "timestamp" to timestamp,
    "phase_id" to phaseId,
    "attempt_count" to attemptCount,
  ).apply {
    resolvedAgentId?.let { put("resolved_agent_id", it) }
    fixLoopIteration?.let { put("fix_loop_iteration", it) }
    blockedReason?.let { put("blocked_reason", it) }
  }

  companion object {
    /**
     * AC6: strict decode of one ledger entry map. Loud-fails with a typed
     * [InvalidWorkflowStateSchemaError] on any missing/malformed required field;
     * no best-effort parsing.
     */
    @OpenBoundaryMap("Feature-task-runtime phase ledger entry decode from the durable workflow-artifact map")
    fun fromArtifactMap(raw: Map<String, Any?>): FeatureTaskRuntimePhaseLedgerEntry =
      FeatureTaskRuntimePhaseLedgerEntry(
        action = FeatureTaskRuntimePhaseLedgerAction.fromWire(raw.requireStringField("action")),
        sequenceNumber = raw.requireIntField("sequence_number"),
        timestamp = raw.requireStringField("timestamp"),
        phaseId = raw.requireStringField("phase_id"),
        attemptCount = raw.requireIntField("attempt_count"),
        resolvedAgentId = raw.optionalStringField("resolved_agent_id"),
        fixLoopIteration = raw.optionalIntField("fix_loop_iteration"),
        blockedReason = raw.optionalStringField("blocked_reason"),
      )
  }
}

// SKILL-65 Subtask 2 (AC6): strict, loud-failing field decoders shared by the
// per-phase record and ledger entry. Every helper throws the typed
// InvalidWorkflowStateSchemaError on a missing/wrong-typed required value; the
// optional variants return null only when the key is absent and still loud-fail
// on a present-but-malformed value. This is the schema-error path of the
// WorkflowEngine read seam — never best-effort.

private fun Map<String, Any?>.requireStringField(key: String): String {
  val value = this[key]
    ?: throw InvalidWorkflowStateSchemaError(
      "Feature-task-runtime artifact map is missing required field '$key'.",
    )
  return (value as? String)?.takeIf(String::isNotBlank)
    ?: throw InvalidWorkflowStateSchemaError(
      "Feature-task-runtime artifact field '$key' must decode to a non-blank string.",
    )
}

private fun Map<String, Any?>.optionalStringField(key: String): String? {
  if (!containsKey(key) || this[key] == null) {
    return null
  }
  return (this[key] as? String)?.takeIf(String::isNotBlank)
    ?: throw InvalidWorkflowStateSchemaError(
      "Feature-task-runtime artifact field '$key' must decode to a non-blank string when present.",
    )
}

private fun Map<String, Any?>.requireIntField(key: String): Int {
  if (!containsKey(key) || this[key] == null) {
    throw InvalidWorkflowStateSchemaError(
      "Feature-task-runtime artifact map is missing required integer field '$key'.",
    )
  }
  return this[key].asExactIntOrNull()
    ?: throw InvalidWorkflowStateSchemaError(
      "Feature-task-runtime artifact field '$key' must decode to an integer.",
    )
}

private fun Map<String, Any?>.optionalIntField(key: String): Int? {
  if (!containsKey(key) || this[key] == null) {
    return null
  }
  return this[key].asExactIntOrNull()
    ?: throw InvalidWorkflowStateSchemaError(
      "Feature-task-runtime artifact field '$key' must decode to an integer when present.",
    )
}

private fun Map<String, Any?>.optionalLongField(key: String): Long? {
  if (!containsKey(key) || this[key] == null) {
    return null
  }
  return this[key].asExactLongOrNull()
    ?: throw InvalidWorkflowStateSchemaError(
      "Feature-task-runtime artifact field '$key' must decode to a long integer when present.",
    )
}

private fun Any?.asExactIntOrNull(): Int? = asExactLongOrNull()?.let { value ->
  if (value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) value.toInt() else null
}

private fun Any?.asExactLongOrNull(): Long? = when (this) {
  is Byte -> toLong()
  is Short -> toLong()
  is Int -> toLong()
  is Long -> this
  is BigInteger -> runCatching { longValueExact() }.getOrNull()
  is BigDecimal -> runCatching { longValueExact() }.getOrNull()
  is String -> toLongOrNull()
  else -> null
}
