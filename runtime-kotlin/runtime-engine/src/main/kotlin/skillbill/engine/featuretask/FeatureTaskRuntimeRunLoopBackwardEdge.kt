package skillbill.engine.featuretask

import skillbill.engine.featuretask.model.FeatureTaskRuntimePhaseStateRequest
import skillbill.engine.featuretask.model.FeatureTaskRuntimePlanningStopDecision
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunRequest
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.model.workflowStepStatus
import skillbill.workflow.decomposition.model.SpecSource
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeBackwardEdge
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeCapExhaustionBehavior
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseRecord
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeReviewFinding
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeTransitionDeclaration
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeVerdict

object FeatureTaskRuntimeRunLoopBackwardEdge {
  internal fun resumeInFlightReviewFix(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    session: FeatureTaskRuntimeRunLoopSession,
    edge: FeatureTaskRuntimeBackwardEdge,
  ): String? {
    if (
      edge.loopId != FeatureTaskRuntimePhaseWorkflowDefinition.REVIEW_FIX_LOOP_ID ||
      state.isLoopLiveClaimed(edge.loopId) ||
      state.isComplete(edge.destinationPhaseId)
    ) {
      return null
    }
    val destinationRecord = state.recordFor(edge.destinationPhaseId)
      ?.takeIf { it.loopId == edge.loopId && it.edgeIteration == state.edgeIterationCount(edge.loopId) }
      ?: return null
    val edgeIteration = requireNotNull(destinationRecord.edgeIteration)
    state.reopenForReentry(edge.fromPhaseId)
    state.recordEdgeIteration(edge.loopId, edgeIteration)
    val pendingReentry = PendingReentry(
      phaseId = edge.destinationPhaseId,
      loopId = edge.loopId,
      edgeIteration = edgeIteration,
      drivingVerdict = edge.triggeringVerdict,
      expectedRepositoryCheckpoint = FeatureTaskRuntimeRunLoopDrive.reviewedCheckpointFingerprint(request, recorder),
    )
    session.transitionReentryPair(pendingReentry, pendingReentry)
    return edge.destinationPhaseId

    }

  internal fun recordBackwardEdge(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    transitions: FeatureTaskRuntimeTransitionDeclaration,
    session: FeatureTaskRuntimeRunLoopSession,
    edge: FeatureTaskRuntimeBackwardEdge,
    destinationPhaseId: String,
    loopId: String,
    edgeIteration: Int,
    verdict: FeatureTaskRuntimeVerdict,
  ) {
    val reopenedSpan = FeatureTaskRuntimeRunLoopTransitions.spanBetween(
      transitions,
      destinationPhaseId,
      edge.fromPhaseId,
    )
    reopenedSpan.forEach(state::reopenForReentry)
    if (FeatureTaskRuntimePhaseWorkflowDefinition.isRegenerationLoopId(loopId)) {
      state.invalidateProducerOutput(destinationPhaseId)
      recorder.invalidateQuarantinedProducerRecord(
        request.workflowId,
        destinationPhaseId,
        loopId,
        edgeIteration,
      )
    }
    state.recordEdgeIteration(loopId, edgeIteration)
    val pendingReentry = PendingReentry(
      phaseId = destinationPhaseId,
      loopId = loopId,
      edgeIteration = edgeIteration,
      drivingVerdict = verdict,
      expectedRepositoryCheckpoint = if (loopId == FeatureTaskRuntimePhaseWorkflowDefinition.REVIEW_FIX_LOOP_ID) {
        FeatureTaskRuntimeRunLoopDrive.reviewedCheckpointFingerprint(request, recorder)
      } else {
        null
      },
    )
    session.transitionReentryPair(pendingReentry, pendingReentry)
  }

  internal fun warnOnThresholdCrossing(
    request: FeatureTaskRuntimeRunRequest,
    diagnostics: RuntimeDiagnostics,
    edge: FeatureTaskRuntimeBackwardEdge,
    edgeIteration: Int,
  ) {
    val threshold = edge.warnAfterIterations ?: return
    if (edgeIteration != threshold + 1) return
    runCatching {
      diagnostics.warning(
        thresholdCrossingWarning(request, edge.loopId, threshold, edgeIteration),
      )
    }
  }

