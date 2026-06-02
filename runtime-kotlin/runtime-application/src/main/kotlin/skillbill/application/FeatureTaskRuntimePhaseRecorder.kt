package skillbill.application

import me.tatarka.inject.annotations.Inject
import skillbill.application.model.FeatureTaskRuntimePhaseLaunchBriefing
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
import skillbill.workflow.taskruntime.model.FEATURE_TASK_RUNTIME_PHASE_BRIEFINGS_ARTIFACT_KEY
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
   * SKILL-65 Subtask 3 (AC2/AC7/AC8): persists the assembled per-phase launch
   * briefing into durable workflow state, keyed by phase id, BEFORE the phase
   * agent is launched. This is the durable delivery of the three-layer handoff
   * (run-invariants + latest-iteration upstream outputs + derived context): the
   * launched agent never selects its own inputs, and a downstream consumer
   * (Subtask 4's surface) reads the briefing back by phase id rather than the
   * briefing being thrown away. The latest briefing per phase replaces the prior
   * one. Returns true when the workflow row exists and was updated.
   */
  fun recordPhaseBriefing(
    workflowId: String,
    briefing: FeatureTaskRuntimePhaseLaunchBriefing,
    dbOverride: String? = null,
  ): Boolean = database.transaction(dbOverride) { unitOfWork ->
    val record = WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId)
      ?: return@transaction false
    val artifacts = decodeArtifacts(record.artifactsJson)
    val existingBriefings = phaseBriefingsFrom(artifacts)
    val updatedBriefings = LinkedHashMap(existingBriefings).apply { put(briefing.phaseId, briefing) }
    val patch = mapOf(
      FEATURE_TASK_RUNTIME_PHASE_BRIEFINGS_ARTIFACT_KEY to
        updatedBriefings.mapValues { (_, value) -> value.toArtifactMap() },
    )
    persistPatch(unitOfWork.workflowStates, record, patch)
    true
  }

  /**
   * SKILL-65 Subtask 3 (AC2/AC7): strict read of the durable per-phase briefing
   * store. Returns the typed briefings keyed by phase id; an absent key yields an
   * empty map while any present-but-malformed entry loud-fails via the typed
   * [skillbill.error.InvalidWorkflowStateSchemaError]. Returns null only when the
   * workflow row does not exist.
   */
  fun loadPhaseBriefings(
    workflowId: String,
    dbOverride: String? = null,
  ): Map<String, FeatureTaskRuntimePhaseLaunchBriefing>? = database.read(dbOverride) { unitOfWork ->
    val record = WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId)
      ?: return@read null
    phaseBriefingsFrom(decodeArtifacts(record.artifactsJson))
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

  /**
   * SKILL-65 Subtask 3 (AC7): runner-owned strict read seam for the durable
   * per-phase records. Returns the typed records keyed by phase id; an absent
   * records key legitimately yields an empty map, while any present-but-malformed
   * record loud-fails via the domain model's typed
   * [skillbill.error.InvalidWorkflowStateSchemaError] in
   * [FeatureTaskRuntimePhaseRecord.fromArtifactMap] (no best-effort decode).
   * Returns null only when the workflow row does not exist.
   */
  fun loadPhaseRecords(workflowId: String, dbOverride: String? = null): Map<String, FeatureTaskRuntimePhaseRecord>? =
    database.read(dbOverride) { unitOfWork ->
      val record = WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId)
        ?: return@read null
      phaseRecordsFrom(decodeArtifacts(record.artifactsJson))
    }

  /**
   * SKILL-65 Subtask 3 (AC1): ensures a runtime workflow row exists for the
   * phase loop, opening one at the definition's initial step when absent. Reuses
   * [WorkflowEngine.openRecord] for validated construction. Idempotent: returns
   * the existing row's id unchanged when one already exists.
   */
  fun ensureWorkflowOpen(workflowId: String, sessionId: String, dbOverride: String? = null): Boolean =
    database.transaction(dbOverride) { unitOfWork ->
      if (WorkflowFamily.TASK_RUNTIME.get(unitOfWork.workflowStates, workflowId) != null) {
        return@transaction true
      }
      val opened = engine.openRecord(
        WorkflowFamily.TASK_RUNTIME.definition,
        workflowId,
        sessionId,
        WorkflowFamily.TASK_RUNTIME.definition.defaultInitialStepId,
      )
      WorkflowFamily.TASK_RUNTIME.save(unitOfWork.workflowStates, opened)
      true
    }

  private fun persistPatch(
    workflowStates: WorkflowStateRepository,
    record: WorkflowStateSnapshot,
    patch: Map<String, Any?>,
  ) {
    // Architecture note (Minor): TASK_RUNTIME progress lives ENTIRELY in the
    // per-phase records map (the single source of truth for what is complete and
    // what to resume). The durable `currentStepId` is intentionally left pinned at
    // the initial step here and must NOT be treated as a second source of truth for
    // run progress.
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

private fun schemaError(detail: String): Nothing = throw InvalidWorkflowStateSchemaError(detail)

// F-P1 / F-C1 / AC6+AC7: shared strict decode for a present-but-keyed artifact
// map (per-phase records, per-phase briefings). An ABSENT key legitimately seeds
// an empty store; a PRESENT-but-non-map value, a non-String key, or a non-map
// entry value all loud-fail with a typed [InvalidWorkflowStateSchemaError]
// rather than being silently coerced to empty or dropped (which would otherwise
// turn corrupt durable state into a blind re-run / lost outputs on resume).
private fun <T> decodeStrictKeyedArtifactMap(
  artifacts: Map<String, Any?>,
  artifactKey: String,
  decodeEntry: (String, Map<String, Any?>) -> T,
): Map<String, T> {
  val raw = artifacts[artifactKey] ?: return emptyMap()
  val rawMap = raw as? Map<*, *>
    ?: schemaError("Feature-task-runtime artifact '$artifactKey' must decode to a map.")
  return rawMap.entries.associate { (key, value) ->
    val phaseId = key as? String
      ?: schemaError("Feature-task-runtime artifact '$artifactKey' must have string keys; found '$key'.")
    val entryMap = JsonSupport.anyToStringAnyMap(value)
      ?: schemaError("Feature-task-runtime artifact '$artifactKey' entry for '$phaseId' must decode to a map.")
    phaseId to decodeEntry(phaseId, entryMap)
  }
}

private fun phaseRecordsFrom(artifacts: Map<String, Any?>): Map<String, FeatureTaskRuntimePhaseRecord> =
  // AC6: strict decode — a malformed persisted per-phase record loud-fails via
  // the domain model's typed InvalidWorkflowStateSchemaError.
  decodeStrictKeyedArtifactMap(artifacts, FEATURE_TASK_RUNTIME_PHASE_RECORDS_ARTIFACT_KEY) { _, recordMap ->
    FeatureTaskRuntimePhaseRecord.fromArtifactMap(recordMap)
  }

private fun phaseBriefingsFrom(artifacts: Map<String, Any?>): Map<String, FeatureTaskRuntimePhaseLaunchBriefing> =
  decodeStrictKeyedArtifactMap(artifacts, FEATURE_TASK_RUNTIME_PHASE_BRIEFINGS_ARTIFACT_KEY) { _, briefingMap ->
    FeatureTaskRuntimePhaseLaunchBriefing.fromArtifactMap(briefingMap)
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
