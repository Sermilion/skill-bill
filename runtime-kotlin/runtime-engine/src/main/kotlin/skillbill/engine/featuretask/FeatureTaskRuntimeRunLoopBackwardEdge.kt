package skillbill.engine.featuretask

import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunRequest
import skillbill.engine.featuretask.model.FeatureTaskRuntimePhaseStateRequest
import skillbill.engine.featuretask.model.FeatureTaskRuntimePlanningStopDecision
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.model.workflowStepStatus
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeBackwardEdge
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeCapExhaustionBehavior
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseRecord
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeTransitionDeclaration
import skillbill.workflow.decomposition.model.SpecSource
import skillbill.engine.featuretask.FeatureTaskRuntimePhaseGates
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseOutputValidator
import java.time.Clock
import skillbill.engine.featuretask.FeatureTaskRuntimeGoalContinuationRecorder
import skillbill.engine.featuretask.FeatureTaskPhaseSettlementService
import skillbill.application.idestatus.AgentActivityStampWriter
import skillbill.ports.goalrunner.runner.GoalRunnerSubtaskLauncher

object FeatureTaskRuntimeRunLoopBackwardEdge {
  internal fun resumeInFlightReviewFix(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, session: FeatureTaskRuntimeRunLoopSession, edge: FeatureTaskRuntimeBackwardEdge): String? {
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

  internal fun recordBackwardEdge(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, observability: FeatureTaskRuntimeRunObservability, session: FeatureTaskRuntimeRunLoopSession, diagnostics: RuntimeDiagnostics, transitions: FeatureTaskRuntimeTransitionDeclaration, args: BackwardEdgeRecordArgs){
    val edge = args.edge
    val destinationPhaseId = args.destinationPhaseId
    val loopId = args.loopId
    val edgeIteration = args.edgeIteration
    val verdict = args.verdict
    val reopenedSpan = FeatureTaskRuntimeRunLoopTransitions.spanBetween(transitions, destinationPhaseId, edge.fromPhaseId)
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
    observability.loopEdge(destinationPhaseId, loopId, edgeIteration, verdict)
    warnOnThresholdCrossing(request, diagnostics, edge, edgeIteration)
  }

  internal fun warnOnThresholdCrossing(request: FeatureTaskRuntimeRunRequest, diagnostics: RuntimeDiagnostics, edge: FeatureTaskRuntimeBackwardEdge, edgeIteration: Int){
    val threshold = edge.warnAfterIterations ?: return
    if (edgeIteration != threshold + 1) return
    runCatching {
      diagnostics.warning(
        thresholdCrossingWarning(request, edge.loopId, threshold, edgeIteration),
      )
    }
  }

  internal fun thresholdCrossingWarning(request: FeatureTaskRuntimeRunRequest, loopId: String, threshold: Int, edgeIteration: Int): String = "Remediation loop '$loopId' exceeded its warning threshold of $threshold: entering iteration " +
    "$edgeIteration for issue ${request.issueKey}, workflow ${request.workflowId}, subtask " +
    "${request.goalContinuation?.subtaskId ?: request.issueKey}, spec " +
    "${request.runInvariants.specReference}."

  internal fun capExhaustedOnResume(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, session: FeatureTaskRuntimeRunLoopSession, transitions: FeatureTaskRuntimeTransitionDeclaration, phaseId: String): String? {
    if (FeatureTaskRuntimeRunLoopPhaseAttempts.operatorReopenedPhase(session, phaseId)) return null
    val record = state.recordFor(phaseId) ?: return null
    return capExhaustionForRecord(request, state, recorder, transitions, phaseId, record)
  }

  internal fun capExhaustionForRecord(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, transitions: FeatureTaskRuntimeTransitionDeclaration, phaseId: String, record: FeatureTaskRuntimePhaseRecord): String? {
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
        FeatureTaskRuntimeRunLoopPlanningBranch.capExhaustionReason(request, recorder, loopId, iteration, it.triggeringVerdict)
      }
  }

