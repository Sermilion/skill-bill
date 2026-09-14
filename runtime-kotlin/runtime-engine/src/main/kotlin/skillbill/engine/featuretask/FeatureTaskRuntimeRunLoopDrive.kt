package skillbill.engine.featuretask

import skillbill.workflow.taskruntime.*

import skillbill.engine.featuretask.model.FeatureTaskRuntimePhaseStateRequest
import skillbill.error.FeatureTaskRuntimePhaseOrderViolationError
import skillbill.goalrunner.subtaskreview.GoalSubtaskReviewSummaryReducer
import skillbill.ports.workflow.gitops.repositoryCheckpointFingerprint
import skillbill.workflow.goal.model.GoalSubtaskReviewState
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.FeatureTaskRuntimeQualityGateRouting
import skillbill.workflow.taskruntime.FeatureTaskRuntimeTransitionFunction
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutputRepairEvidence
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeTransitionContext
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeVerdict
import skillbill.workflow.taskruntime.model.NormalizedFeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.requireAcceptedOutput

object FeatureTaskRuntimeRunLoopDrive {
  internal fun resumedReentry(runLoop: FeatureTaskRuntimeRunLoop): PendingReentry? {
    val (loopId, reentry) = runLoop.state.latestInFlightReentry ?: return null
    if (
      runLoop.state.spanBlockedByEntryGate(reentry.span) ||
      (
        loopId == FeatureTaskRuntimePhaseWorkflowDefinition.REVIEW_FIX_LOOP_ID &&
          FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW !in runLoop.state.completedPhaseIds()
        )
    ) {
      runLoop.state.discardStaleReentry(loopId)
      return null
    }
    runLoop.state.recordEdgeIteration(loopId, reentry.edgeIteration)
    val resumePhaseId = reentry.resumePhaseId
    return PendingReentry(
      phaseId = resumePhaseId,
      loopId = loopId,
      edgeIteration = reentry.edgeIteration,
      drivingVerdict = reentry.drivingVerdict,
      expectedRepositoryCheckpoint = if (
        loopId == FeatureTaskRuntimePhaseWorkflowDefinition.REVIEW_FIX_LOOP_ID
      ) {
        reviewedCheckpointFingerprint(runLoop)
      } else {
        null
      },
    )
  }

  fun reviewedCheckpointFingerprint(runLoop: FeatureTaskRuntimeRunLoop): String? =
    runLoop.recorder.loadDeliveredProjections(runLoop.request.workflowId)
      ?.get(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW)
      ?.repositoryCheckpointFingerprint

  fun phaseEntryBlockReason(runLoop: FeatureTaskRuntimeRunLoop, phaseId: String): String? =
    entryGateBlockReason(runLoop, phaseId)
      ?: FeatureTaskRuntimeRunLoopBackwardEdge.capExhaustedOnResume(runLoop, phaseId)
      ?: reconcileCompletedGoalReviewPass(runLoop, phaseId)

  fun entryGateBlockReason(runLoop: FeatureTaskRuntimeRunLoop, phaseId: String): String? {
    val settledVerdicts = runLoop.state.settledVerdictsByPhaseId
    return runLoop.transitions.entryGateViolation(phaseId, settledVerdicts)?.let { gate ->
      FeatureTaskRuntimePhaseOrderViolationError(
        phaseId = gate.phaseId,
        requiredPhaseId = gate.requiredPhaseId,
        requiredVerdict = gate.requiredVerdict.wireValue,
        observedVerdict = settledVerdicts[gate.requiredPhaseId]?.wireValue,
      ).message
    }
  }

  fun reconcileCompletedGoalReviewPass(runLoop: FeatureTaskRuntimeRunLoop, phaseId: String): String? =
    if (isCompletedGoalReview(runLoop, phaseId)) {
      reconcileReservedGoalReviewPass(runLoop, phaseId)
    } else {
      null
    }

  fun isCompletedGoalReview(runLoop: FeatureTaskRuntimeRunLoop, phaseId: String): Boolean =
    phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW &&
      isGoalContinuationRun(runLoop.request) &&
      runLoop.state.isComplete(phaseId)

