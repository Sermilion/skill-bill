package skillbill.engine.featuretask.runloop.core

import skillbill.engine.featuretask.lifecycle.checkpoint.FeatureTaskRuntimeCheckpointMessage
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeRunRequest
import skillbill.engine.featuretask.runloop.checkpoint.FeatureTaskRuntimeRunLoopCheckpointRemediation
import skillbill.engine.featuretask.runloop.observability.loopEdge
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeQualityGateSelection
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeBackwardEdge
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeNextPhase
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeTransitionDeclaration
import skillbill.workflow.taskruntime.model.validation.FeatureTaskRuntimeVerdict
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition
object FeatureTaskRuntimeRunLoopTransitions {
  internal fun qualityGateSelection(request: FeatureTaskRuntimeRunRequest): FeatureTaskRuntimeQualityGateSelection =
    request.goalContinuation?.qualityGateSelection ?: FeatureTaskRuntimeQualityGateSelection.VALIDATE

  internal fun transitionTarget(
    context: FeatureTaskRuntimeRunLoopContext,
    phaseId: String,
    edge: FeatureTaskRuntimeBackwardEdge?,
    effectiveVerdict: FeatureTaskRuntimeVerdict,
    transition: FeatureTaskRuntimeNextPhase,
  ): String? = with(context) {
    when (transition) {
      is FeatureTaskRuntimeNextPhase.TerminalAdvance -> null
      is FeatureTaskRuntimeNextPhase.TerminalBlock -> {
        FeatureTaskRuntimeRunLoopPlanningBranch.blockOnCapExhaustion(
          BlockOnCapExhaustionArgs(
            request = request,
            state = state,
            recorder = recorder,
            observability = observability,
            session = session,
            goalContinuationRecorder = goalContinuationRecorder,
            specSource = specSource,
            phaseId = phaseId,
            transition = transition,
          ),
        )
        null
      }
      is FeatureTaskRuntimeNextPhase.Next -> nextTransitionTarget(
        context,
        phaseId,
        edge,
        effectiveVerdict,
        transition,
      )
    }
  }

  internal fun nextTransitionTarget(
    context: FeatureTaskRuntimeRunLoopContext,
    phaseId: String,
    edge: FeatureTaskRuntimeBackwardEdge?,
    effectiveVerdict: FeatureTaskRuntimeVerdict,
    transition: FeatureTaskRuntimeNextPhase.Next,
  ): String? = with(context) {
    val loopId = transition.loopId
    return when {
      loopId == null && !establishForwardCheckpoint(
        context,
        precedingPhaseId = phaseId,
        destinationPhaseId = transition.phaseId,
      ) -> null
      loopId == null -> transition.phaseId
      reentersMutatingPhase(transitions, requireNotNull(edge), transition.phaseId) &&
        !with(FeatureTaskRuntimeRunLoopCheckpointRemediation) {
          FeatureTaskRuntimeRunLoopCheckpointRemediation.establishRemediationCheckpoint(context, phaseId, loopId)
        } -> null
      else -> {
        with(FeatureTaskRuntimeRunLoopBackwardEdge) {
          FeatureTaskRuntimeRunLoopBackwardEdge.recordBackwardEdge(
            context,
            session,
            edge = requireNotNull(edge),
            edgeIteration = requireNotNull(transition.edgeIteration),
            verdict = effectiveVerdict,
          )
          observability.loopEdge(
            transition.phaseId,
            loopId,
            requireNotNull(transition.edgeIteration),
            effectiveVerdict,
          )
          FeatureTaskRuntimeRunLoopBackwardEdge.warnOnThresholdCrossing(
            request,
            diagnostics,
            requireNotNull(edge),
            requireNotNull(transition.edgeIteration),
          )
        }
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
    context: FeatureTaskRuntimeRunLoopContext,
    precedingPhaseId: String,
    destinationPhaseId: String,
  ): Boolean = with(context) {
    if (
      precedingPhaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT &&
      destinationPhaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW
    ) {
      with(FeatureTaskRuntimeRunLoopCheckpointRemediation) {
        FeatureTaskRuntimeRunLoopCheckpointRemediation.checkpointEstablished(
          context,
          precedingPhaseId = precedingPhaseId,
          loopId = null,
          intent = FeatureTaskRuntimeCheckpointMessage.INTENT_AUDITED_IMPLEMENTATION,
          blockedReason = { branch, error ->
            FeatureTaskRuntimeRunLoopPlanningBranch.auditReviewCheckpointBlockedReason(branch, error)
          },
        )
      }
    } else {
      true
    }
  }
}
