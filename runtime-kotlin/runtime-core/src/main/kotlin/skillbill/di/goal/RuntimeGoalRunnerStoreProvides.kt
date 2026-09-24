package skillbill.di.goal

import me.tatarka.inject.annotations.Provides
import skillbill.engine.goalrunner.manifest.WorkflowGoalRunnerManifestStore
import skillbill.engine.goalrunner.persist.WorkflowGoalRunnerOutcomeStore
import skillbill.engine.goalrunner.planning.hydration.GoalChildPlanningHydratorPortAdapter
import skillbill.engine.goalrunner.repair.GoalRunnerChildRepairOperations
import skillbill.ports.goalrunner.persistence.GoalChildPlanningHydratorPort
import skillbill.ports.goalrunner.persistence.GoalRunnerChildRepairRunnerPort
import skillbill.ports.goalrunner.runner.GoalRunnerManifestStore
import skillbill.ports.goalrunner.runner.GoalRunnerWorkflowOutcomeStore

internal interface RuntimeGoalRunnerStoreProvides {
  @Provides
  fun goalRunnerManifestStore(store: WorkflowGoalRunnerManifestStore): GoalRunnerManifestStore = store

  @Provides
  fun goalRunnerWorkflowOutcomeStore(store: WorkflowGoalRunnerOutcomeStore): GoalRunnerWorkflowOutcomeStore = store

  @Provides
  fun goalRunnerChildRepairExecutorPort(operations: GoalRunnerChildRepairOperations): GoalRunnerChildRepairRunnerPort =
    operations

  @Provides
  fun goalChildPlanningHydratorPort(adapter: GoalChildPlanningHydratorPortAdapter): GoalChildPlanningHydratorPort =
    adapter
}
