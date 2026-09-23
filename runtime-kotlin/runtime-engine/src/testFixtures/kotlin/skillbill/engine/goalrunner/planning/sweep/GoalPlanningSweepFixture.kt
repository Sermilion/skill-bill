package skillbill.engine.goalrunner.planning.sweep

import skillbill.engine.goalrunner.planning.model.GoalPlanningSweepOutcome

val PREPARE_ALL_GOAL_PLANNING_SWEEP: GoalPlanningSweep =
  GoalPlanningSweep { _, _ -> GoalPlanningSweepOutcome.PreparedAll() }
