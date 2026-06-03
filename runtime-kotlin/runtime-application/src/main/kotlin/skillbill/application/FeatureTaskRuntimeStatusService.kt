package skillbill.application

import me.tatarka.inject.annotations.Inject
import skillbill.application.model.FeatureTaskRuntimePhaseStatus
import skillbill.application.model.FeatureTaskRuntimeStatusProjection
import skillbill.application.model.FeatureTaskRuntimeStatusRequest
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseLedgerAction
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseLedgerEntry
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseRecord

/**
 * SKILL-65 Subtask 4 (AC1): the read-only feature-task-runtime status service.
 *
 * Mirrors [GoalRunnerStatusService] in shape — it is a thin application read seam
 * that projects durable state into a typed, read-only projection without any
 * orchestration. It reuses the recorder's strict read seam
 * ([FeatureTaskRuntimePhaseRecorder.loadPhaseRecords]) as its ONLY durable
 * dependency: no new infrastructure import, no file IO, and no resume logic
 * (resume is the runner's resumable `run()`). The projection orders phases by the
 * runtime definition's `stepIds`, derives complete/pending/blocked counts, and
 * reports the first not-yet-complete phase as the current phase.
 */
@Inject
class FeatureTaskRuntimeStatusService(
  private val recorder: FeatureTaskRuntimePhaseRecorder,
) {
  /**
   * Projects the read-only status for the requested runtime workflow. Returns
   * null only when the workflow row does not exist (the recorder read seam
   * returns null), distinguishing "no such runtime workflow" from "workflow
   * exists but no phase has produced a record yet" (an empty record map projects
   * every phase as pending).
   */
  fun status(request: FeatureTaskRuntimeStatusRequest): FeatureTaskRuntimeStatusProjection? {
    val records = recorder.loadPhaseRecords(request.workflowId, request.dbPathOverride) ?: return null
    // F-001: the runner persists only `running`/`completed` per-phase records and
    // records a block solely as an append-only ledger entry (action=BLOCKED). The
    // status projection therefore derives blocked-ness from the ledger so a phase
    // that blocked is distinguishable from one merely in-flight. A phase is blocked
    // when its newest ledger entry is BLOCKED (any later START/RESUME/COMPLETE for
    // that phase supersedes the block on a resumed run).
    val ledger = recorder.loadPhaseLedger(request.workflowId, request.dbPathOverride).orEmpty()
    val blockedPhaseIds = blockedPhaseIds(ledger)
    val phases = FeatureTaskRuntimePhaseWorkflowDefinition.definition.stepIds.map { phaseId ->
      records[phaseId].toPhaseStatus(phaseId, blocked = phaseId in blockedPhaseIds)
    }
    return FeatureTaskRuntimeStatusProjection(
      workflowId = request.workflowId,
      phases = phases,
      completeCount = phases.count { it.status == STATUS_COMPLETED },
      pendingCount = phases.count { it.status !in TERMINAL_PHASE_STATUSES },
      blockedCount = phases.count { it.status == STATUS_BLOCKED },
      // F-001: the current phase is the first not-yet-complete phase, including a
      // phase the ledger reports as blocked (which otherwise reads as `running`).
      currentPhaseId = phases.firstOrNull { it.status != STATUS_COMPLETED }?.phaseId,
    )
  }

  // F-001: per phase, the newest ledger entry (highest sequence number) decides
  // whether that phase is currently blocked. A blocked phase whose run was later
  // resumed (START/RESUME/COMPLETE recorded after the block) is no longer blocked.
  private fun blockedPhaseIds(ledger: List<FeatureTaskRuntimePhaseLedgerEntry>): Set<String> = ledger
    .groupBy { it.phaseId }
    .filterValues { entries ->
      entries.maxByOrNull { it.sequenceNumber }?.action == FeatureTaskRuntimePhaseLedgerAction.BLOCKED
    }
    .keys

  private fun FeatureTaskRuntimePhaseRecord?.toPhaseStatus(
    phaseId: String,
    blocked: Boolean,
  ): FeatureTaskRuntimePhaseStatus = if (this == null) {
    FeatureTaskRuntimePhaseStatus(
      phaseId = phaseId,
      // F-001: a phase with no per-phase record can still be blocked when the
      // block happened before any `running` record was persisted (e.g. a
      // missing-upstream block surfaced at handoff assembly).
      status = if (blocked) STATUS_BLOCKED else STATUS_PENDING,
      attemptCount = 0,
      resolvedAgentId = null,
      finished = false,
    )
  } else {
    FeatureTaskRuntimePhaseStatus(
      phaseId = phaseId,
      // F-001: the durable record is left at `running` on a block; the ledger is
      // the authority that reclassifies it as blocked. A completed record always
      // wins over a stale block (its ledger COMPLETE supersedes any prior BLOCKED).
      status = if (blocked && status != STATUS_COMPLETED) STATUS_BLOCKED else status,
      attemptCount = attemptCount,
      resolvedAgentId = resolvedAgentId,
      finished = finishedAt != null,
    )
  }

  private companion object {
    const val STATUS_PENDING = "pending"
    const val STATUS_COMPLETED = "completed"
    const val STATUS_BLOCKED = "blocked"
    val TERMINAL_PHASE_STATUSES = setOf(STATUS_COMPLETED, STATUS_BLOCKED)
  }
}
