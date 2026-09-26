package skillbill.engine.goalrunner.planning.recovery

import skillbill.goalrunner.model.ExecutionLiveness

val IDLE_GOAL_PLANNING_REFRESH_LIVENESS: GoalPlanningRefreshLiveness =
  GoalPlanningRefreshLiveness { _ -> ExecutionLiveness.IDLE }

val NO_GOAL_PLANNING_STATUS_REASON_COHERENCE: GoalPlanningStatusReasonCoherence =
  GoalPlanningStatusReasonCoherence { request -> request.snapshot }
