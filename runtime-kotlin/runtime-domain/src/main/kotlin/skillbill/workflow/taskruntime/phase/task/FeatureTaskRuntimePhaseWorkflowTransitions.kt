package skillbill.workflow.taskruntime.phase.task
import skillbill.workflow.engine.model.WorkflowDefinition
import skillbill.workflow.taskruntime.artifact.phaseId
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeBackwardEdge
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeBackwardEdgeCapScope
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeCapExhaustionBehavior
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseEntryGate
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeTransitionDeclaration
import skillbill.workflow.taskruntime.model.validation.FeatureTaskRuntimeVerdict
import skillbill.workflow.taskruntime.review.definition

internal object FeatureTaskRuntimePhaseWorkflowTransitions {
  fun transitions(definition: WorkflowDefinition): FeatureTaskRuntimeTransitionDeclaration =
    FeatureTaskRuntimeTransitionDeclaration(
      forwardPhaseIds = definition.stepIds,
      entryGates = listOf(
        FeatureTaskRuntimePhaseEntryGate(
          phaseId = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW,
          requiredPhaseId = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT,
          requiredVerdict = FeatureTaskRuntimeVerdict.SATISFIED,
        ),
        FeatureTaskRuntimePhaseEntryGate(
          phaseId = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT_FIX,
          requiredPhaseId = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VERIFY_FINDINGS,
          requiredVerdict = FeatureTaskRuntimeVerdict.FINDINGS_VERIFIED,
        ),
      ),
      backwardEdges = listOf(
        FeatureTaskRuntimeBackwardEdge(
          fromPhaseId = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VERIFY_FINDINGS,
          triggeringVerdict = FeatureTaskRuntimeVerdict.FINDINGS_VERIFIED,
          destinationPhaseId = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT_FIX,
          loopId = FeatureTaskRuntimePhaseWorkflowDefinition.REVIEW_FIX_LOOP_ID,
          perEdgeCap = 1,
          capExhaustionBehavior = FeatureTaskRuntimeCapExhaustionBehavior.ADVANCE,
          capScope = FeatureTaskRuntimeBackwardEdgeCapScope.PER_SUBTASK,
        ),
      ),
      loopOnlyPhaseIds = setOf(
        FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT_FIX,
        FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_BUILD,
      ),
      loopOnlySuccessors = emptyMap(),
    )

  fun backwardEdgeForLoop(
    transitions: FeatureTaskRuntimeTransitionDeclaration,
    loopId: String,
  ): FeatureTaskRuntimeBackwardEdge? = transitions.backwardEdges.firstOrNull { it.loopId == loopId }
}
