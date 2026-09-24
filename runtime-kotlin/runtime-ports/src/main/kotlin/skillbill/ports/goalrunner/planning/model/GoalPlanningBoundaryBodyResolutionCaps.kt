package skillbill.ports.goalrunner.planning.model

data class GoalPlanningBoundaryBodyResolutionCaps(
  val maxSelectedBodies: Int = GoalPlanningContext.MAX_SELECTED_BODIES,
  val maxBodyBytes: Int = GoalPlanningContext.MAX_BODY_BYTES,
  val maxTotalBodyBytes: Int = GoalPlanningContext.MAX_TOTAL_BODY_BYTES,
) {
  companion object {
    val PLANNING: GoalPlanningBoundaryBodyResolutionCaps = GoalPlanningBoundaryBodyResolutionCaps()
    val VERIFICATION: GoalPlanningBoundaryBodyResolutionCaps =
      GoalPlanningBoundaryBodyResolutionCaps(
        maxSelectedBodies = GoalPlanningContext.VERIFICATION_MAX_SELECTED_BODIES,
        maxBodyBytes = GoalPlanningContext.VERIFICATION_MAX_BODY_BYTES,
        maxTotalBodyBytes = GoalPlanningContext.VERIFICATION_MAX_TOTAL_BODY_BYTES,
      )
  }
}
