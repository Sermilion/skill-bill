package skillbill.engine.featuretask.runloop.state




import skillbill.engine.featuretask.runloop.core.ReconstructFixLoopBudgetBasesArgs
import skillbill.engine.featuretask.persist.durationMillis
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.model.workflowStepStatus
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeBackwardEdge
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseLedgerAction
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseLedgerEntry
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseRecord
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeTransitionDeclaration
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeVerdict

data class FeatureTaskRuntimeStatelessAuditInputs(
  val records: Map<String, FeatureTaskRuntimePhaseRecord>,
  val ledger: List<FeatureTaskRuntimePhaseLedgerEntry>,
)

object FeatureTaskRuntimeRunStateReconstruction {
  fun isRetiredAuditGapLoop(loopId: String?): Boolean =
    loopId == FeatureTaskRuntimePhaseWorkflowDefinition.AUDIT_GAP_LOOP_ID

  fun normalizeForStatelessAudit(
    rawRecords: Map<String, FeatureTaskRuntimePhaseRecord>,
    rawLedger: List<FeatureTaskRuntimePhaseLedgerEntry>,
  ): FeatureTaskRuntimeStatelessAuditInputs = FeatureTaskRuntimeStatelessAuditInputs(
    records = normalizeInitialRecordsForStatelessAudit(rawRecords),
    ledger = normalizeLedgerForStatelessAudit(rawLedger, rawRecords),
  )

  fun normalizeLedgerForStatelessAudit(
    ledger: List<FeatureTaskRuntimePhaseLedgerEntry>,
    rawRecords: Map<String, FeatureTaskRuntimePhaseRecord>,
  ): List<FeatureTaskRuntimePhaseLedgerEntry> {
    val normalizedRecords = normalizeInitialRecordsForStatelessAudit(rawRecords)
    return ledger.filterNot { entry ->
      isRetiredAuditGapLoop(entry.loopId) ||
        (
          entry.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT &&
            entry.action == FeatureTaskRuntimePhaseLedgerAction.BLOCKED &&
            (
              rawRecords[entry.phaseId]?.let(::hasLegacyAuditGapLineage) == true ||
                normalizedRecords[entry.phaseId]?.status?.workflowStepStatus() == WorkflowStepStatus.PENDING
              )
          )
    }
  }

  internal fun reconstructInFlightReentries(
    transitions: FeatureTaskRuntimeTransitionDeclaration,
    initialLedger: List<FeatureTaskRuntimePhaseLedgerEntry>,
    initialRecords: Map<String, FeatureTaskRuntimePhaseRecord>,
  ): Map<String, InFlightReentry> = buildMap {
    val ledger = normalizeLedgerForStatelessAudit(initialLedger, initialRecords)
    transitions.backwardEdges.forEach { edge ->
      val latestEdge = ledger
        .filter { ledger ->
          ledger.action == FeatureTaskRuntimePhaseLedgerAction.LOOP_EDGE && ledger.loopId == edge.loopId
        }
        .maxByOrNull { it.sequenceNumber }
        ?: return@forEach
      val completedAfterEdge = ledger
        .asSequence()
        .filter { it.sequenceNumber > latestEdge.sequenceNumber }
        .filter { it.action == FeatureTaskRuntimePhaseLedgerAction.COMPLETE }
        .map { it.phaseId }
        .filter { phaseId -> initialRecords[phaseId]?.status?.workflowStepStatus() == WorkflowStepStatus.COMPLETED }
        .toMutableSet()
      initialRecords.values
        .filter { record ->
          record.status.workflowStepStatus() == WorkflowStepStatus.COMPLETED &&
            record.loopId == edge.loopId &&
            record.edgeIteration == latestEdge.edgeIteration
        }
        .mapTo(completedAfterEdge) { it.phaseId }
      val span = reopenedSpan(transitions, edge)
      if (span.any { phaseId -> phaseId !in completedAfterEdge }) {
        put(
          edge.loopId,
          InFlightReentry(
            destinationPhaseId = edge.destinationPhaseId,
            edgeIteration = requireNotNull(latestEdge.edgeIteration),
            drivingVerdict = edge.triggeringVerdict,
            span = span,
            completedAfterEdge = completedAfterEdge,
            edgeSequenceNumber = latestEdge.sequenceNumber,
          ),
        )
      }
    }
  }

  internal fun reconstructFixLoopBudgetBases(args: ReconstructFixLoopBudgetBasesArgs): MutableMap<String, Int> {
    val transitions = args.transitions
    val edgeIterationByLoop = args.edgeIterationByLoop
    val initialRecords = normalizeInitialRecordsForStatelessAudit(args.initialRecords)
    val initialLedger = normalizeLedgerForStatelessAudit(args.initialLedger, args.initialRecords)
    val completed = args.completed
    val gateInvalidatedPhases = args.gateInvalidatedPhases
    val nextIteration = args.nextIteration
    val bases = mutableMapOf<String, Int>()
    transitions.backwardEdges.forEach { edge ->
      if ((edgeIterationByLoop[edge.loopId] ?: 0) <= 0) {
        return@forEach
      }
      reopenedSpan(transitions, edge).forEach { phaseId ->
        if (phaseId !in completed) {
          bases[phaseId] = maxOf(nextIteration(phaseId) - 1, 0)
        }
      }
    }
    seedBudgetBasesOutsideLiveSpans(bases, initialRecords, gateInvalidatedPhases, completed, nextIteration)
    seedOperatorRetryBudgetBases(bases, initialLedger, completed)
    return bases
  }

