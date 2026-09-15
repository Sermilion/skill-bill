package skillbill.engine.featuretask

import skillbill.engine.featuretask.model.FeatureTaskRuntimePhaseStateRequest
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunRequest
import skillbill.error.FeatureTaskRuntimePhaseOrderViolationError
import skillbill.goalrunner.subtaskreview.GoalSubtaskReviewSummaryReducer
import skillbill.ports.workflow.gitops.repositoryCheckpointFingerprint
import skillbill.workflow.goal.model.GoalSubtaskReviewState
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.FeatureTaskRuntimeQualityGateRouting
import skillbill.workflow.taskruntime.FeatureTaskRuntimeTransitionFunction
import skillbill.workflow.taskruntime.envelopeWireMap
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutputRepairEvidence
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeTransitionContext
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeTransitionDeclaration
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeVerdict
import skillbill.workflow.taskruntime.model.NormalizedFeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.requireAcceptedOutput

object FeatureTaskRuntimeRunLoopDrive {
  internal fun FeatureTaskRuntimeRunLoopContext.resumedReentry(): PendingReentry? {
    val (loopId, reentry) = state.latestInFlightReentry ?: return null
    if (
      state.spanBlockedByEntryGate(reentry.span) ||
      (
        loopId == FeatureTaskRuntimePhaseWorkflowDefinition.REVIEW_FIX_LOOP_ID &&
          FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW !in state.completedPhaseIds()
        )
    ) {
      state.discardStaleReentry(loopId)
      return null
    }
    state.recordEdgeIteration(loopId, reentry.edgeIteration)
    val resumePhaseId = reentry.resumePhaseId
    return PendingReentry(
      phaseId = resumePhaseId,
      loopId = loopId,
      edgeIteration = reentry.edgeIteration,
      drivingVerdict = reentry.drivingVerdict,
      expectedRepositoryCheckpoint = if (
        loopId == FeatureTaskRuntimePhaseWorkflowDefinition.REVIEW_FIX_LOOP_ID
      ) {
        reviewedCheckpointFingerprint(request, recorder)
      } else {
        null
      },
    )
  }

  internal fun reviewedCheckpointFingerprint(
    request: FeatureTaskRuntimeRunRequest,
    recorder: FeatureTaskRuntimePhaseRecorder,
  ): String? = recorder.loadDeliveredProjections(request.workflowId)
    ?.get(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW)
    ?.repositoryCheckpointFingerprint

  internal fun FeatureTaskRuntimeRunLoopContext.phaseEntryBlockReason(phaseId: String): String? {
    val context = this
    return entryGateBlockReason(state, transitions, phaseId)
      ?: with(FeatureTaskRuntimeRunLoopBackwardEdge) {
        context.capExhaustedOnResume(phaseId)
      }
      ?: reconcileCompletedGoalReviewPass(phaseId)
  }

  internal fun entryGateBlockReason(
    state: FeatureTaskRuntimeRunState,
    transitions: FeatureTaskRuntimeTransitionDeclaration,
    phaseId: String,
  ): String? {
    val settledVerdicts = state.settledVerdictsByPhaseId
    return transitions.entryGateViolation(phaseId, settledVerdicts)?.let { gate ->
      FeatureTaskRuntimePhaseOrderViolationError(
        phaseId = gate.phaseId,
        requiredPhaseId = gate.requiredPhaseId,
        requiredVerdict = gate.requiredVerdict.wireValue,
        observedVerdict = settledVerdicts[gate.requiredPhaseId]?.wireValue,
      ).message
    }
  }

  internal fun FeatureTaskRuntimeRunLoopContext.reconcileCompletedGoalReviewPass(phaseId: String): String? =
    if (isCompletedGoalReview(request, state, phaseId)) {
      reconcileReservedGoalReviewPass(phaseId)
    } else {
      null
    }

  internal fun isCompletedGoalReview(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    phaseId: String,
  ): Boolean = phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW &&
    isGoalContinuationRun(request) &&
    state.isComplete(phaseId)

  internal fun FeatureTaskRuntimeRunLoopContext.reconcileReservedGoalReviewPass(phaseId: String): String? =
    runCatching {
      goalContinuationRecorder.reviewState(
        request.workflowId,
      )
    }.fold(
      onSuccess = { reviewState ->
        when {
          reviewState == null ->
            "Goal-subtask review persistence.state is missing while reconciling a completed review pass."
          reviewState.reservedPassNumber != null ->
            reconcileReservedGoalReviewOutput(phaseId)
          else -> null
        }
      },
      onFailure = { error ->
        "Goal-subtask review persistence.state is malformed while reconciling a completed review pass: " +
          error.message.orEmpty()
      },
    )

