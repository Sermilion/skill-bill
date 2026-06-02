package skillbill.application

import skillbill.application.model.FeatureTaskRuntimePhaseLedgerRequest
import skillbill.application.model.FeatureTaskRuntimeRunEvent
import skillbill.application.model.FeatureTaskRuntimeRunRequest
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseLedgerAction

/**
 * SKILL-65 Subtask 3 (AC4): the per-phase observability + attempt-ledger sink for
 * one feature-task-runtime run.
 *
 * Modeled on the `GoalRunnerObservabilityEmitter` / `GoalRunnerLedgerRecorder`
 * style: at each phase boundary it (1) emits a typed
 * [FeatureTaskRuntimeRunEvent] to the run request's thin event sink and (2)
 * appends a [FeatureTaskRuntimePhaseLedgerAction] entry to the durable
 * append-only ledger via [FeatureTaskRuntimePhaseRecorder.appendLedgerEntry].
 * The recorder mints the timestamp and the monotonic sequence, so this class
 * never sources time or ordering.
 *
 * Ledger actions:
 *  - `START`  on a fresh phase attempt;
 *  - `RESUME` when the first attempt is a resume of persisted state;
 *  - `FIX_LOOP_ITERATION` on each bounded re-run (carrying `fixLoopIteration`);
 *  - `BLOCKED` on a schema-gate / missing-upstream / exhausted-budget failure;
 *  - `COMPLETE` on a phase that passed its schema gate.
 */
internal class FeatureTaskRuntimeRunObservability(
  private val recorder: FeatureTaskRuntimePhaseRecorder,
  private val request: FeatureTaskRuntimeRunRequest,
) {
  fun started(phaseId: String, resolvedAgentId: String, attemptCount: Int, resumed: Boolean) {
    request.eventSink.emit(
      FeatureTaskRuntimeRunEvent.PhaseStarted(
        workflowId = request.workflowId,
        phaseId = phaseId,
        resolvedAgentId = resolvedAgentId,
        attemptCount = attemptCount,
        resumed = resumed,
      ),
    )
    appendLedger(
      FeatureTaskRuntimePhaseLedgerRequest(
        workflowId = request.workflowId,
        action = if (resumed) {
          FeatureTaskRuntimePhaseLedgerAction.RESUME
        } else {
          FeatureTaskRuntimePhaseLedgerAction.START
        },
        phaseId = phaseId,
        attemptCount = attemptCount,
        resolvedAgentId = resolvedAgentId,
      ),
    )
  }

  fun fixLoopIteration(phaseId: String, resolvedAgentId: String, attemptCount: Int, fixLoopIteration: Int) {
    request.eventSink.emit(
      FeatureTaskRuntimeRunEvent.PhaseFixLoopIteration(
        workflowId = request.workflowId,
        phaseId = phaseId,
        resolvedAgentId = resolvedAgentId,
        attemptCount = attemptCount,
        fixLoopIteration = fixLoopIteration,
      ),
    )
    appendLedger(
      FeatureTaskRuntimePhaseLedgerRequest(
        workflowId = request.workflowId,
        action = FeatureTaskRuntimePhaseLedgerAction.FIX_LOOP_ITERATION,
        phaseId = phaseId,
        attemptCount = attemptCount,
        resolvedAgentId = resolvedAgentId,
        fixLoopIteration = fixLoopIteration,
      ),
    )
  }

  fun completed(phaseId: String, resolvedAgentId: String, attemptCount: Int) {
    request.eventSink.emit(
      FeatureTaskRuntimeRunEvent.PhaseCompleted(
        workflowId = request.workflowId,
        phaseId = phaseId,
        resolvedAgentId = resolvedAgentId,
        attemptCount = attemptCount,
      ),
    )
    appendLedger(
      FeatureTaskRuntimePhaseLedgerRequest(
        workflowId = request.workflowId,
        action = FeatureTaskRuntimePhaseLedgerAction.COMPLETE,
        phaseId = phaseId,
        attemptCount = attemptCount,
        resolvedAgentId = resolvedAgentId,
      ),
    )
  }

  fun blocked(phaseId: String, resolvedAgentId: String, attemptCount: Int, blockedReason: String) {
    request.eventSink.emit(
      FeatureTaskRuntimeRunEvent.PhaseBlocked(
        workflowId = request.workflowId,
        phaseId = phaseId,
        resolvedAgentId = resolvedAgentId,
        attemptCount = attemptCount,
        blockedReason = blockedReason,
      ),
    )
    appendLedger(
      FeatureTaskRuntimePhaseLedgerRequest(
        workflowId = request.workflowId,
        action = FeatureTaskRuntimePhaseLedgerAction.BLOCKED,
        phaseId = phaseId,
        attemptCount = attemptCount,
        resolvedAgentId = resolvedAgentId,
        blockedReason = blockedReason,
      ),
    )
  }

  private fun appendLedger(ledgerRequest: FeatureTaskRuntimePhaseLedgerRequest) {
    recorder.appendLedgerEntry(ledgerRequest, request.dbPathOverride)
  }
}
