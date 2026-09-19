package skillbill.di.goal
import me.tatarka.inject.annotations.Provides
import skillbill.engine.goalrunner.planning.hydration.GoalChildPlanningHydratorPortAdapter
import skillbill.engine.goalrunner.repair.GoalRunnerChildRepairOperations
import skillbill.infrastructure.sqlite.goalrunner.manifest.WorkflowGoalRunnerManifestStore
import skillbill.infrastructure.sqlite.goalrunner.outcome.WorkflowGoalRunnerOutcomeStore
import skillbill.ports.goalrunner.persistence.GoalChildPlanningHydratorPort
import skillbill.ports.goalrunner.persistence.GoalRunnerChildRepairRunnerPort
import skillbill.ports.goalrunner.runner.GoalRunnerManifestStore
import skillbill.ports.goalrunner.runner.GoalRunnerWorkflowOutcomeStore

internal interface RuntimeGoalRunnerStoreProvides {
  @Provides @JvmSynthetic
  fun goalRunnerManifestStore(adapter: WorkflowGoalRunnerManifestStore): GoalRunnerManifestStore = adapter

  @Provides @JvmSynthetic
  fun goalRunnerWorkflowOutcomeStore(adapter: WorkflowGoalRunnerOutcomeStore): GoalRunnerWorkflowOutcomeStore = adapter

  @Provides @JvmSynthetic
  fun goalRunnerChildRepairExecutorPort(operations: GoalRunnerChildRepairOperations): GoalRunnerChildRepairRunnerPort =
    operations

  @Provides @JvmSynthetic
  fun goalChildPlanningHydratorPort(adapter: GoalChildPlanningHydratorPortAdapter): GoalChildPlanningHydratorPort =
    adapter
}