  internal fun thresholdCrossingWarning(
    request: FeatureTaskRuntimeRunRequest,
    loopId: String,
    threshold: Int,
    edgeIteration: Int,
  ): String = "Remediation loop '$loopId' exceeded its warning threshold of $threshold: entering iteration " +
    "$edgeIteration for issue ${request.issueKey}, workflow ${request.workflowId}, subtask " +
    "${request.goalContinuation?.subtaskId ?: request.issueKey}, spec " +
    "${request.runInvariants.specReference}."

  internal fun capExhaustedOnResume(
    session: FeatureTaskRuntimeRunLoopSession,
    state: FeatureTaskRuntimeRunState,
    transitions: FeatureTaskRuntimeTransitionDeclaration,
    request: FeatureTaskRuntimeRunRequest,
    recorder: FeatureTaskRuntimePhaseRecorder,
    phaseId: String,
  ): String? {
    if (FeatureTaskRuntimeRunLoopPhaseAttempts.operatorReopenedPhase(session, phaseId)) return null
    val record = state.recordFor(phaseId) ?: return null
    return FeatureTaskRuntimeRunLoopBackwardEdge.capExhaustionForRecord(
      state,
      transitions,
      request,
      recorder,
      phaseId,
      record,
    )
  }

  internal fun capExhaustionForRecord(
    state: FeatureTaskRuntimeRunState,
    transitions: FeatureTaskRuntimeTransitionDeclaration,
    request: FeatureTaskRuntimeRunRequest,
    recorder: FeatureTaskRuntimePhaseRecorder,
    phaseId: String,
    record: FeatureTaskRuntimePhaseRecord,
  ): String? {
    val loopId = record.loopId
    val iteration = record.edgeIteration
    if (loopId == null || iteration == null || state.isLoopLiveClaimed(loopId)) {
      return null
    }
    val edge = transitions.backwardEdges.firstOrNull { candidate ->
      candidate.loopId == loopId &&
        (candidate.destinationPhaseId == phaseId || candidate.fromPhaseId == phaseId)
    }
    if (edge?.destinationPhaseId == phaseId) {
      val sourceRecord = state.recordFor(edge.fromPhaseId)
      if (
        sourceRecord?.status?.workflowStepStatus() == WorkflowStepStatus.BLOCKED && sourceRecord.loopId == loopId &&
        sourceRecord.edgeIteration == iteration
      ) {
        return null
      }
    }
    return edge
      ?.takeIf { candidate -> blocksWhenCapExhausted(candidate, iteration) }
      ?.let {
        FeatureTaskRuntimeRunLoopPlanningBranch.capExhaustionReason(
          CapExhaustionReasonArgs(
            request = request,
            recorder = recorder,
            loopId = loopId,
            edgeIteration = iteration,
            verdict = it.triggeringVerdict,
            unresolvedFindings = emptyList<FeatureTaskRuntimeReviewFinding>(),
          ),
        )
      }

  }

  internal fun blocksWhenCapExhausted(edge: FeatureTaskRuntimeBackwardEdge, iteration: Int): Boolean =
    edge.capExhaustionBehavior == FeatureTaskRuntimeCapExhaustionBehavior.BLOCK &&
      edge.perEdgeCap?.let { iteration >= it } == true

  internal fun runPhaseFor(
    context: FeatureTaskRuntimeRunLoopContext,
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    observability: FeatureTaskRuntimeRunObservability,
    transitions: FeatureTaskRuntimeTransitionDeclaration,
    phaseGates: FeatureTaskRuntimePhaseGates,
    specSource: SpecSource,
    session: FeatureTaskRuntimeRunLoopSession,
    phaseId: String,
  ): String? {
    val briefingReentry = session.pendingReentry?.takeIf { it.phaseId == phaseId }
    if (briefingReentry != null) session.transitionPendingReentry(null)
    val reentry = briefingReentry ?: session.activeReentry?.takeIf { active ->
      transitions.backwardEdges
        .firstOrNull { it.loopId == active.loopId }
        ?.let { edge ->
          phaseId in FeatureTaskRuntimeRunLoopTransitions.spanBetween(
            transitions,
            edge.destinationPhaseId,
            edge.fromPhaseId,
          )
        } == true
    }?.copy(phaseId = phaseId)
    val outcome =
      FeatureTaskRuntimeRunLoopPlanningBranch.runPhase(
        context,
        RunPhaseArgs(
          phaseId = phaseId,
          request = request,
          state = state,
          observability = observability,
          specSource = specSource,
          reentry = reentry,
        ),
      )
    outcome.regenerationTargetPhaseId?.let {
      session.markRecordRejectionSettlementPending()
      return null
    }
    outcome.pausedReason?.let { return it }
    return outcome.blockedReason ?: run {
      val completedOutput = requireNotNull(outcome.completedOutput)
      state.recordCompleted(completedOutput)
      session.consumeOperatorBlockRetryCompletion(phaseId)
      applyPlanningStop(
        request = request,
        state = state,
        recorder = recorder,
        observability = observability,
        session = session,
        phaseGates = phaseGates,
        specSource = specSource,
        phaseId = phaseId,
        planOutput = completedOutput,
      )
    }

    }

