package skillbill.application

import me.tatarka.inject.annotations.Inject
import skillbill.application.model.FeatureTaskRuntimePhaseLedgerRequest
import skillbill.application.model.FeatureTaskRuntimePhaseStateRequest
import skillbill.contracts.JsonSupport
import skillbill.error.InvalidWorkflowStateSchemaError
import skillbill.ports.persistence.DatabaseSessionFactory
import skillbill.ports.persistence.WorkflowStateRepository
import skillbill.workflow.WorkflowEngine
import skillbill.workflow.WorkflowSnapshotValidator
import skillbill.workflow.model.WorkflowStateSnapshot
import skillbill.workflow.model.WorkflowUpdateInput
import skillbill.workflow.model.appendBoundedHistoryBySequence
import skillbill.workflow.taskruntime.model.FEATURE_TASK_RUNTIME_PHASE_LEDGER_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.FEATURE_TASK_RUNTIME_PHASE_LEDGER_LIMIT
import skillbill.workflow.taskruntime.model.FEATURE_TASK_RUNTIME_PHASE_RECORDS_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseLedgerEntry
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseRecord
import java.time.Duration
import java.time.Instant

/**
 * SKILL-65 Subtask 2 (AC3, AC4, AC5): the live application-layer write seam for
 * feature-task-runtime per-phase persistence and the append-only phase
 * attempt/event ledger.
 *
 * Timestamps and durations are minted HERE, from the runtime clock
 * ([Instant.now]) — never from agent-reported values (AC5), following the
 * `GoalRunnerLedgerRecorder` precedent. Per-phase records (AC3) live in the
 * workflow row's `artifacts_json` under
 * [FEATURE_TASK_RUNTIME_PHASE_RECORDS_ARTIFACT_KEY] keyed by phase id; the
 * append-only ledger (AC4) lives under
 * [FEATURE_TASK_RUNTIME_PHASE_LEDGER_ARTIFACT_KEY] and is appended/pruned with
 * the shared [appendBoundedHistoryBySequence] domain helper, seeding a distinct
 * monotonic sequence from the persisted max so it never restarts at 0.
 *
 * All persistence flows through the [WorkflowStateRepository] port via the
 * [WorkflowFamily.TASK_RUNTIME] delegation; no infrastructure type is reached
 * directly.
 */
@Inject
class FeatureTaskRuntimePhaseRecorder(
  private val database: DatabaseSessionFactory,
  private val workflowSnapshotValidator: WorkflowSnapshotValidator,
) {
  private val engine: WorkflowEngine = WorkflowEngine(workflowSnapshotValidator)

  /**
   * Persists one per-phase record (AC3). On a finishing call the runtime mints
   * `finished_at` and derives `duration_millis` from the previously persisted
   * runtime-minted `started_at`; on a non-finishing call it mints `started_at`.
   * Returns true when the workflow row exists and was updated.
   */
  fun recordPhaseState(request: FeatureTaskRuntimePhaseStateRequest, dbOverride: String? = null): Boolean =
    database.transaction(dbOverride) { unitOfWork ->
      val record = WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, request.workflowId)
        ?: return@transaction false
      val artifacts = decodeArtifacts(record.artifactsJson)
      val existingRecords = phaseRecordsFrom(artifacts)
      val now = Instant.now().toString()
      val previous = existingRecords[request.phaseId]
      val startedAt = previous?.startedAt ?: now
      val phaseRecord = FeatureTaskRuntimePhaseRecord(
        phaseId = request.phaseId,
        status = request.status,
        attemptCount = request.attemptCount,
        startedAt = startedAt,
        finishedAt = if (request.finished) now else null,
        durationMillis = if (request.finished) durationMillis(startedAt, now) else null,
        resolvedAgentId = request.resolvedAgentId,
        outputArtifact = request.outputArtifact,
      )
      val updatedRecords = LinkedHashMap(existingRecords).apply { put(request.phaseId, phaseRecord) }
      val patch = mapOf(
        FEATURE_TASK_RUNTIME_PHASE_RECORDS_ARTIFACT_KEY to
          updatedRecords.mapValues { (_, value) -> value.toArtifactMap() },
      )
      persistPatch(unitOfWork.workflowStates, record, patch)
      true
    }

  /**
   * Appends one phase attempt/event ledger entry (AC4). The runtime mints the
   * timestamp and assigns the next monotonic sequence from the persisted max.
   * Returns true when the workflow row exists and was updated.
   */
  fun appendLedgerEntry(request: FeatureTaskRuntimePhaseLedgerRequest, dbOverride: String? = null): Boolean =
    database.transaction(dbOverride) { unitOfWork ->
      val record = WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, request.workflowId)
        ?: return@transaction false
      val artifacts = decodeArtifacts(record.artifactsJson)
      val existingEntries = phaseLedgerFrom(artifacts)
      val nextSequence = (existingEntries.maxOfOrNull { it.sequenceNumber } ?: -1) + 1
      val entry = FeatureTaskRuntimePhaseLedgerEntry(
        action = request.action,
        sequenceNumber = nextSequence,
        timestamp = Instant.now().toString(),
        phaseId = request.phaseId,
        attemptCount = request.attemptCount,
        resolvedAgentId = request.resolvedAgentId,
        fixLoopIteration = request.fixLoopIteration,
        blockedReason = request.blockedReason,
      )
      val updatedLedger = appendBoundedHistoryBySequence(
        existing = existingEntries.map { it.toArtifactMap() },
        entry = entry.toArtifactMap(),
        retentionLimit = FEATURE_TASK_RUNTIME_PHASE_LEDGER_LIMIT,
      )
      persistPatch(
        unitOfWork.workflowStates,
        record,
        mapOf(FEATURE_TASK_RUNTIME_PHASE_LEDGER_ARTIFACT_KEY to updatedLedger),
      )
      true
    }

  private fun persistPatch(
    workflowStates: WorkflowStateRepository,
    record: WorkflowStateSnapshot,
    patch: Map<String, Any?>,
  ) {
    val updated = engine.updateRecord(
      WorkflowFamily.TASK_RUNTIME.definition,
      record,
      WorkflowUpdateInput(
        workflowStatus = record.workflowStatus,
        currentStepId = record.currentStepId,
        stepUpdates = null,
        artifactsPatch = patch,
        sessionId = record.sessionId.orEmpty(),
      ),
    )
    WorkflowFamily.TASK_RUNTIME.save(workflowStates, updated)
  }
}

