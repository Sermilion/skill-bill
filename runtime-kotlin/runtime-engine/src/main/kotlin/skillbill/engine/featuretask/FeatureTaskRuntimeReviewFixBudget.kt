package skillbill.engine.featuretask

import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseLedgerAction
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseLedgerEntry

/**
 * Whether the named review-fix budget was actually spent, or `null` when the run holds no durable
 * phase ledger to read it from. Exhaustion is the durable LOOP_CAP_EXHAUSTED transition the run loop
 * writes when the declared cap denies re-entry — never a loop-edge count, which an ordinary repair
 * round that later succeeded also produces. The ledger is append-only, so a genuine exhaustion stays
 * readable after an authorized resume completes the run.
 */
fun reviewFixCapExhaustion(
  ledger: List<FeatureTaskRuntimePhaseLedgerEntry>?,
  goalReviewCapReached: Boolean?,
): Boolean? {
  if (ledger == null) return null
  if (goalReviewCapReached == true) return true
  return ledger.any {
    it.action == FeatureTaskRuntimePhaseLedgerAction.LOOP_CAP_EXHAUSTED &&
      it.loopId == FeatureTaskRuntimePhaseWorkflowDefinition.REVIEW_FIX_LOOP_ID
  }
}

/**
 * Audit-gap rounds this run durably entered, at the grain of one relaunched audit per round, or
 * `null` when no durable phase ledger exists. A measured zero (the first audit was satisfied) is a
 * different fact from an unavailable measurement and stays distinguishable.
 *
 * A stateless audit relaunches inside its own phase and records each round as an AUDIT_AC_RETRY
 * continuation; the `audit_gap` backward edge that once carried the same fact is retired and no
 * current run writes one. Counting only the retired edge reported every run as converging on its
 * first audit, which is the fabricated zero this measurement exists to prevent, so both shapes count.
 */
fun auditGapIterationCount(ledger: List<FeatureTaskRuntimePhaseLedgerEntry>?): Int? {
  if (ledger == null) return null
  return ledger.count(::isAuditGapRound)
}

private fun isAuditGapRound(entry: FeatureTaskRuntimePhaseLedgerEntry): Boolean = when (entry.action) {
  FeatureTaskRuntimePhaseLedgerAction.LOOP_EDGE ->
    entry.loopId == FeatureTaskRuntimePhaseWorkflowDefinition.AUDIT_GAP_LOOP_ID
  FeatureTaskRuntimePhaseLedgerAction.FIX_LOOP_ITERATION ->
    entry.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT &&
      FeatureTaskRuntimeContinuationKind.fromLedgerDetail(entry.blockedReason) ==
      FeatureTaskRuntimeContinuationKind.AUDIT_AC_RETRY
  else -> false
}

fun FeatureTaskRuntimeRunner.reviewFixCapExhaustion(workflowId: String): Boolean? = reviewFixCapExhaustion(
  recorder.loadPhaseLedger(workflowId),
  goalContinuationRecorder.reviewState(workflowId)?.reviewCapReached,
)

fun FeatureTaskRuntimeRunner.auditGapIterationCount(workflowId: String): Int? =
  auditGapIterationCount(recorder.loadPhaseLedger(workflowId))