  internal fun applyPlanningStop(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    observability: FeatureTaskRuntimeRunObservability,
    session: FeatureTaskRuntimeRunLoopSession,
    phaseGates: FeatureTaskRuntimePhaseGates,
    specSource: SpecSource,
    phaseId: String,
    planOutput: FeatureTaskRuntimePhaseOutput,
  ): String? {
    if (phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PLAN) {
      return null
    }
    return when (
      val decision = resolvePlanningStop(
        phaseGates,
        request,
        state,
        session,
        specSource,
        planOutput,
      )
    ) {
      is FeatureTaskRuntimePlanningStopDecision.Proceed -> null
      is FeatureTaskRuntimePlanningStopDecision.Decomposed -> {
        session.transitionToDecomposed(decision.report)
        null
      }
      is FeatureTaskRuntimePlanningStopDecision.Blocked -> {
        persistPlanningStopBlock(request, recorder, observability, phaseId, decision.reason)
        decision.reason
      }
    }
  }

  internal fun resolvePlanningStop(
    phaseGates: FeatureTaskRuntimePhaseGates,
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    session: FeatureTaskRuntimeRunLoopSession,
    specSource: SpecSource,
    planOutput: FeatureTaskRuntimePhaseOutput,
  ): FeatureTaskRuntimePlanningStopDecision = phaseGates.planningStopper.resolve(
    request = request,
    completedOutput = planOutput,
    completedPhaseIds = state.completedPhaseIds(),
    resolvedBranch = session.resolvedBranch,
    specSource = specSource,
  )

  internal fun persistPlanningStopBlock(
    request: FeatureTaskRuntimeRunRequest,
    recorder: FeatureTaskRuntimePhaseRecorder,
    observability: FeatureTaskRuntimeRunObservability,
    phaseId: String,
    reason: String,
  ) {
    val resolvedAgentId = FeatureTaskRuntimeAgentResolver.resolve(
      phaseId = phaseId,
      assignment = request.agentAssignment,
      invokedAgentId = request.invokedAgentId,
    ).resolvedAgentId
    recorder.recordPhaseState(
      FeatureTaskRuntimePhaseStateRequest(
        workflowId = request.workflowId,
        phaseId = phaseId,
        status = STATUS_BLOCKED,
        attemptCount = 1,
        resolvedAgentId = resolvedAgentId,
        finished = false,
        outputArtifact = null,
        blockedReason = reason,
      ),
    )
    observability.blocked(phaseId, resolvedAgentId, 1, reason)
  }

  internal fun establishBranchIfNeeded(
    phaseGates: FeatureTaskRuntimePhaseGates,
    request: FeatureTaskRuntimeRunRequest,
    observability: FeatureTaskRuntimeRunObservability,
    recorder: FeatureTaskRuntimePhaseRecorder,
    state: FeatureTaskRuntimeRunState,
    session: FeatureTaskRuntimeRunLoopSession,
    phaseId: String,
  ): String? {
    if (!isFileMutating(phaseId)) {
      return null
    }
    val setup = phaseGates.branchSetupRunner.ensureFeatureBranch(request, observability)
    return setup.blockedReason?.also { reason ->
      FeatureTaskRuntimeRunLoopPlanningBranch.persistBranchSetupBlock(request, recorder, observability, phaseId, reason)
    } ?: run {
      session.transitionResolvedBranch(requireNotNull(setup.establishedBranch))
      FeatureTaskRuntimeRunLoopPlanningBranch.clearRecoveredBranchSetupBlock(state, phaseId)
      null
    }

  }
}