  internal fun blocksWhenCapExhausted(edge: FeatureTaskRuntimeBackwardEdge, iteration: Int): Boolean =
    edge.capExhaustionBehavior == FeatureTaskRuntimeCapExhaustionBehavior.BLOCK &&
      edge.perEdgeCap?.let { iteration >= it } == true

  internal fun runPhaseFor(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, observability: FeatureTaskRuntimeRunObservability, session: FeatureTaskRuntimeRunLoopSession, goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder, phaseSettlementService: FeatureTaskPhaseSettlementService, outputValidator: FeatureTaskRuntimePhaseOutputValidator, diagnostics: RuntimeDiagnostics, phaseGates: FeatureTaskRuntimePhaseGates, clock: Clock, transitions: FeatureTaskRuntimeTransitionDeclaration, specSource: SpecSource, phaseTokenAccumulator: MutableMap<String, Pair<Int, Int>>?, subtaskLauncher: GoalRunnerSubtaskLauncher, activityStampWriter: AgentActivityStampWriter, phaseId: String): String? {
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
    val outcome = FeatureTaskRuntimeRunLoopPlanningBranch.runPhase(request, state, recorder, observability, session, goalContinuationRecorder, phaseSettlementService, outputValidator, diagnostics, phaseGates, clock, transitions, specSource, phaseTokenAccumulator, subtaskLauncher, activityStampWriter, RunPhaseArgs(
        phaseId = phaseId,
        request = request,
        state = state,
        observability = observability,
        specSource = specSource,
        reentry = reentry,
        phaseTokenAccumulator = phaseTokenAccumulator,
      ))
    outcome.regenerationTargetPhaseId?.let {
      session.markRecordRejectionSettlementPending()
      return null
    }
    outcome.pausedReason?.let { return it }
    return outcome.blockedReason ?: run {
      val completedOutput = requireNotNull(outcome.completedOutput)
      state.recordCompleted(completedOutput)
      session.consumeOperatorBlockRetryCompletion(phaseId)
      applyPlanningStop(request, state, recorder, observability, session, phaseGates, specSource, phaseId, completedOutput)
    }
  }

  internal fun isLoopDestination( transitions: FeatureTaskRuntimeTransitionDeclaration, reentry: PendingReentry): Boolean =
    transitions.backwardEdges.firstOrNull { it.loopId == reentry.loopId }?.destinationPhaseId == reentry.phaseId

  internal fun applyPlanningStop(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, observability: FeatureTaskRuntimeRunObservability, session: FeatureTaskRuntimeRunLoopSession, phaseGates: FeatureTaskRuntimePhaseGates, specSource: SpecSource, phaseId: String, planOutput: FeatureTaskRuntimePhaseOutput): String? {
    if (phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PLAN) {
      return null
    }
    return when (val decision = resolvePlanningStop(request, state, session, phaseGates, specSource, planOutput)) {
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

  internal fun resolvePlanningStop(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, session: FeatureTaskRuntimeRunLoopSession, phaseGates: FeatureTaskRuntimePhaseGates, specSource: SpecSource, planOutput: FeatureTaskRuntimePhaseOutput): FeatureTaskRuntimePlanningStopDecision = phaseGates.planningStopper.resolve(
    request = request,
    completedOutput = planOutput,
    completedPhaseIds = state.completedPhaseIds(),
    resolvedBranch = session.resolvedBranch,
    specSource = specSource,
  )

  internal fun persistPlanningStopBlock(request: FeatureTaskRuntimeRunRequest, recorder: FeatureTaskRuntimePhaseRecorder, observability: FeatureTaskRuntimeRunObservability, phaseId: String, reason: String){
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

  internal fun establishBranchIfNeeded(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, observability: FeatureTaskRuntimeRunObservability, session: FeatureTaskRuntimeRunLoopSession, phaseGates: FeatureTaskRuntimePhaseGates, phaseId: String): String? {
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
