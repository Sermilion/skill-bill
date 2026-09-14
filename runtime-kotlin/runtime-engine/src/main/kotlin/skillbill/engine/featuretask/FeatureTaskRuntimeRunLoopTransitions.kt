package skillbill.engine.featuretask

import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunRequest
import skillbill.workflow.decomposition.model.SpecSource
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeBackwardEdge
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeNextPhase
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeQualityGateSelection
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeTransitionDeclaration
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeVerdict
import skillbill.ports.diagnostics.RuntimeDiagnostics

object FeatureTaskRuntimeRunLoopTransitions {
  internal fun qualityGateSelection(request: FeatureTaskRuntimeRunRequest): FeatureTaskRuntimeQualityGateSelection =
    request.goalContinuation?.qualityGateSelection ?: FeatureTaskRuntimeQualityGateSelection.VALIDATE

  internal fun transitionTarget(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, observability: FeatureTaskRuntimeRunObservability, session: FeatureTaskRuntimeRunLoopSession, goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder, diagnostics: RuntimeDiagnostics, phaseGates: FeatureTaskRuntimePhaseGates, transitions: FeatureTaskRuntimeTransitionDeclaration, specSource: SpecSource, phaseId: String, edge: FeatureTaskRuntimeBackwardEdge?, effectiveVerdict: FeatureTaskRuntimeVerdict, transition: FeatureTaskRuntimeNextPhase): String? = when (transition) {
    is FeatureTaskRuntimeNextPhase.TerminalAdvance -> null
    is FeatureTaskRuntimeNextPhase.TerminalBlock -> {
      FeatureTaskRuntimeRunLoopPlanningBranch.blockOnCapExhaustion(
        request, state, recorder, observability, session,
        specSource,
        phaseId,
        transition,
      )
      null
    }
    is FeatureTaskRuntimeNextPhase.Next -> nextTransitionTarget(
      request, state, recorder, observability, session,
      goalContinuationRecorder,
      diagnostics,
      phaseGates,
      transitions,
      phaseId,
      edge,
      effectiveVerdict,
      transition,
    )
  }

  internal fun nextTransitionTarget(
    request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, observability: FeatureTaskRuntimeRunObservability, session: FeatureTaskRuntimeRunLoopSession,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    diagnostics: RuntimeDiagnostics,
    phaseGates: FeatureTaskRuntimePhaseGates,
    transitions: FeatureTaskRuntimeTransitionDeclaration,
    phaseId: String,
    edge: FeatureTaskRuntimeBackwardEdge?,
    effectiveVerdict: FeatureTaskRuntimeVerdict,
    transition: FeatureTaskRuntimeNextPhase.Next,
  ): String? {
    val loopId = transition.loopId
    return when {
      loopId == null && !establishForwardCheckpoint(
        request, state, recorder, session,
        diagnostics,
        phaseGates,
        precedingPhaseId = phaseId,
        destinationPhaseId = transition.phaseId,
      ) -> null
      loopId == null -> transition.phaseId
      reentersMutatingPhase(transitions, requireNotNull(edge), transition.phaseId) &&
        !FeatureTaskRuntimeRunLoopCheckpointRemediation.establishRemediationCheckpoint(
          request, state, recorder, session,
          goalContinuationRecorder,
          diagnostics,
          phaseGates,
          phaseId,
          loopId,
        ) -> null
      else -> {
        FeatureTaskRuntimeRunLoopBackwardEdge.recordBackwardEdge(
          request, state, recorder, observability, session,
          diagnostics,
          transitions,
          BackwardEdgeRecordArgs(
            edge = edge,
            destinationPhaseId = transition.phaseId,
            loopId = loopId,
            edgeIteration = requireNotNull(transition.edgeIteration),
            verdict = effectiveVerdict,
          ),
        )
        transition.phaseId
      }
    }
  }

  internal fun reentersMutatingPhase(
    transitions: FeatureTaskRuntimeTransitionDeclaration,
    edge: FeatureTaskRuntimeBackwardEdge,
    destinationPhaseId: String,
  ): Boolean = spanBetween(
    transitions,
    destinationPhaseId,
    edge.fromPhaseId,
  ).any(FeatureTaskRuntimePhaseWorkflowDefinition::isMutatingPhase)

  internal fun spanBetween(
    transitions: FeatureTaskRuntimeTransitionDeclaration,
    destinationPhaseId: String,
    sourcePhaseId: String,
  ): List<String> = transitions.spanBetween(destinationPhaseId, sourcePhaseId)

  internal fun establishForwardCheckpoint(
    request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, session: FeatureTaskRuntimeRunLoopSession,
    diagnostics: RuntimeDiagnostics,
    phaseGates: FeatureTaskRuntimePhaseGates,
    precedingPhaseId: String,
    destinationPhaseId: String,
  ): Boolean = if (
    precedingPhaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT &&
    destinationPhaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW
  ) {
    FeatureTaskRuntimeRunLoopCheckpointRemediation.checkpointEstablished(
      request, state, recorder, session,
      diagnostics,
      phaseGates,
      precedingPhaseId = precedingPhaseId,
      loopId = null,
      intent = FeatureTaskRuntimeCheckpointMessage.INTENT_AUDITED_IMPLEMENTATION,
      blockedReason = { branch, error ->
        FeatureTaskRuntimeRunLoopPlanningBranch.auditReviewCheckpointBlockedReason(branch, error)
      },
    )
  } else {
    true
  }
}
