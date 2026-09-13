package skillbill.engine.featuretask

import skillbill.error.InvalidFeatureTaskRuntimePhaseOutputSchemaError
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.model.workflowStepStatus
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseOutputValidator
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseLedgerAction
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseLedgerEntry
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseRecord
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeReviewFinding
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeTransitionDeclaration
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidationEvidence
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeVerdict
import skillbill.workflow.taskruntime.model.requireAcceptedOutput

class FeatureTaskRuntimeRunState(
  initialRecords: Map<String, FeatureTaskRuntimePhaseRecord>,
  val transitions: FeatureTaskRuntimeTransitionDeclaration,
  durableInitialLedger: List<FeatureTaskRuntimePhaseLedgerEntry> = emptyList(),
  val outputValidator: FeatureTaskRuntimePhaseOutputValidator,
  initialReviewGeneration: Int = 0,
  private val validationEvidenceCommandResolver: (FeatureTaskRuntimeValidationEvidence?) -> String? = {
    it?.results?.lastOrNull()?.command
  },
) {
  private val durableInitialRecords: Map<String, FeatureTaskRuntimePhaseRecord> = initialRecords

  private val statelessAuditInputs: FeatureTaskRuntimeStatelessAuditInputs =
    FeatureTaskRuntimeRunStateReconstruction.normalizeForStatelessAudit(
      durableInitialRecords,
      durableInitialLedger,
    )

  val initialRecords: Map<String, FeatureTaskRuntimePhaseRecord> = statelessAuditInputs.records

  private val normalizedInitialLedger: List<FeatureTaskRuntimePhaseLedgerEntry> = statelessAuditInputs.ledger

  internal var reviewGeneration: Int = initialReviewGeneration
    private set

  private val hasDurableReviewInvalidationTombstone: Boolean = durableInitialRecords[
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW,
  ]?.resolvedAgentId == REVIEW_INVALIDATION_AGENT_ID
  internal val inFlightReentries: MutableMap<String, InFlightReentry> =
    FeatureTaskRuntimeRunStateReconstruction.reconstructInFlightReentries(
      transitions,
      durableInitialLedger,
      durableInitialRecords,
    ).filterKeys { loopId ->
      !hasDurableReviewInvalidationTombstone ||
        loopId != FeatureTaskRuntimePhaseWorkflowDefinition.REVIEW_FIX_LOOP_ID
    }.toMutableMap()

  val gateInvalidatedPhases: MutableSet<String> = mutableSetOf()

  val parsedOutputsByPayload: MutableMap<String, Map<String, Any?>> = mutableMapOf()

  val outputs: MutableList<FeatureTaskRuntimePhaseOutput> = mutableListOf()

  val completed: MutableSet<String> =
    this.initialRecords.values
      .filter { it.status.workflowStepStatus() == WorkflowStepStatus.COMPLETED }
      .map { it.phaseId }
      .toMutableSet()
      .also(::invalidateLegacyPlanWithoutPreplan)
      .also {
        FeatureTaskRuntimeRunStateReconstruction.invalidateLegacyRemovedAuditCompletion(
          this.initialRecords,
          it,
          gateInvalidatedPhases,
        )
      }
      .also {
        FeatureTaskRuntimeRunStateReconstruction.invalidateDownstreamOfIncompleteAudit(
          transitions,
          it,
          gateInvalidatedPhases,
        )
      }
      .also { FeatureTaskRuntimeRunStateReconstruction.invalidateIncompleteReentrySpans(inFlightReentries.values, it) }
      .also {
        FeatureTaskRuntimeRunStateReconstruction.invalidateUnsatisfiedGateSuccessors(
          transitions,
          it,
          gateInvalidatedPhases,
          ::durableVerdictFor,
        )
      }
      .also { completedPhases ->
        invalidateIncompleteValidationSettlement(
          state = ValidationSettlementState(
            completed = completedPhases,
            initialRecords = this.initialRecords,
            transitions = transitions,
            gateInvalidatedPhases = gateInvalidatedPhases,
          ),
          validation = ValidationSettlementValidation(
            validatedRecordToOutput = ::validatedRecordToOutput,
            validationEvidenceCommandResolver = validationEvidenceCommandResolver,
            durableVerdictFor = ::durableVerdictFor,
          ),
        )
      }
  init {
    this.initialRecords.values
      .mapNotNull(::validatedRecordToOutput)
      .filterNot { it.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PLAN && it.phaseId !in completed }
      .filterNot { it.phaseId in gateInvalidatedPhases }
      .toCollection(outputs)
  }

  fun validatedRecordToOutput(record: FeatureTaskRuntimePhaseRecord): FeatureTaskRuntimePhaseOutput? {
    if (FeatureTaskRuntimeRunStateReconstruction.hasLegacyRemovedAuditVerdict(record)) return null
    return record.outputArtifact?.let { artifact ->
      val accepted = try {
        outputValidator.validatePhaseOutput(artifact, record.phaseId).requireAcceptedOutput(record.phaseId)
      } catch (error: InvalidFeatureTaskRuntimePhaseOutputSchemaError) {
        if (record.status.workflowStepStatus() == WorkflowStepStatus.COMPLETED) throw error
        return@let null
      }
      FeatureTaskRuntimePhaseOutput(
        phaseId = record.phaseId,
        iteration = record.attemptCount,
        payload = accepted.normalizedOutput.canonicalJson,
        normalizedOutput = accepted.normalizedOutput,
        repairEvidence = record.repairEvidence ?: accepted.repairEvidence,
      )
    }
  }

  val priorRecords: MutableSet<String> = this.initialRecords.keys.toMutableSet()
  val phasesLaunchedThisProcess: MutableSet<String> = mutableSetOf()
  private val initialReviewRecord = this.initialRecords[FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW]
    ?.takeIf { FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW !in gateInvalidatedPhases }
  internal var currentReviewPassNumber: Int? = initialReviewRecord?.reviewPassNumber
    ?: initialReviewRecord?.let { 1 }
    private set
  internal var completedReviewPassNumber: Int? = currentReviewPassNumber
    ?.takeIf { initialReviewRecord?.status?.workflowStepStatus() == WorkflowStepStatus.COMPLETED }
    private set

  val persistedAttemptCounts: MutableMap<String, Int> =
    this.initialRecords.mapValues { (_, record) -> record.attemptCount }.toMutableMap()

  val blockedRecords: MutableMap<String, String> = this.initialRecords
    .filterValues { record ->
      record.status.workflowStepStatus() == WorkflowStepStatus.BLOCKED &&
        record.resolvedAgentId != BRANCH_SETUP_AGENT_ID
    }
    .mapValues { (_, record) -> record.blockedReason.orEmpty() }
    .toMutableMap()

  val branchSetupBlockedPhases: MutableSet<String> = this.initialRecords
    .filterValues {
      it.status.workflowStepStatus() == WorkflowStepStatus.BLOCKED && it.resolvedAgentId == BRANCH_SETUP_AGENT_ID
    }
    .keys
    .toMutableSet()

  val edgeIterationByLoop: MutableMap<String, Int> = (
    this.initialRecords.values
      .mapNotNull { record -> record.loopId?.let { loopId -> record.edgeIteration?.let { loopId to it } } } +
      normalizedInitialLedger.mapNotNull { entry ->
        entry.takeIf { it.action == FeatureTaskRuntimePhaseLedgerAction.LOOP_EDGE }
          ?.loopId?.let { loopId -> entry.edgeIteration?.let { loopId to it } }
      }
    )
    .groupBy({ it.first }, { it.second })
    .mapValues { (_, iterations) -> iterations.max() }
    .toMutableMap()
    .apply {
      if (hasDurableReviewInvalidationTombstone) remove(FeatureTaskRuntimePhaseWorkflowDefinition.REVIEW_FIX_LOOP_ID)
    }

  val liveClaimedLoops: MutableSet<String> = inFlightReentries.keys.toMutableSet()

  val fixLoopBudgetBaseByPhase: MutableMap<String, Int> =
    FeatureTaskRuntimeRunStateReconstruction.reconstructFixLoopBudgetBases(
      ReconstructFixLoopBudgetBasesArgs(
        transitions = transitions,
        edgeIterationByLoop = edgeIterationByLoop,
        initialRecords = durableInitialRecords,
        initialLedger = durableInitialLedger,
        completed = completed,
        gateInvalidatedPhases = gateInvalidatedPhases,
        nextIteration = ::nextIteration,
      ),
    )

  init {
    if (hasDurableReviewInvalidationTombstone) resetInvalidatedReviewGeneration()
  }

  fun outputs(): List<FeatureTaskRuntimePhaseOutput> = outputs.toList()

  fun phasesRequiringDurableGateInvalidation(): Set<String> = gateInvalidatedPhases.toSet()

  fun resetInvalidatedReviewGeneration() {
    val loopId = FeatureTaskRuntimePhaseWorkflowDefinition.REVIEW_FIX_LOOP_ID
    val reviewPhaseId = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW
    val fixPhaseId = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT_FIX
    inFlightReentries.remove(loopId)
    edgeIterationByLoop.remove(loopId)
    liveClaimedLoops.remove(loopId)
    fixLoopBudgetBaseByPhase.remove(fixPhaseId)
    fixLoopBudgetBaseByPhase.remove(reviewPhaseId)
    persistedAttemptCounts.remove(fixPhaseId)
    persistedAttemptCounts.remove(reviewPhaseId)
    priorRecords.remove(reviewPhaseId)
    blockedRecords.remove(reviewPhaseId)
    currentReviewPassNumber = null
    completedReviewPassNumber = null
  }

  fun recordFor(phaseId: String): FeatureTaskRuntimePhaseRecord? = initialRecords[phaseId]

  fun reopenForReentry(phaseId: String) {
    completed.remove(phaseId)
    fixLoopBudgetBaseByPhase[phaseId] = maxOf(nextIteration(phaseId) - 1, 0)
  }

  fun reopenFromExplicitResume(phaseId: String) {
    val start = transitions.forwardPhaseIds.indexOf(phaseId)
    require(start >= 0) { "Unknown explicit resume phase '$phaseId'." }
    transitions.forwardPhaseIds.drop(start).forEach { phase ->
      completed.remove(phase)
      outputs.removeAll { it.phaseId == phase }
      blockedRecords.remove(phase)
      branchSetupBlockedPhases.remove(phase)
      fixLoopBudgetBaseByPhase[phase] = maxOf(nextIteration(phase) - 1, 0)
    }
    inFlightReentries.clear()
    edgeIterationByLoop.clear()
    liveClaimedLoops.clear()
  }

  fun invalidateProducerOutput(phaseId: String) {
    completed.remove(phaseId)
    outputs.removeAll { it.phaseId == phaseId }
    fixLoopBudgetBaseByPhase[phaseId] = maxOf(nextIteration(phaseId) - 1, 0)
  }

  fun isComplete(phaseId: String): Boolean = phaseId in completed

  fun recordCompleted(output: FeatureTaskRuntimePhaseOutput) {
    outputs += output
    completed += output.phaseId
    priorRecords += output.phaseId
    if (output.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW) {
      completedReviewPassNumber = currentReviewPassNumber
    }
  }

  fun completedPhaseIds(): List<String> =
    FeatureTaskRuntimePhaseWorkflowDefinition.definition.stepIds.filter { it in completed }

  fun fixLoopIterationFor(phaseId: String, absoluteIteration: Int): Int =
    absoluteIteration - (fixLoopBudgetBaseByPhase[phaseId] ?: 0)

  fun restartAttemptBudget(phaseId: String) {
    fixLoopBudgetBaseByPhase[phaseId] = maxOf(nextIteration(phaseId) - 1, 0)
  }

  internal fun trailingNonOutputAttempts(
    phaseId: String,
    isProcessFailure: (String) -> Boolean,
  ): List<FeatureTaskRuntimeNonOutputAttempt> {
    val base = fixLoopBudgetBaseByPhase[phaseId] ?: 0
    return normalizedInitialLedger
      .filter { entry ->
        entry.phaseId == phaseId &&
          entry.attemptCount > base &&
          entry.action in NON_OUTPUT_LEDGER_ACTIONS
      }
      .sortedBy(FeatureTaskRuntimePhaseLedgerEntry::sequenceNumber)
      .takeLastWhile { entry ->
        entry.action == FeatureTaskRuntimePhaseLedgerAction.PAUSED ||
          isProcessFailure(entry.blockedReason.orEmpty())
      }
      .map { entry ->
        FeatureTaskRuntimeNonOutputAttempt(
          paused = entry.action == FeatureTaskRuntimePhaseLedgerAction.PAUSED,
          reason = entry.blockedReason.orEmpty(),
        )
      }
  }

  fun legacyReviewPreparationRetryConsumedBudget(phaseId: String, currentReason: String): Boolean {
    if (phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW ||
      !currentReason.startsWith("Phase 'review' exhausted the bounded fix loop")
    ) {
      return false
    }
    val recentBlocks = recentBlockedReasons(phaseId)
    return recentBlocks.firstOrNull() == currentReason &&
      recentBlocks.getOrNull(1)
        ?.startsWith("Goal-subtask review state or durable raw evidence is malformed: [SQLITE_BUSY]") == true
  }

  fun legacyLaunchSeamRejectionConsumedBudget(phaseId: String, currentReason: String): Boolean {
    if (!currentReason.startsWith("Phase '$phaseId' exhausted the bounded fix loop") ||
      phaseId !in FeatureTaskRuntimePhaseWorkflowDefinition.REGENERATION_PRODUCER_BY_CONSUMER
    ) {
      return false
    }
    val recentBlocks = recentBlockedReasons(phaseId)
    return recentBlocks.firstOrNull() == currentReason &&
      recentBlocks.getOrNull(1)
        ?.contains("rejected an upstream bounded planning projection at the launch seam") == true
  }

  private fun recentBlockedReasons(phaseId: String): List<String?> = normalizedInitialLedger
    .filter { entry -> entry.phaseId == phaseId && entry.action == FeatureTaskRuntimePhaseLedgerAction.BLOCKED }
    .sortedByDescending(FeatureTaskRuntimePhaseLedgerEntry::sequenceNumber)
    .take(2)
    .map(FeatureTaskRuntimePhaseLedgerEntry::blockedReason)

  fun hasPriorRecord(phaseId: String): Boolean = phaseId in priorRecords

  fun resumedFromPriorProcess(phaseId: String): Boolean =
    phaseId in initialRecords && phaseId !in phasesLaunchedThisProcess

  fun recordPhaseLaunched(phaseId: String) {
    phasesLaunchedThisProcess += phaseId
  }

  fun persistedBlockedReason(phaseId: String): String? = blockedRecords[phaseId]

  fun hasBranchSetupBlock(phaseId: String): Boolean = phaseId in branchSetupBlockedPhases

  fun clearBranchSetupBlock(phaseId: String) {
    branchSetupBlockedPhases.remove(phaseId)
    persistedAttemptCounts.remove(phaseId)
  }

  fun edgeIterationCount(loopId: String): Int = edgeIterationByLoop[loopId] ?: 0

  fun recordEdgeIteration(loopId: String, edgeIteration: Int) {
    edgeIterationByLoop[loopId] = edgeIteration
    liveClaimedLoops += loopId
  }

  fun isLoopLiveClaimed(loopId: String): Boolean = loopId in liveClaimedLoops

  fun discardStaleReentry(loopId: String) {
    inFlightReentries.remove(loopId)
    edgeIterationByLoop.remove(loopId)
    liveClaimedLoops.remove(loopId)
  }

  internal val latestInFlightReentry: Pair<String, InFlightReentry>?
    get() = inFlightReentries.maxByOrNull { (_, reentry) -> reentry.edgeSequenceNumber }?.toPair()

  fun outputFor(phaseId: String): FeatureTaskRuntimePhaseOutput? =
    outputs.filter { it.phaseId == phaseId }.maxByOrNull { it.iteration }

  fun outputCountFor(phaseId: String): Int = outputs.count { it.phaseId == phaseId }

  fun nextIteration(phaseId: String): Int {
    val latestOutputIteration = outputs.filter { it.phaseId == phaseId }.maxOfOrNull { it.iteration } ?: 0
    val persistedAttempts = persistedAttemptCounts[phaseId] ?: 0
    return maxOf(persistedAttempts, latestOutputIteration) + 1
  }

  fun parsedOutput(output: FeatureTaskRuntimePhaseOutput?): Map<String, Any?>? {
    val payload = output?.payload ?: return null
    return parsedOutputsByPayload.getOrPut(payload) {
      output.normalizedOutput?.envelope
        ?: outputValidator.validatePhaseOutput(payload, sourceLabel = output.phaseId)
          .requireAcceptedOutput(output.phaseId)
          .normalizedOutput
          .envelope
    }
  }

  fun advanceReviewGeneration(next: Int) {
    if (next > reviewGeneration) reviewGeneration = next
  }

  fun evidenceGeneration(phaseId: String): Int =
    if (phaseId in FeatureTaskRuntimePhaseWorkflowDefinition.GENERATION_SCOPED_PHASE_IDS) reviewGeneration else 0

  fun reserveReviewPass(passNumber: Int?) {
    if (passNumber != null) currentReviewPassNumber = passNumber
  }

  fun verdictFor(phaseId: String): FeatureTaskRuntimeVerdict =
    FeatureTaskRuntimeOutputVerification.verdictFor(phaseId, parsedOutput(outputFor(phaseId)))

  val settledVerdictsByPhaseId: Map<String, FeatureTaskRuntimeVerdict>
    get() = completed.associateWith(::verdictFor)

  fun spanBlockedByEntryGate(span: List<String>): Boolean {
    val settledVerdicts = settledVerdictsByPhaseId
    return span.any { phaseId -> transitions.entryGateViolation(phaseId, settledVerdicts) != null }
  }

  fun unresolvedReviewFindings(phaseId: String): List<FeatureTaskRuntimeReviewFinding> =
    FeatureTaskRuntimeOutputVerification.unresolvedReviewFindings(parsedOutput(outputFor(phaseId)))

  fun durableVerdictFor(phaseId: String): FeatureTaskRuntimeVerdict {
    val record = initialRecords[phaseId] ?: return verdictFor(phaseId)
    val output = validatedRecordToOutput(record) ?: return verdictFor(phaseId)
    return FeatureTaskRuntimeOutputVerification.verdictFor(phaseId, parsedOutput(output))
  }
}

internal data class FeatureTaskRuntimeNonOutputAttempt(val paused: Boolean, val reason: String)

val NON_OUTPUT_LEDGER_ACTIONS = setOf(
  FeatureTaskRuntimePhaseLedgerAction.BLOCKED,
  FeatureTaskRuntimePhaseLedgerAction.PAUSED,
)

const val REVIEW_INVALIDATION_AGENT_ID: String = "audit-gate-migration"

internal data class InFlightReentry(
  val destinationPhaseId: String,
  val edgeIteration: Int,
  val drivingVerdict: FeatureTaskRuntimeVerdict,
  val span: List<String>,
  val completedAfterEdge: Set<String>,
  val edgeSequenceNumber: Int,
) {
  val resumePhaseId: String
    get() = span.firstOrNull { phaseId -> phaseId !in completedAfterEdge } ?: destinationPhaseId
}
