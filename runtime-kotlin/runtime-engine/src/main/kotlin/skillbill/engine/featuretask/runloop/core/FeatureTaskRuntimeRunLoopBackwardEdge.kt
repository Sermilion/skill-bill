package skillbill.engine.featuretask.runloop.core

import skillbill.application.decomposition.specSource
import skillbill.engine.diagnostics.RuntimeDiagnosticsBestEffortWarning
import skillbill.engine.featuretask.lifecycle.branch.Blocked
import skillbill.engine.featuretask.lifecycle.core.FeatureTaskRuntimeAgentResolver
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimePlanningStopDecision
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeRunRequest
import skillbill.engine.featuretask.model.phase.FeatureTaskRuntimePhaseStateRequest
import skillbill.engine.featuretask.phase.record.FeatureTaskRuntimePhaseRecorder
import skillbill.engine.featuretask.runloop.observability.FeatureTaskRuntimeRunObservability
import skillbill.engine.featuretask.runloop.observability.blocked
import skillbill.engine.featuretask.runloop.phase.FeatureTaskRuntimeRunLoopPhaseBlocking
import skillbill.engine.featuretask.runloop.state.FeatureTaskRuntimeRunState
import skillbill.engine.featuretask.runner.STATUS_BLOCKED
import skillbill.engine.featuretask.runner.isFileMutating
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.model.validation.FeatureTaskRuntimeVerdict
import skillbill.workflow.model.workflowStepStatus
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeBackwardEdge
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeCapExhaustionBehavior
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseRecord
import skillbill.workflow.taskruntime.model.review.FeatureTaskRuntimeReviewFinding
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition

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
    val destinationRecord =
      state.recordFor(edge.destinationPhaseId)
        ?.takeIf { it.loopId == edge.loopId && it.edgeIteration == state.edgeIterationCount(edge.loopId) }
        ?: return null
    val edgeIteration = requireNotNull(destinationRecord.edgeIteration)
    state.reopenForReentry(edge.fromPhaseId)
    state.recordEdgeIteration(edge.loopId, edgeIteration)
    val pendingReentry =
      PendingReentry(
        phaseId = edge.destinationPhaseId,
        loopId = edge.loopId,
        edgeIteration = edgeIteration,
        drivingVerdict = edge.triggeringVerdict,
        expectedRepositoryCheckpoint =
          FeatureTaskRuntimeRunLoopPhaseBlocking.reviewedCheckpointFingerprint(request, recorder),
      )
    session.transitionReentryPair(pendingReentry, pendingReentry)
    return edge.destinationPhaseId
  }

  internal fun recordBackwardEdge(
    context: FeatureTaskRuntimeRunLoopContext,
    session: FeatureTaskRuntimeRunLoopSession,
    edge: FeatureTaskRuntimeBackwardEdge,
    edgeIteration: Int,
    verdict: FeatureTaskRuntimeVerdict,
  ) = with(context) {
    val destinationPhaseId = edge.destinationPhaseId
    val loopId = edge.loopId
    val reopenedSpan =
      spanBetween(
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
    val pendingReentry =
      PendingReentry(
        phaseId = destinationPhaseId,
        loopId = loopId,
        edgeIteration = edgeIteration,
        drivingVerdict = verdict,
        expectedRepositoryCheckpoint =
          if (loopId == FeatureTaskRuntimePhaseWorkflowDefinition.REVIEW_FIX_LOOP_ID) {
            FeatureTaskRuntimeRunLoopPhaseBlocking.reviewedCheckpointFingerprint(request, recorder)
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
    RuntimeDiagnosticsBestEffortWarning.record(
      diagnostics,
      thresholdCrossingWarning(request, edge.loopId, threshold, edgeIteration),
    )
  }

  internal fun thresholdCrossingWarning(
    request: FeatureTaskRuntimeRunRequest,
    loopId: String,
    threshold: Int,
    edgeIteration: Int,
  ): String =
    "Remediation loop '$loopId' exceeded its warning threshold of $threshold: entering iteration " +
      "$edgeIteration for issue ${request.issueKey}, workflow ${request.workflowId}, subtask " +
      "${request.goalContinuation?.subtaskId ?: request.issueKey}, spec " +
      "${request.runInvariants.specReference}."

  internal fun capExhaustedOnResume(
    context: FeatureTaskRuntimeRunLoopContext,
    phaseId: String,
  ): String? =
    with(context) {
      if (FeatureTaskRuntimeRunLoopPhaseBlocking.operatorReopenedPhase(session, phaseId)) return null
      val record = state.recordFor(phaseId) ?: return null
      return FeatureTaskRuntimeRunLoopBackwardEdge.capExhaustionForRecord(
        context,
        phaseId,
        record,
      )
    }

  internal fun capExhaustionForRecord(
    context: FeatureTaskRuntimeRunLoopContext,
    phaseId: String,
    record: FeatureTaskRuntimePhaseRecord,
  ): String? =
    with(context) {
      val loopId = record.loopId
      val iteration = record.edgeIteration
      if (loopId == null || iteration == null || state.isLoopLiveClaimed(loopId)) {
        return null
      }
      val edge =
        transitions.backwardEdges.firstOrNull { candidate ->
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

  internal fun blocksWhenCapExhausted(
    edge: FeatureTaskRuntimeBackwardEdge,
    iteration: Int,
  ): Boolean =
    edge.capExhaustionBehavior == FeatureTaskRuntimeCapExhaustionBehavior.BLOCK &&
      edge.perEdgeCap?.let { iteration >= it } == true

  internal fun runPhaseFor(
    context: FeatureTaskRuntimeRunLoopContext,
    phaseId: String,
  ): String? =
    with(context) {
      val briefingReentry = session.pendingReentry?.takeIf { it.phaseId == phaseId }
      if (briefingReentry != null) session.transitionPendingReentry(null)
      val reentry =
        briefingReentry ?: session.activeReentry?.takeIf { active ->
          transitions.backwardEdges
            .firstOrNull { it.loopId == active.loopId }
            ?.let { edge ->
              phaseId in
                spanBetween(
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
          context = context,
          phaseId = phaseId,
          planOutput = completedOutput,
        )
      }
    }

  internal fun applyPlanningStop(
    context: FeatureTaskRuntimeRunLoopContext,
    phaseId: String,
    planOutput: FeatureTaskRuntimePhaseOutput,
  ): String? =
    with(context) {
      if (phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PLAN) {
        return null
      }
      return when (
        val decision =
          resolvePlanningStop(
            context,
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
    context: FeatureTaskRuntimeRunLoopContext,
    planOutput: FeatureTaskRuntimePhaseOutput,
  ): FeatureTaskRuntimePlanningStopDecision =
    context.phaseGates.planningStopper.resolve(
      request = context.request,
      completedOutput = planOutput,
      completedPhaseIds = context.state.completedPhaseIds(),
      resolvedBranch = context.session.resolvedBranch,
      specSource = context.specSource,
    )

  internal fun persistPlanningStopBlock(
    request: FeatureTaskRuntimeRunRequest,
    recorder: FeatureTaskRuntimePhaseRecorder,
    observability: FeatureTaskRuntimeRunObservability,
    phaseId: String,
    reason: String,
  ) {
    val resolvedAgentId =
      FeatureTaskRuntimeAgentResolver.resolve(
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
    context: FeatureTaskRuntimeRunLoopContext,
    phaseId: String,
  ): String? =
    with(context) {
      if (!isFileMutating(phaseId)) {
        return null
      }
      val setup =
        phaseGates.branchSetupRunner.ensureFeatureBranch(
          request,
          observability,
        )
      return setup.blockedReason?.also { reason ->
        FeatureTaskRuntimeRunLoopPhaseBlocking.persistBranchSetupBlock(
          request,
          recorder,
          observability,
          phaseId,
          reason,
        )
      } ?: run {
        session.transitionResolvedBranch(requireNotNull(setup.establishedBranch))
        FeatureTaskRuntimeRunLoopPhaseBlocking.clearRecoveredBranchSetupBlock(state, phaseId)
        null
      }
    }
}
