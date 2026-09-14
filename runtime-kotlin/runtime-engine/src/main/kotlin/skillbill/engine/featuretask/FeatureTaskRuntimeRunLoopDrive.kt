package skillbill.engine.featuretask

import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunRequest
import skillbill.engine.featuretask.model.FeatureTaskRuntimePhaseStateRequest
import skillbill.error.FeatureTaskRuntimePhaseOrderViolationError
import skillbill.goalrunner.subtaskreview.GoalSubtaskReviewSummaryReducer
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.ports.goalrunner.runner.GoalRunnerSubtaskLauncher
import skillbill.application.idestatus.AgentActivityStampWriter
import skillbill.ports.workflow.gitops.repositoryCheckpointFingerprint
import skillbill.workflow.decomposition.model.SpecSource
import skillbill.workflow.goal.model.GoalSubtaskReviewState
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseOutputValidator
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
import java.time.Clock

object FeatureTaskRuntimeRunLoopDrive {
  internal fun resumedReentry(
    request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    outputValidator: FeatureTaskRuntimePhaseOutputValidator,
    transitions: FeatureTaskRuntimeTransitionDeclaration,
  ): PendingReentry? {
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

  internal fun reviewedCheckpointFingerprint(request: FeatureTaskRuntimeRunRequest, recorder: FeatureTaskRuntimePhaseRecorder): String? =
    recorder.loadDeliveredProjections(request.workflowId)
      ?.get(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW)
      ?.repositoryCheckpointFingerprint

  internal fun phaseEntryBlockReason(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, session: FeatureTaskRuntimeRunLoopSession, goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder, outputValidator: FeatureTaskRuntimePhaseOutputValidator, diagnostics: RuntimeDiagnostics, phaseGates: FeatureTaskRuntimePhaseGates, transitions: FeatureTaskRuntimeTransitionDeclaration, specSource: SpecSource, phaseTokenAccumulator: MutableMap<String, Pair<Int, Int>>?, phaseId: String): String? =
    entryGateBlockReason(state, transitions, phaseId)
      ?: FeatureTaskRuntimeRunLoopBackwardEdge.capExhaustedOnResume(
        request, state, recorder, session,
        transitions,
        phaseId,
      )
      ?: reconcileCompletedGoalReviewPass(
        request, state, recorder,
        goalContinuationRecorder,
        outputValidator,
        transitions,
        phaseId,
      )

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

  internal fun reconcileCompletedGoalReviewPass(
    request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    outputValidator: FeatureTaskRuntimePhaseOutputValidator,
    transitions: FeatureTaskRuntimeTransitionDeclaration,
    phaseId: String,
  ): String? =
    if (isCompletedGoalReview(request, state, goalContinuationRecorder, phaseId)) {
      reconcileReservedGoalReviewPass(
        request, state, recorder,
        goalContinuationRecorder,
        outputValidator,
        transitions,
        phaseId,
      )
    } else {
      null
    }

  internal fun isCompletedGoalReview(
    request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    phaseId: String,
  ): Boolean =
    phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW &&
      isGoalContinuationRun(request) &&
      state.isComplete(phaseId)

  internal fun reconcileReservedGoalReviewPass(
    request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    outputValidator: FeatureTaskRuntimePhaseOutputValidator,
    transitions: FeatureTaskRuntimeTransitionDeclaration,
    phaseId: String,
  ): String? = runCatching {
    goalContinuationRecorder.reviewState(
      request.workflowId,
    )
  }.fold(
    onSuccess = { reviewState ->
      when {
        reviewState == null ->
          "Goal-subtask review persistence.state is missing while reconciling a completed review pass."
        reviewState.reservedPassNumber != null ->
          reconcileReservedGoalReviewOutput(
            request, state, recorder,
            goalContinuationRecorder,
            outputValidator,
            transitions,
            phaseId,
          )
        else -> null
      }
    },
    onFailure = { error ->
      "Goal-subtask review persistence.state is malformed while reconciling a completed review pass: " +
        error.message.orEmpty()
    },
  )

  internal fun reconcileReservedGoalReviewOutput(
    request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    outputValidator: FeatureTaskRuntimePhaseOutputValidator,
    transitions: FeatureTaskRuntimeTransitionDeclaration,
    phaseId: String,
  ): String? =
    state.outputFor(phaseId)?.payload?.let { output ->
      runCatching {
        outputValidator.validatePhaseOutput(output, sourceLabel = phaseId)
          .requireAcceptedOutput(phaseId)
      }.fold(
        onSuccess = { accepted ->
          FeatureTaskRuntimeRunLoopDrive.completeReservedGoalReviewPass(
            request, recorder,
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
    request: FeatureTaskRuntimeRunRequest, recorder: FeatureTaskRuntimePhaseRecorder,
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

  internal fun nextPhaseAfter(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, observability: FeatureTaskRuntimeRunObservability, session: FeatureTaskRuntimeRunLoopSession, goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder, outputValidator: FeatureTaskRuntimePhaseOutputValidator, diagnostics: RuntimeDiagnostics, phaseGates: FeatureTaskRuntimePhaseGates, transitions: FeatureTaskRuntimeTransitionDeclaration, specSource: SpecSource, phaseTokenAccumulator: MutableMap<String, Pair<Int, Int>>?, phaseId: String, verdict: FeatureTaskRuntimeVerdict): String? {
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
    val edge = FeatureTaskRuntimeRunLoopCheckpoint.matchingBackwardEdge( transitions, phaseId, effectiveVerdict)
    edge?.let {
      FeatureTaskRuntimeRunLoopBackwardEdge.resumeInFlightReviewFix(request, state, recorder, session, it)
    }?.let { return it }
    val transition = runCatching {
      FeatureTaskRuntimeTransitionFunction.nextTransition(
        declaration = transitions,
        currentPhaseId = phaseId,
        verdict = effectiveVerdict,
        edgeIterationCount = edge?.let {
          FeatureTaskRuntimeRunLoopPlanningBranch.effectiveEdgeIterationCount(
            state,
            it,
          )
        } ?: 0,
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
    return FeatureTaskRuntimeRunLoopTransitions.transitionTarget(
      request, state, recorder, observability, session,
      goalContinuationRecorder,
      diagnostics,
      phaseGates,
      transitions,
      specSource,
      phaseId,
      edge,
      effectiveVerdict,
      routed,
    )
  }

  internal fun carriedForwardGoalReviewSettlement(
    request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, session: FeatureTaskRuntimeRunLoopSession,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    outputValidator: FeatureTaskRuntimePhaseOutputValidator,
    transitions: FeatureTaskRuntimeTransitionDeclaration,
  ): PhaseSettlement? = runCatching {
    goalContinuationRecorder.reviewState(request.workflowId)
  }.fold(
    onSuccess = { reviewState ->
      reviewState
        ?.takeIf { it.reviewCapReached || it.reviewSkippedByUser }
        ?.let {
          settleCarriedForwardGoalReview(
            request, state, recorder, session,
            goalContinuationRecorder,
            outputValidator,
            transitions,
            it,
            session.activeReentry,
          )
        }
    },
    onFailure = { error ->
      FeatureTaskRuntimeRunLoopDrive.blockCarriedForwardReview(
        request, state, session,
        error.message.orEmpty(),
      )
    },
  )

  internal fun settleCarriedForwardGoalReview(
    request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, session: FeatureTaskRuntimeRunLoopSession,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    outputValidator: FeatureTaskRuntimePhaseOutputValidator,
    transitions: FeatureTaskRuntimeTransitionDeclaration,
    reviewState: GoalSubtaskReviewState,
    reentry: PendingReentry?,
  ): PhaseSettlement = runCatching {
    goalContinuationRecorder.lastGoalReviewResult(
      request.workflowId,
    )
  }.fold(
    onSuccess = { rawResult ->
      rawResult?.let {
        validateCarriedForwardGoalReview(
          request, state, recorder, session,
          outputValidator,
          transitions,
          it,
          reviewState,
          reentry,
        )
      }
        ?: FeatureTaskRuntimeRunLoopDrive.blockCarriedForwardReview(request, state, session, "missing")
    },
    onFailure = { error ->
      FeatureTaskRuntimeRunLoopDrive.blockCarriedForwardReview(
        request, state, session,
        error.message.orEmpty(),
      )
    },
  )

  internal fun validateCarriedForwardGoalReview(
    request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, session: FeatureTaskRuntimeRunLoopSession,
    outputValidator: FeatureTaskRuntimePhaseOutputValidator,
    transitions: FeatureTaskRuntimeTransitionDeclaration,
    rawResult: String,
    reviewState: GoalSubtaskReviewState,
    reentry: PendingReentry?,
  ): PhaseSettlement = runCatching {
    val acceptedOutput = outputValidator
      .validatePhaseOutput(rawResult, FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW)
      .requireAcceptedOutput(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW)
    recordCarriedForwardGoalReview(
      request, state, recorder, session,
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
        request, state, session,
        error.message.orEmpty(),
      )
    },
  )

  internal fun recordCarriedForwardGoalReview(
    request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, session: FeatureTaskRuntimeRunLoopSession,
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
    request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, session: FeatureTaskRuntimeRunLoopSession,
    detail: String,
  ): PhaseSettlement {
    val reason = if (detail == "missing") {
      "Goal-subtask review pass budget is exhausted but its durable raw review result is missing."
    } else {
      "Goal-subtask review pass budget is exhausted but its durable raw review result is malformed: $detail"
    }
    FeatureTaskRuntimeRunLoopPlanningBranch.blockAt(
      request, state, session,
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW,
      reason,
    )
    return PhaseSettlement.stop()
  }

  internal fun invalidateReviewGenerationIfNeeded(
    request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, session: FeatureTaskRuntimeRunLoopSession,
    transitions: FeatureTaskRuntimeTransitionDeclaration,
  ) {
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

  internal fun runPhaseDriveLoop(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, observability: FeatureTaskRuntimeRunObservability, session: FeatureTaskRuntimeRunLoopSession, goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder, outputValidator: FeatureTaskRuntimePhaseOutputValidator, diagnostics: RuntimeDiagnostics, phaseGates: FeatureTaskRuntimePhaseGates, transitions: FeatureTaskRuntimeTransitionDeclaration, specSource: SpecSource, phaseTokenAccumulator: MutableMap<String, Pair<Int, Int>>, subtaskLauncher: GoalRunnerSubtaskLauncher, advance: (String) -> PhaseSettlement,) {
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
        FeatureTaskRuntimeRunLoopDrive.nextPhaseAfter(
          request, state, recorder, observability, session,
          goalContinuationRecorder,
          outputValidator,
          diagnostics,
          phaseGates,
          transitions,
          specSource,
          phaseTokenAccumulator,
          completedPhaseId,
          requireNotNull(settled.completedVerdict),
        )
      } else {
        null
      }
    }
  }

  internal fun advancePhaseReason(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, observability: FeatureTaskRuntimeRunObservability, session: FeatureTaskRuntimeRunLoopSession, goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder, phaseSettlementService: FeatureTaskPhaseSettlementService, outputValidator: FeatureTaskRuntimePhaseOutputValidator, diagnostics: RuntimeDiagnostics, phaseGates: FeatureTaskRuntimePhaseGates, clock: Clock, transitions: FeatureTaskRuntimeTransitionDeclaration, specSource: SpecSource, phaseTokenAccumulator: MutableMap<String, Pair<Int, Int>>?, subtaskLauncher: GoalRunnerSubtaskLauncher, activityStampWriter: AgentActivityStampWriter, phaseId: String): String? =
    if (state.isComplete(phaseId)) {
      state.outputFor(phaseId)
        ?.takeIf { phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PLAN }
        ?.let {
          FeatureTaskRuntimeRunLoopBackwardEdge.applyPlanningStop(
            request, state, recorder, observability, session,
            phaseGates,
            specSource,
            phaseId,
            it,
          )
        }
    } else {
      FeatureTaskRuntimeRunLoopBackwardEdge.establishBranchIfNeeded(
        request, state, recorder, observability, session,
        phaseGates,
        phaseId,
      ) ?: FeatureTaskRuntimeRunLoopBackwardEdge.runPhaseFor(
        request, state, recorder, observability, session,
        goalContinuationRecorder,
        phaseSettlementService,
        outputValidator,
        diagnostics,
        phaseGates,
        clock,
        transitions,
        specSource,
        phaseTokenAccumulator,
        subtaskLauncher,
        activityStampWriter,
        phaseId,
      )
    }

  internal fun settleAdvanceOutcome(
    request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, session: FeatureTaskRuntimeRunLoopSession,
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