private fun phaseRecordsFrom(artifacts: Map<String, Any?>): Map<String, FeatureTaskRuntimePhaseRecord> {
  val raw = artifacts[FEATURE_TASK_RUNTIME_PHASE_RECORDS_ARTIFACT_KEY] as? Map<*, *> ?: return emptyMap()
  return raw.entries.mapNotNull { (key, value) ->
    val phaseId = key as? String ?: return@mapNotNull null
    val recordMap = JsonSupport.anyToStringAnyMap(value) ?: return@mapNotNull null
    // AC6: strict decode — a malformed persisted per-phase record loud-fails
    // here via the domain model's typed InvalidWorkflowStateSchemaError.
    phaseId to FeatureTaskRuntimePhaseRecord.fromArtifactMap(recordMap)
  }.toMap()
}

private fun phaseLedgerFrom(artifacts: Map<String, Any?>): List<FeatureTaskRuntimePhaseLedgerEntry> {
  // AC6: an ABSENT ledger key legitimately seeds an empty ledger; a PRESENT but
  // non-list value is corrupt persisted state and must loud-fail rather than be
  // coerced to empty.
  val raw = artifacts[FEATURE_TASK_RUNTIME_PHASE_LEDGER_ARTIFACT_KEY] ?: return emptyList()
  val rawList = raw as? List<*>
    ?: throw InvalidWorkflowStateSchemaError(
      "Feature-task-runtime artifact '$FEATURE_TASK_RUNTIME_PHASE_LEDGER_ARTIFACT_KEY' must decode to a list.",
    )
  return rawList.map { item ->
    val entryMap = JsonSupport.anyToStringAnyMap(item)
      ?: throw InvalidWorkflowStateSchemaError(
        "Feature-task-runtime phase ledger entry must decode to a string-keyed map.",
      )
    // AC6: strict decode — a malformed persisted ledger entry loud-fails here
    // via the domain model's typed InvalidWorkflowStateSchemaError, mirroring the
    // per-phase record read path; the watermark is computed from the typed
    // sequenceNumber so a malformed sequence_number never silently seeds 0.
    FeatureTaskRuntimePhaseLedgerEntry.fromArtifactMap(entryMap)
  }
}

private fun durationMillis(startedAt: String, finishedAt: String): Long =
  Duration.between(Instant.parse(startedAt), Instant.parse(finishedAt)).toMillis().coerceAtLeast(0)