  fun reconcileReservedGoalReviewPass(runLoop: FeatureTaskRuntimeRunLoop, phaseId: String): String? = runCatching {
    runLoop.goalContinuationRecorder.reviewState(
      runLoop.request.workflowId,
    )
  }.fold(
    onSuccess = { reviewState ->
      when {
        reviewState == null ->
          "Goal-subtask review runLoop.state is missing while reconciling a completed review pass."
        reviewState.reservedPassNumber != null ->
          reconcileReservedGoalReviewOutput(runLoop, phaseId)
        else -> null
      }
    },
    onFailure = { error ->
      "Goal-subtask review runLoop.state is malformed while reconciling a completed review pass: " +
        error.message.orEmpty()
    },
  )

  fun reconcileReservedGoalReviewOutput(runLoop: FeatureTaskRuntimeRunLoop, phaseId: String): String? =
    runLoop.state.outputFor(phaseId)?.payload?.let { output ->
      runCatching {
        runLoop.outputValidator.validatePhaseOutput(output, sourceLabel = phaseId)
          .requireAcceptedOutput(phaseId)
      }.fold(
        onSuccess = { accepted ->
          FeatureTaskRuntimeRunLoopDrive.completeReservedGoalReviewPass(
            runLoop,
            output,
            accepted.normalizedOutput.envelopeWireMap(),
          )
        },
        onFailure = { error ->
          "Completed goal-subtask review output cannot reconcile its reserved pass: " +
            error.message.orEmpty()
        },
      )
    } ?: "Completed goal-subtask review has no durable output to reconcile its reserved pass."

  fun completeReservedGoalReviewPass(
    runLoop: FeatureTaskRuntimeRunLoop,
    output: String,
    outputMap: Map<String, Any?>,
  ): String? {
    val recordedVerdicts = runLoop.recorder.recordedFindingVerdicts(outputMap)
    val findings = GoalSubtaskReviewSummaryReducer.fromOutput(outputMap, recordedVerdicts)
    val outcome = GoalSubtaskReviewSummaryReducer.outcomeFor(outputMap, findings)
    return if (
      runLoop.goalContinuationRecorder.completeGoalReviewPass(
        request = GoalReviewPassCompletionRequest(
          workflowId = runLoop.request.workflowId,
          verdict = outcome.verdict,
          unresolvedFindingCount = outcome.unresolvedFindingCount,
          findings = findings,
          rawReviewResult = output,
          normalizedOutput = outputMap,
          blockerDispositions = GoalSubtaskReviewSummaryReducer.blockerDispositions(
            outputMap,
            FeatureTaskRuntimeRunLoopPlanningBranch.priorBlockerFindingIds(runLoop),
          ),
          commitFocusedAccounting = GoalSubtaskReviewSummaryReducer.commitFocusedAccounting(outputMap),
        ),
      ) == null
    ) {
      "Completed goal-subtask review could not persist its reserved pass."
    } else {
      null
    }
  }

  fun nextPhaseAfter(
    runLoop: FeatureTaskRuntimeRunLoop,
    phaseId: String,
    verdict: FeatureTaskRuntimeVerdict,
  ): String? {
    val effectiveVerdict = if (
      phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW &&
      isGoalContinuationRun(runLoop.request) &&
      runLoop.goalContinuationRecorder.reviewState(
        runLoop.request.workflowId,
      )?.reviewCapReached == true
    ) {
      FeatureTaskRuntimeVerdict.REVIEW_CAP_REACHED
    } else {
      verdict
    }
    val edge = FeatureTaskRuntimeRunLoopCheckpoint.matchingBackwardEdge(runLoop, phaseId, effectiveVerdict)
    edge?.let { FeatureTaskRuntimeRunLoopBackwardEdge.resumeInFlightReviewFix(runLoop, it) }?.let { return it }
    val transition = runCatching {
      FeatureTaskRuntimeTransitionFunction.nextTransition(
        declaration = runLoop.transitions,
        currentPhaseId = phaseId,
        verdict = effectiveVerdict,
        edgeIterationCount = edge?.let {
          FeatureTaskRuntimeRunLoopPlanningBranch.effectiveEdgeIterationCount(
            runLoop,
            it,
          )
        } ?: 0,
        context = FeatureTaskRuntimeTransitionContext(
          settledVerdictsByPhaseId = runLoop.state.settledVerdictsByPhaseId,
        ),
      )
    }.getOrElse { error ->
      if (error !is FeatureTaskRuntimePhaseOrderViolationError) throw error
      FeatureTaskRuntimeRunLoopPlanningBranch.blockAt(runLoop, error.phaseId, error.message.orEmpty())
      return null
    }
    val routed = FeatureTaskRuntimeQualityGateRouting.applyAfterBuild(
      phaseId,
      FeatureTaskRuntimeQualityGateRouting.applyAfterReview(
        phaseId,
        transition,
        FeatureTaskRuntimeRunLoopTransitions.qualityGateSelection(runLoop),
      ),
    )
    return FeatureTaskRuntimeRunLoopTransitions.transitionTarget(runLoop, phaseId, edge, effectiveVerdict, routed)
  }

