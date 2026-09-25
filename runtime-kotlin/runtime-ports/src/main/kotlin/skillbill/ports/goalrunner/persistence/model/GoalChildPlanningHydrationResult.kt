package skillbill.ports.goalrunner.persistence.model

import skillbill.workflow.engine.model.WorkflowArtifactPatch
import skillbill.workflow.engine.model.WorkflowStepUpdates

data class GoalChildPlanningHydrationResult(
  val currentStepId: String,
  val stepUpdates: WorkflowStepUpdates,
  val artifacts: WorkflowArtifactPatch,
)