  internal fun FeatureTaskRuntimeRunLoopContext.reconcileReservedGoalReviewOutput(phaseId: String): String? =
    state.outputFor(phaseId)?.payload?.let { output ->
      runCatching {
        outputValidator.validatePhaseOutput(output, sourceLabel = phaseId)
          .requireAcceptedOutput(phaseId)
      }.fold(
        onSuccess = { accepted ->
          completeReservedGoalReviewPass(
            request,
            recorder,
            goalContinuationRecorder,
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

  internal fun completeReservedGoalReviewPass(
    request: FeatureTaskRuntimeRunRequest,
    recorder: FeatureTaskRuntimePhaseRecorder,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    output: String,
    outputMap: Map<String, Any?>,
  ): String? {
    val recordedVerdicts = recorder.recordedFindingVerdicts(outputMap)
    val findings = GoalSubtaskReviewSummaryReducer.fromOutput(outputMap, recordedVerdicts)
    val outcome = GoalSubtaskReviewSummaryReducer.outcomeFor(outputMap, findings)
    return if (
      goalContinuationRecorder.completeGoalReviewPass(
        request = GoalReviewPassCompletionRequest(
          workflowId = request.workflowId,
          verdict = outcome.verdict,
          unresolvedFindingCount = outcome.unresolvedFindingCount,
          findings = findings,
          rawReviewResult = output,
          normalizedOutput = outputMap,
          blockerDispositions = GoalSubtaskReviewSummaryReducer.blockerDispositions(
            outputMap,
            FeatureTaskRuntimeRunLoopPlanningBranch.priorBlockerFindingIds(request, goalContinuationRecorder),
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

  internal fun FeatureTaskRuntimeRunLoopContext.nextPhaseAfter(
    phaseId: String,
    verdict: FeatureTaskRuntimeVerdict,
  ): String? {
    val context = this
    val effectiveVerdict = if (
      phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW &&
      isGoalContinuationRun(request) &&
      goalContinuationRecorder.reviewState(
        request.workflowId,
      )?.reviewCapReached == true
    ) {
      FeatureTaskRuntimeVerdict.REVIEW_CAP_REACHED
    } else {
      verdict
    }
    val edge = FeatureTaskRuntimeRunLoopCheckpoint.matchingBackwardEdge(transitions, phaseId, effectiveVerdict)
    edge?.let {
      with(FeatureTaskRuntimeRunLoopBackwardEdge) {
        context.resumeInFlightReviewFix(it)
      }
    }?.let { return it }
    val edgeIterationCount = edge?.let {
      FeatureTaskRuntimeRunLoopPlanningBranch.effectiveEdgeIterationCount(state, it)
    } ?: 0
    edge?.perEdgeCap?.takeIf { edgeIterationCount >= it }?.let { declaredCap ->
      observability.loopCapExhausted(phaseId, edge.loopId, declaredCap, effectiveVerdict)
    }
    val transition = runCatching {
      FeatureTaskRuntimeTransitionFunction.nextTransition(
        declaration = transitions,
        currentPhaseId = phaseId,
        verdict = effectiveVerdict,
        edgeIterationCount = edgeIterationCount,
        context = FeatureTaskRuntimeTransitionContext(
          settledVerdictsByPhaseId = state.settledVerdictsByPhaseId,
        ),
      )
    }.getOrElse { error ->
      if (error !is FeatureTaskRuntimePhaseOrderViolationError) throw error
      FeatureTaskRuntimeRunLoopPlanningBranch.blockAt(request, state, session, error.phaseId, error.message.orEmpty())
      return null
    }
    val routed = FeatureTaskRuntimeQualityGateRouting.applyAfterBuild(
      phaseId,
      FeatureTaskRuntimeQualityGateRouting.applyAfterReview(
        phaseId,
        transition,
        FeatureTaskRuntimeRunLoopTransitions.qualityGateSelection(request),
      ),
    )
    return with(FeatureTaskRuntimeRunLoopTransitions) {
      context.transitionTarget(
        phaseId,
        edge,
        effectiveVerdict,
        routed,
      )
    }
  }

  internal fun FeatureTaskRuntimeRunLoopContext.carriedForwardGoalReviewSettlement(): PhaseSettlement? = runCatching {
    goalContinuationRecorder.reviewState(request.workflowId)
  }.fold(
    onSuccess = { reviewState ->
      reviewState
        ?.takeIf { it.reviewCapReached || it.reviewSkippedByUser }
        ?.let {
          settleCarriedForwardGoalReview(it, session.activeReentry)
        }
    },
    onFailure = { error ->
      blockCarriedForwardReview(request, state, session, error.message.orEmpty())
    },
  )

  internal fun FeatureTaskRuntimeRunLoopContext.settleCarriedForwardGoalReview(
    reviewState: GoalSubtaskReviewState,
    reentry: PendingReentry?,
  ): PhaseSettlement = runCatching {
    goalContinuationRecorder.lastGoalReviewResult(
      request.workflowId,
    )
  }.fold(
    onSuccess = { rawResult ->
      rawResult?.let {
        validateCarriedForwardGoalReview(it, reviewState, reentry)
      }
        ?: FeatureTaskRuntimeRunLoopDrive.blockCarriedForwardReview(request, state, session, "missing")
    },
    onFailure = { error ->
      blockCarriedForwardReview(request, state, session, error.message.orEmpty())
    },
  )

  internal fun FeatureTaskRuntimeRunLoopContext.validateCarriedForwardGoalReview(
    rawResult: String,
    reviewState: GoalSubtaskReviewState,
    reentry: PendingReentry?,
  ): PhaseSettlement = runCatching {
    val acceptedOutput = outputValidator
      .validatePhaseOutput(rawResult, FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW)
      .requireAcceptedOutput(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW)
    recordCarriedForwardGoalReview(
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
      blockCarriedForwardReview(request, state, session, error.message.orEmpty())
    },
  )

  internal fun FeatureTaskRuntimeRunLoopContext.recordCarriedForwardGoalReview(
    normalizedOutput: NormalizedFeatureTaskRuntimePhaseOutput,
    repairEvidence: FeatureTaskRuntimePhaseOutputRepairEvidence?,
    reentry: PendingReentry?,
  ) {
    val phaseId = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW
    if (state.isComplete(phaseId)) {
      return
    }
    val iteration = state.nextIteration(phaseId)
    val priorRecord = state.recordFor(phaseId)
    val persisted = recorder.recordCompletedPhase(
      FeatureTaskRuntimePhaseStateRequest(
        workflowId = request.workflowId,
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
    if (reentry != null) session.transitionPendingReentry(null)
    state.recordCompleted(
      FeatureTaskRuntimePhaseOutput(
        phaseId,
        iteration,
        normalizedOutput.canonicalJson,
        normalizedOutput,
        repairEvidence,
      ),
    )
  }

  internal fun blockCarriedForwardReview(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    session: FeatureTaskRuntimeRunLoopSession,
    detail: String,
  ): PhaseSettlement {
    val reason = if (detail == "missing") {
      "Goal-subtask review pass budget is exhausted but its durable raw review result is missing."
    } else {
      "Goal-subtask review pass budget is exhausted but its durable raw review result is malformed: $detail"
    }
    FeatureTaskRuntimeRunLoopPlanningBranch.blockAt(
      request,
      state,
      session,
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW,
      reason,
    )
    return PhaseSettlement.stop()
  }

  internal fun FeatureTaskRuntimeRunLoopContext.invalidateReviewGenerationIfNeeded() {
    if (
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW !in
      state.phasesRequiringDurableGateInvalidation()
    ) {
      return
    }
    val generation = checkNotNull(
      recorder.persistReviewGenerationInvalidation(request.workflowId),
    ) {
      "Could not durably invalidate legacy review evidence for workflow '${request.workflowId}'."
    }
    state.advanceReviewGeneration(generation)
    state.resetInvalidatedReviewGeneration()
    if (session.pendingReentry?.loopId == FeatureTaskRuntimePhaseWorkflowDefinition.REVIEW_FIX_LOOP_ID) {
      session.transitionReentryPair(null, null)
    }
  }

  internal fun FeatureTaskRuntimeRunLoopContext.runPhaseDriveLoop(advance: (String) -> PhaseSettlement) {
    val explicitResumePhase = request.goalContinuation?.lastResumableStep
      ?.takeIf(String::isNotBlank)
    if (explicitResumePhase != null) {
      state.reopenFromExplicitResume(explicitResumePhase)
      session.transitionReentryPair(null, null)
    }
    var phaseId: String? = explicitResumePhase
      ?: session.pendingReentry?.phaseId
      ?: transitions.forwardPhaseIds.first()
    while (phaseId != null) {
      val settled = advance(phaseId)
      val completedPhaseId = settled.completedPhaseId
      phaseId = if (completedPhaseId != null) {
        nextPhaseAfter(
          completedPhaseId,
          requireNotNull(settled.completedVerdict),
        )
      } else {
        null
      }
    }
  }

  internal fun FeatureTaskRuntimeRunLoopContext.advancePhaseReason(phaseId: String): String? {
    val context = this
    return if (state.isComplete(phaseId)) {
      state.outputFor(phaseId)
        ?.takeIf { phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PLAN }
        ?.let {
          FeatureTaskRuntimeRunLoopBackwardEdge.applyPlanningStop(context, phaseId, it)
        }
    } else {
      with(FeatureTaskRuntimeRunLoopBackwardEdge) {
        context.establishBranchIfNeeded(phaseId)
          ?: context.runPhaseFor(phaseId)
      }
    }
  }

  internal fun settleAdvanceOutcome(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    session: FeatureTaskRuntimeRunLoopSession,
    phaseId: String,
    reason: String?,
  ): PhaseSettlement = when {
    session.decomposed != null -> PhaseSettlement.stop()
    session.recordRejectionSettlementPending -> {
      session.clearRecordRejectionSettlementPending()
      PhaseSettlement.completed(phaseId, FeatureTaskRuntimeVerdict.RECORD_REJECTED)
    }
    reason != null -> {
      if (session.paused == null) {
        FeatureTaskRuntimeRunLoopPlanningBranch.blockAt(request, state, session, phaseId, reason)
      }
      PhaseSettlement.stop()
    }
    else -> PhaseSettlement.completed(phaseId, state.verdictFor(phaseId))
  }
}