  internal fun carriedForwardGoalReviewSettlement(runLoop: FeatureTaskRuntimeRunLoop): PhaseSettlement? = runCatching {
    runLoop.goalContinuationRecorder.reviewState(runLoop.request.workflowId)
  }.fold(
    onSuccess = { reviewState ->
      reviewState
        ?.takeIf { it.reviewCapReached || it.reviewSkippedByUser }
        ?.let {
          settleCarriedForwardGoalReview(
            runLoop,
            it,
            runLoop.session.activeReentry,
          )
        }
    },
    onFailure = { error ->
      FeatureTaskRuntimeRunLoopDrive.blockCarriedForwardReview(
        runLoop,
        error.message.orEmpty(),
      )
    },
  )

  internal fun settleCarriedForwardGoalReview(
    runLoop: FeatureTaskRuntimeRunLoop,
    reviewState: GoalSubtaskReviewState,
    reentry: PendingReentry?,
  ): PhaseSettlement = runCatching {
    runLoop.goalContinuationRecorder.lastGoalReviewResult(
      runLoop.request.workflowId,
    )
  }.fold(
    onSuccess = { rawResult ->
      rawResult?.let { validateCarriedForwardGoalReview(runLoop, it, reviewState, reentry) }
        ?: FeatureTaskRuntimeRunLoopDrive.blockCarriedForwardReview(runLoop, "missing")
    },
    onFailure = { error ->
      FeatureTaskRuntimeRunLoopDrive.blockCarriedForwardReview(
        runLoop,
        error.message.orEmpty(),
      )
    },
  )

  internal fun validateCarriedForwardGoalReview(
    runLoop: FeatureTaskRuntimeRunLoop,
    rawResult: String,
    reviewState: GoalSubtaskReviewState,
    reentry: PendingReentry?,
  ): PhaseSettlement = runCatching {
    val acceptedOutput = runLoop.outputValidator
      .validatePhaseOutput(rawResult, FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW)
      .requireAcceptedOutput(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW)
    recordCarriedForwardGoalReview(
      runLoop,
      acceptedOutput.normalizedOutput,
      acceptedOutput.repairEvidence,
      reentry,
    )
  }.fold(
    onSuccess = {
      PhaseSettlement.completed(
        FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW,
        requireNotNull(reviewState.passResults.lastOrNull()).verdict,
      )
    },
    onFailure = { error ->
      FeatureTaskRuntimeRunLoopDrive.blockCarriedForwardReview(
        runLoop,
        error.message.orEmpty(),
      )
    },
  )

  internal fun recordCarriedForwardGoalReview(
    runLoop: FeatureTaskRuntimeRunLoop,
    normalizedOutput: NormalizedFeatureTaskRuntimePhaseOutput,
    repairEvidence: FeatureTaskRuntimePhaseOutputRepairEvidence?,
    reentry: PendingReentry?,
  ) {
    val phaseId = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW
    if (runLoop.state.isComplete(phaseId)) {
      return
    }
    val iteration = runLoop.state.nextIteration(phaseId)
    val priorRecord = runLoop.state.recordFor(phaseId)
    val persisted = runLoop.recorder.recordCompletedPhase(
      FeatureTaskRuntimePhaseStateRequest(
        workflowId = runLoop.request.workflowId,
        phaseId = phaseId,
        status = STATUS_COMPLETED,
        attemptCount = iteration,
        resolvedAgentId = priorRecord?.resolvedAgentId ?: "user-directed",
        finished = true,
        outputArtifact = normalizedOutput.canonicalJson,
        normalizedOutput = normalizedOutput,
        repairEvidence = repairEvidence,
        loopId = reentry?.loopId,
        edgeIteration = reentry?.edgeIteration,
      ),
    )
    if (!persisted) {
      error("Carried-forward goal review could not atomically persist its canonical result.")
    }
    if (reentry != null) runLoop.session.pendingReentry = null
    runLoop.state.recordCompleted(
      FeatureTaskRuntimePhaseOutput(
        phaseId,
        iteration,
        normalizedOutput.canonicalJson,
        normalizedOutput,
        repairEvidence,
      ),
    )
  }