  internal fun invalidateIncompleteReentrySpans(
    inFlightReentries: Collection<InFlightReentry>,
    completedPhases: MutableSet<String>,
  ) {
    inFlightReentries.forEach { reentry ->
      reentry.span
        .filterNot(reentry.completedAfterEdge::contains)
        .forEach(completedPhases::remove)
    }
  }

  fun normalizeInitialRecordsForStatelessAudit(
    initialRecords: Map<String, FeatureTaskRuntimePhaseRecord>,
  ): Map<String, FeatureTaskRuntimePhaseRecord> = initialRecords.mapValues { (_, record) ->
    val legacyLineage = hasLegacyAuditGapLineage(record)
    val withoutAuditGapLoop = if (isRetiredAuditGapLoop(record.loopId)) {
      record.copy(loopId = null, edgeIteration = null)
    } else {
      record
    }
    if (record.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT &&
      isRetiredAuditGapLoop(record.loopId) &&
      phaseOutputEnvelope(record)?.get(SharedPayloadKeys.STATUS) == WorkflowStepStatus.COMPLETED.wireValue
    ) {
      return@mapValues withoutAuditGapLoop.copy(
        status = WorkflowStepStatus.COMPLETED,
        blockedReason = null,
        failureDisposition = null,
      )
    }
    if (!legacyLineage) {
      return@mapValues withoutAuditGapLoop
    }
    when (withoutAuditGapLoop.status.workflowStepStatus()) {
      WorkflowStepStatus.COMPLETED ->
        if (hasLegacyRemovedAuditVerdict(withoutAuditGapLoop)) {
          discardLegacyAuditActiveState(withoutAuditGapLoop)
        } else {
          withoutAuditGapLoop
        }
      WorkflowStepStatus.BLOCKED,
      WorkflowStepStatus.PAUSED,
      WorkflowStepStatus.RUNNING,
      -> discardLegacyAuditActiveState(withoutAuditGapLoop)
      else -> withoutAuditGapLoop
    }
  }

  internal fun hasLegacyAuditGapLineage(record: FeatureTaskRuntimePhaseRecord): Boolean {
    if (record.phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT) return false
    return isRetiredAuditGapLoop(record.loopId) ||
      hasLegacyRemovedAuditVerdict(record) ||
      hasLegacyAuditGapInnerGaps(record)
  }

  internal fun isLegacyAuditGapPersistedBlock(record: FeatureTaskRuntimePhaseRecord): Boolean =
    record.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT &&
      record.status.workflowStepStatus() == WorkflowStepStatus.BLOCKED &&
      hasLegacyAuditGapLineage(record)

  private fun discardLegacyAuditActiveState(record: FeatureTaskRuntimePhaseRecord): FeatureTaskRuntimePhaseRecord =
    record.copy(
      status = WorkflowStepStatus.PENDING,
      outputArtifact = null,
      finishedAt = null,
      durationMillis = null,
      blockedReason = null,
      failureDisposition = null,
      loopId = null,
      edgeIteration = null,
    )

  fun invalidateDownstreamOfIncompleteAudit(
    transitions: FeatureTaskRuntimeTransitionDeclaration,
    completedPhases: MutableSet<String>,
    gateInvalidatedPhases: MutableSet<String>,
  ) {
    val auditPhase = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT
    if (auditPhase in completedPhases) return
    val auditIndex = transitions.forwardPhaseIds.indexOf(auditPhase)
    if (auditIndex < 0) return
    transitions.forwardPhaseIds
      .drop(auditIndex + 1)
      .filter(completedPhases::contains)
      .forEach { phaseId ->
        completedPhases.remove(phaseId)
        gateInvalidatedPhases += phaseId
      }
  }

  fun invalidateLegacyRemovedAuditCompletion(
    initialRecords: Map<String, FeatureTaskRuntimePhaseRecord>,
    completedPhases: MutableSet<String>,
    gateInvalidatedPhases: MutableSet<String>,
  ) {
    val auditPhase = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT
    val record = initialRecords[auditPhase] ?: return
    if (!isLegacyRemovedAuditCompletion(record)) return
    completedPhases.remove(auditPhase)
    gateInvalidatedPhases += auditPhase
  }

