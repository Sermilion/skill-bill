package skillbill.engine.featuretask

import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseLedgerAction
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseLedgerEntry

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