  internal fun blockCarriedForwardReview(runLoop: FeatureTaskRuntimeRunLoop, detail: String): PhaseSettlement {
    val reason = if (detail == "missing") {
      "Goal-subtask review pass budget is exhausted but its durable raw review result is missing."
    } else {
      "Goal-subtask review pass budget is exhausted but its durable raw review result is malformed: $detail"
    }
    FeatureTaskRuntimeRunLoopPlanningBranch.blockAt(
      runLoop,
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW,
      reason,
    )
    return PhaseSettlement.stop()
  }

  fun invalidateReviewGenerationIfNeeded(runLoop: FeatureTaskRuntimeRunLoop) {
    if (
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW !in
      runLoop.state.phasesRequiringDurableGateInvalidation()
    ) {
      return
    }
    val generation = checkNotNull(
      runLoop.recorder.persistReviewGenerationInvalidation(runLoop.request.workflowId),
    ) {
      "Could not durably invalidate legacy review evidence for workflow '${runLoop.request.workflowId}'."
    }
    runLoop.state.advanceReviewGeneration(generation)
    runLoop.state.resetInvalidatedReviewGeneration()
    if (runLoop.session.pendingReentry?.loopId == FeatureTaskRuntimePhaseWorkflowDefinition.REVIEW_FIX_LOOP_ID) {
      runLoop.session.pendingReentry = null
      runLoop.session.activeReentry = null
    }
  }

  fun runPhaseDriveLoop(runLoop: FeatureTaskRuntimeRunLoop) {
    val explicitResumePhase = runLoop.request.goalContinuation?.lastResumableStep
      ?.takeIf(String::isNotBlank)
    if (explicitResumePhase != null) {
      runLoop.state.reopenFromExplicitResume(explicitResumePhase)
      runLoop.session.pendingReentry = null
      runLoop.session.activeReentry = null
    }
    var phaseId: String? = explicitResumePhase
      ?: runLoop.session.pendingReentry?.phaseId
      ?: runLoop.transitions.forwardPhaseIds.first()
    while (phaseId != null) {
      val settled = runLoop.advance(phaseId)
      val completedPhaseId = settled.completedPhaseId
      phaseId = if (completedPhaseId != null) {
        FeatureTaskRuntimeRunLoopDrive.nextPhaseAfter(
          runLoop,
          completedPhaseId,
          requireNotNull(settled.completedVerdict),
        )
      } else {
        null
      }
    }
  }

  fun advancePhaseReason(runLoop: FeatureTaskRuntimeRunLoop, phaseId: String): String? =
    if (runLoop.state.isComplete(phaseId)) {
      runLoop.state.outputFor(phaseId)
        ?.takeIf { phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PLAN }
        ?.let { FeatureTaskRuntimeRunLoopBackwardEdge.applyPlanningStop(runLoop, phaseId, it) }
    } else {
      FeatureTaskRuntimeRunLoopBackwardEdge.establishBranchIfNeeded(
        runLoop,
        phaseId,
      ) ?: FeatureTaskRuntimeRunLoopBackwardEdge.runPhaseFor(runLoop, phaseId)
    }

  internal fun settleAdvanceOutcome(
    runLoop: FeatureTaskRuntimeRunLoop,
    phaseId: String,
    reason: String?,
  ): PhaseSettlement = when {
    runLoop.session.decomposed != null -> PhaseSettlement.stop()
    runLoop.session.recordRejectionSettlementPending -> {
      runLoop.session.recordRejectionSettlementPending = false
      PhaseSettlement.completed(phaseId, FeatureTaskRuntimeVerdict.RECORD_REJECTED)
    }
    reason != null -> {
      if (runLoop.session.paused == null) FeatureTaskRuntimeRunLoopPlanningBranch.blockAt(runLoop, phaseId, reason)
      PhaseSettlement.stop()
    }
    else -> PhaseSettlement.completed(phaseId, runLoop.state.verdictFor(phaseId))
  }
}
