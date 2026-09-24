package skillbill.di.goal

import me.tatarka.inject.annotations.Provides
import skillbill.engine.goalrunner.planning.recovery.ChildAwareGoalPlanningRefreshLiveness
import skillbill.engine.goalrunner.planning.recovery.GoalPlanningRefreshLiveness
import skillbill.engine.goalrunner.planning.sweep.DefaultGoalPlanningSweep
import skillbill.engine.goalrunner.planning.sweep.GoalPlanningSweep

internal interface RuntimeGoalPlanningSweepProvides {
  @Provides
  fun goalPlanningSweep(sweep: DefaultGoalPlanningSweep): GoalPlanningSweep = sweep

  @Provides
  fun goalPlanningRefreshLiveness(adapter: ChildAwareGoalPlanningRefreshLiveness): GoalPlanningRefreshLiveness = adapter
}
