package skillbill.ports.goalrunner.persistence.model

data class GoalChildPlanningHydrationResult(
  val currentStepId: String,
  val stepUpdates: List<Any>,
  val artifacts: Any,
)
