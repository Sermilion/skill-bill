package skillbill.workflow.taskruntime.validation

import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeQualityGateSelection
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeNextPhase
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition

object FeatureTaskRuntimeQualityGateRouting {
  fun selectedGatePhase(selection: FeatureTaskRuntimeQualityGateSelection): String =
    when (selection) {
      FeatureTaskRuntimeQualityGateSelection.BUILD -> FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_BUILD
      FeatureTaskRuntimeQualityGateSelection.VALIDATE -> FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE
    }

  fun applyAfterReview(
    currentPhaseId: String,
    transition: FeatureTaskRuntimeNextPhase,
    selection: FeatureTaskRuntimeQualityGateSelection,
  ): FeatureTaskRuntimeNextPhase {
    if (selection != FeatureTaskRuntimeQualityGateSelection.BUILD) {
      return transition
    }
    if (currentPhaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_BUILD) {
      return transition
    }
    val next = transition as? FeatureTaskRuntimeNextPhase.Next ?: return transition
    return if (next.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE) {
      next.copy(phaseId = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_BUILD)
    } else {
      transition
    }
  }

  fun applyAfterBuild(
    currentPhaseId: String,
    transition: FeatureTaskRuntimeNextPhase,
  ): FeatureTaskRuntimeNextPhase {
    if (currentPhaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_BUILD) {
      return transition
    }
    val next = transition as? FeatureTaskRuntimeNextPhase.Next ?: return transition
    if (next.phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE) {
      return transition
    }
    return next.copy(phaseId = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_WRITE_HISTORY)
  }
}