  internal fun hasLegacyRemovedAuditVerdict(record: FeatureTaskRuntimePhaseRecord): Boolean {
    if (record.phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT) return false
    val envelope = phaseOutputEnvelope(record) ?: return false
    val verdict = (envelope[SharedPayloadKeys.VERDICT] as? String)?.trim()?.lowercase()
    return verdict == FeatureTaskRuntimeVerdict.GAPS_FOUND.wireValue
  }

  private fun hasLegacyAuditGapInnerGaps(record: FeatureTaskRuntimePhaseRecord): Boolean {
    if (record.phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT) return false
    val envelope = phaseOutputEnvelope(record) ?: return false
    val produced = JsonCodec.anyToStringAnyMap(envelope[SharedPayloadKeys.PRODUCED_OUTPUTS]) ?: return false
    val directGaps = produced["gaps"] as? List<*>
    return !directGaps.isNullOrEmpty() || hasLegacyInnerGaps(produced[SharedPayloadKeys.VALUE]?.toString())
  }

  private fun hasLegacyInnerGaps(value: String?): Boolean {
    if (value.isNullOrBlank()) return false
    val inner = runCatching {
      JsonCodec.parseObjectOrNull(value)
        ?.let(JsonCodec::jsonElementToValue)
        ?.let(JsonCodec::anyToStringAnyMap)
    }.getOrNull() ?: return false
    return (inner["gaps"] as? List<*>)?.isNotEmpty() == true
  }

  private fun phaseOutputEnvelope(record: FeatureTaskRuntimePhaseRecord): Map<String, Any?>? =
    record.outputArtifact?.let { artifact ->
      runCatching {
        JsonCodec.parseObjectOrNull(artifact)
          ?.let(JsonCodec::jsonElementToValue)
          ?.let(JsonCodec::anyToStringAnyMap)
      }.getOrNull()
    }

  internal fun isLegacyRemovedAuditCompletion(record: FeatureTaskRuntimePhaseRecord): Boolean =
    hasLegacyRemovedAuditVerdict(record) && record.status.workflowStepStatus() == WorkflowStepStatus.COMPLETED

  fun invalidateUnsatisfiedGateSuccessors(
    transitions: FeatureTaskRuntimeTransitionDeclaration,
    completedPhases: MutableSet<String>,
    gateInvalidatedPhases: MutableSet<String>,
    durableVerdictFor: (String) -> FeatureTaskRuntimeVerdict,
  ) {
    val durableVerdicts = completedPhases.associateWith(durableVerdictFor)
    transitions.entryGates.forEach { gate ->
      if (transitions.entryGateViolation(gate.phaseId, durableVerdicts) == null) {
        return@forEach
      }
      val gatedIndex = transitions.forwardPhaseIds.indexOf(gate.phaseId)
      if (gatedIndex < 0) {
        return@forEach
      }
      transitions.forwardPhaseIds
        .drop(gatedIndex)
        .filter(completedPhases::contains)
        .forEach { phaseId ->
          completedPhases.remove(phaseId)
          gateInvalidatedPhases += phaseId
        }
    }
  }

  private fun seedOperatorRetryBudgetBases(
    bases: MutableMap<String, Int>,
    initialLedger: List<FeatureTaskRuntimePhaseLedgerEntry>,
    completed: Set<String>,
  ) {
    initialLedger
      .filter { it.action == FeatureTaskRuntimePhaseLedgerAction.RETRY }
      .groupBy { it.phaseId }
      .forEach { (phaseId, retries) ->
        val latestRetry = retries.maxBy { it.sequenceNumber }
        val settledAfterRetry = initialLedger.any { entry ->
          entry.phaseId == phaseId &&
            entry.sequenceNumber > latestRetry.sequenceNumber &&
            entry.action in setOf(
              FeatureTaskRuntimePhaseLedgerAction.BLOCKED,
              FeatureTaskRuntimePhaseLedgerAction.COMPLETE,
            )
        }
        if (!settledAfterRetry && phaseId !in completed) {
          bases[phaseId] = latestRetry.attemptCount
        }
      }
  }

  private fun seedBudgetBasesOutsideLiveSpans(
    bases: MutableMap<String, Int>,
    initialRecords: Map<String, FeatureTaskRuntimePhaseRecord>,
    gateInvalidatedPhases: Set<String>,
    completed: Set<String>,
    nextIteration: (String) -> Int,
  ) {
    val staleLoopPhases = initialRecords.values
      .filter {
        it.status.workflowStepStatus() != WorkflowStepStatus.COMPLETED &&
          it.loopId != null
      }
      .map { it.phaseId }
    (staleLoopPhases + gateInvalidatedPhases).forEach { phaseId ->
      if (phaseId !in completed && phaseId !in bases) {
        bases[phaseId] = maxOf(nextIteration(phaseId) - 1, 0)
      }
    }
  }

  private fun reopenedSpan(
    transitions: FeatureTaskRuntimeTransitionDeclaration,
    edge: FeatureTaskRuntimeBackwardEdge,
  ): List<String> = transitions.spanBetween(edge.destinationPhaseId, edge.fromPhaseId)
}
