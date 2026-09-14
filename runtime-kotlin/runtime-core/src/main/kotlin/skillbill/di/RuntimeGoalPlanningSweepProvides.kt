package skillbill.di

import me.tatarka.inject.annotations.Provides
import skillbill.engine.goalrunner.planning.ChildAwareGoalPlanningRefreshLiveness
import skillbill.engine.goalrunner.planning.DefaultGoalPlanningSweep
import skillbill.engine.goalrunner.planning.GoalPlanningRefreshLiveness
import skillbill.engine.goalrunner.planning.GoalPlanningSweep

internal interface RuntimeGoalPlanningSweepProvides {
  @Provides @JvmSynthetic
  fun goalPlanningSweep(sweep: DefaultGoalPlanningSweep): GoalPlanningSweep = sweep

  @Provides @JvmSynthetic
  fun goalPlanningRefreshLiveness(adapter: ChildAwareGoalPlanningRefreshLiveness): GoalPlanningRefreshLiveness = adapter
}
