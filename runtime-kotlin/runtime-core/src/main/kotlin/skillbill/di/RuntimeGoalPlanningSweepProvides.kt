package skillbill.di

import me.tatarka.inject.annotations.Provides
import skillbill.engine.goalrunner.planning.recovery.ChildAwareGoalPlanningRefreshLiveness
import skillbill.engine.goalrunner.planning.sweep.DefaultGoalPlanningSweep
import skillbill.engine.goalrunner.planning.recovery.GoalPlanningRefreshLiveness
import skillbill.engine.goalrunner.planning.sweep.GoalPlanningSweep

internal interface RuntimeGoalPlanningSweepProvides {
  @Provides @JvmSynthetic
  fun goalPlanningSweep(sweep: DefaultGoalPlanningSweep): GoalPlanningSweep = sweep

  @Provides @JvmSynthetic
  fun goalPlanningRefreshLiveness(adapter: ChildAwareGoalPlanningRefreshLiveness): GoalPlanningRefreshLiveness = adapter
}
