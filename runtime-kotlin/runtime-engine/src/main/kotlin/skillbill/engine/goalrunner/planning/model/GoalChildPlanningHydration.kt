package skillbill.engine.goalrunner.planning.model

data class GoalChildPlanningHydration(
  val currentStepId: String,
  val stepUpdates: List<Map<String, Any?>>,
  val artifacts: Map<String, Any?>,
)
