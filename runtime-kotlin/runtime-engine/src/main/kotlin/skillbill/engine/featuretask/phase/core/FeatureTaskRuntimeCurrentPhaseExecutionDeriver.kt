package skillbill.engine.featuretask.phase.core
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimePhaseStatus
import skillbill.engine.work.model.IdeStatusCurrentPhaseExecution
import skillbill.engine.work.model.IdeStatusCurrentPhaseExecutionKind
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.model.workflowStepStatus
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseLedgerAction
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseLedgerEntry
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseRecord
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowQueries
internal data class FeatureTaskRuntimeCurrentPhaseExecutionContext(
  val currentPhaseId: String?,
  val records: Map<String, FeatureTaskRuntimePhaseRecord>,
  val phases: List<FeatureTaskRuntimePhaseStatus>,
  val ledger: List<FeatureTaskRuntimePhaseLedgerEntry>,
  val gateRunCount: Int?,
)

class FeatureTaskRuntimeCurrentPhaseExecutionDeriver {

  internal fun derive(context: FeatureTaskRuntimeCurrentPhaseExecutionContext): IdeStatusCurrentPhaseExecution? {
    val phaseId = context.currentPhaseId?.takeIf(String::isNotBlank) ?: return null
    val phaseStatus = context.phases.firstOrNull { it.phaseId == phaseId } ?: return null
    val record = context.records[phaseId]
    return when (phaseId) {
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT ->
        auditExecution(phaseId, phaseStatus, record)
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW ->
        reviewExecution(phaseId, phaseStatus, record, context.ledger)
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VERIFY_FINDINGS ->
        attemptExecution(phaseId, phaseStatus.attemptCount)
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE ->
        validationExecution(phaseId, phaseStatus, context.gateRunCount)
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_BUILD ->
        buildExecution(phaseId, phaseStatus, context.gateRunCount)
      else ->
        edgeExecution(phaseId, record, context.ledger) ?: attemptExecution(phaseId, phaseStatus.attemptCount)
    }
  }

  private fun auditExecution(
    phaseId: String,
    phaseStatus: FeatureTaskRuntimePhaseStatus,
    record: FeatureTaskRuntimePhaseRecord?,
  ): IdeStatusCurrentPhaseExecution? = when {
    phaseStatus.attemptCount >= 1 || record != null -> IdeStatusCurrentPhaseExecution(
      phaseId = phaseId,
      kind = IdeStatusCurrentPhaseExecutionKind.PASS,
      count = 1,
    )
    else -> null
  }

  private fun reviewExecution(
    phaseId: String,
    phaseStatus: FeatureTaskRuntimePhaseStatus,
    record: FeatureTaskRuntimePhaseRecord?,
    ledger: List<FeatureTaskRuntimePhaseLedgerEntry>,
  ): IdeStatusCurrentPhaseExecution? = activeReviewPassNumber(record, ledger)?.let { pass ->
    IdeStatusCurrentPhaseExecution(
      phaseId = phaseId,
      kind = IdeStatusCurrentPhaseExecutionKind.PASS,
      count = pass,
    )
  } ?: attemptExecution(phaseId, phaseStatus.attemptCount)

  private fun validationExecution(
    phaseId: String,
    phaseStatus: FeatureTaskRuntimePhaseStatus,
    gateRunCount: Int?,
  ): IdeStatusCurrentPhaseExecution? = gateRunExecution(phaseId, phaseStatus, gateRunCount)

  private fun buildExecution(
    phaseId: String,
    phaseStatus: FeatureTaskRuntimePhaseStatus,
    gateRunCount: Int?,
  ): IdeStatusCurrentPhaseExecution? = gateRunExecution(phaseId, phaseStatus, gateRunCount)

  private fun gateRunExecution(
    phaseId: String,
    phaseStatus: FeatureTaskRuntimePhaseStatus,
    gateRunCount: Int?,
  ): IdeStatusCurrentPhaseExecution? = gateRunCount?.takeIf { it >= 1 }?.let { count ->
    IdeStatusCurrentPhaseExecution(
      phaseId = phaseId,
      kind = IdeStatusCurrentPhaseExecutionKind.GATE_RUN,
      count = count,
    )
  } ?: attemptExecution(phaseId, phaseStatus.attemptCount)

  private fun edgeExecution(
    phaseId: String,
    record: FeatureTaskRuntimePhaseRecord?,
    ledger: List<FeatureTaskRuntimePhaseLedgerEntry>,
  ): IdeStatusCurrentPhaseExecution? {
    val (loopId, edgeIteration) = activeEdgeContext(phaseId, record, ledger) ?: return null
    val edge = FeatureTaskRuntimePhaseWorkflowQueries.backwardEdgeForLoop(loopId) ?: return null
    return IdeStatusCurrentPhaseExecution(
      phaseId = phaseId,
      kind = if (edge.perEdgeCap == null) {
        IdeStatusCurrentPhaseExecutionKind.SEMANTIC_LOOP
      } else {
        IdeStatusCurrentPhaseExecutionKind.BOUNDED_EDGE
      },
      count = edgeIteration,
      total = edge.perEdgeCap,
    )
  }

  private fun activeReviewPassNumber(
    record: FeatureTaskRuntimePhaseRecord?,
    ledger: List<FeatureTaskRuntimePhaseLedgerEntry>,
  ): Int? {
    val pass = record?.reviewPassNumber ?: return null
    if (record.status.workflowStepStatus() != WorkflowStepStatus.COMPLETED) return pass
    val latestReviewFixEdge = ledger
      .filter {
        it.action == FeatureTaskRuntimePhaseLedgerAction.LOOP_EDGE &&
          it.loopId == FeatureTaskRuntimePhaseWorkflowDefinition.REVIEW_FIX_LOOP_ID &&
          it.edgeIteration != null
      }
      .maxByOrNull { it.sequenceNumber }
    val ledgerEdge = latestReviewFixEdge?.edgeIteration
    val reenteredReview = record.takeIf {
      it.loopId == FeatureTaskRuntimePhaseWorkflowDefinition.REVIEW_FIX_LOOP_ID
    }?.edgeIteration
    return ledgerEdge?.let { edge ->
      reenteredReview?.takeIf { it >= edge }
    }?.let { pass }
  }

  private fun activeEdgeContext(
    phaseId: String,
    record: FeatureTaskRuntimePhaseRecord?,
    ledger: List<FeatureTaskRuntimePhaseLedgerEntry>,
  ): Pair<String, Int>? {
    val edgeEntry = ledger
      .filter {
        it.action == FeatureTaskRuntimePhaseLedgerAction.LOOP_EDGE &&
          it.phaseId == phaseId &&
          it.loopId != null &&
          it.edgeIteration != null
      }
      .maxByOrNull { it.sequenceNumber }
    if (edgeEntry != null) return edgeEntry.loopId!! to edgeEntry.edgeIteration!!
    record?.loopId?.let { loopId ->
      record.edgeIteration?.let { return loopId to it }
    }
    return null
  }

  private fun attemptExecution(phaseId: String, attemptCount: Int): IdeStatusCurrentPhaseExecution? =
    attemptCount.takeIf { it >= 1 }?.let { count ->
      IdeStatusCurrentPhaseExecution(
        phaseId = phaseId,
        kind = IdeStatusCurrentPhaseExecutionKind.ATTEMPT,
        count = count,
      )
    }
}
