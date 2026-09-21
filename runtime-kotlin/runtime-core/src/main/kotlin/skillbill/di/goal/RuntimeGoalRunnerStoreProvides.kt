package skillbill.di.goal
import me.tatarka.inject.annotations.Provides
import skillbill.engine.goalrunner.planning.hydration.GoalChildPlanningHydratorPortAdapter
import skillbill.engine.goalrunner.repair.GoalRunnerChildRepairOperations
import skillbill.infrastructure.sqlite.goalrunner.manifest.WorkflowGoalRunnerManifestStore
import skillbill.infrastructure.sqlite.goalrunner.outcome.WorkflowGoalRunnerOutcomeStore
import skillbill.infrastructure.sqlite.goalrunner.outcome.WorkflowGoalRunnerOutcomeStoreDependencies
import skillbill.model.RepositoryRoot
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.decomposition.DecompositionManifestProjectionWriter
import skillbill.ports.goalrunner.persistence.GoalChildPlanningHydratorPort
import skillbill.ports.goalrunner.persistence.GoalRunnerChildRepairRunnerPort
import skillbill.ports.goalrunner.runner.GoalRunnerManifestStore
import skillbill.ports.goalrunner.runner.GoalRunnerWorkflowOutcomeStore
import skillbill.ports.taskruntime.FeatureTaskRuntimeWorkerSupervisor
import skillbill.ports.workflow.decomposition.DecompositionManifestStore
import skillbill.ports.workflow.gitops.WorkflowGitOperations
import skillbill.workflow.decomposition.DecompositionManifestValidator
import skillbill.workflow.engine.WorkflowSnapshotValidator
import skillbill.workflow.goal.GoalObservabilityEventValidator
import skillbill.workflow.goal.GoalProgressEventValidator
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseOutputValidator
import java.time.Clock

class GoalRunnerManifestPersistenceDependencies(
  val database: DatabaseSessionFactory,
  val workflowSnapshotValidator: WorkflowSnapshotValidator,
  val decompositionManifestValidator: DecompositionManifestValidator,
  val decompositionManifestStore: DecompositionManifestStore,
)

class GoalRunnerManifestProjectionDependencies(
  val clock: Clock,
  val decompositionManifestWriter: DecompositionManifestProjectionWriter,
  val repositoryRoot: RepositoryRoot,
  val planningHydrator: GoalChildPlanningHydratorPort,
)

internal class GoalRunnerOutcomeValidationDependencies(
  val workflowSnapshotValidator: WorkflowSnapshotValidator,
  val goalObservabilityEventValidator: GoalObservabilityEventValidator,
  val goalProgressEventValidator: GoalProgressEventValidator,
  val gitOperations: WorkflowGitOperations,
)

internal class GoalRunnerOutcomeExecutionDependencies(
  val phaseOutputValidator: FeatureTaskRuntimePhaseOutputValidator,
  val workerSupervisor: FeatureTaskRuntimeWorkerSupervisor,
  val clock: Clock,
  val decompositionManifestValidator: DecompositionManifestValidator,
)

internal class GoalRunnerOutcomePersistenceDependencies(
  val decompositionManifestStore: DecompositionManifestStore,
  val decompositionManifestWriter: DecompositionManifestProjectionWriter,
  val childRepairExecutor: GoalRunnerChildRepairRunnerPort,
)

internal interface RuntimeGoalRunnerStoreProvides {
  @Provides @JvmSynthetic
  fun goalRunnerManifestStore(
    persistence: GoalRunnerManifestPersistenceDependencies,
    projection: GoalRunnerManifestProjectionDependencies,
  ): GoalRunnerManifestStore = WorkflowGoalRunnerManifestStore(
    persistence.database,
    persistence.workflowSnapshotValidator,
    persistence.decompositionManifestValidator,
    persistence.decompositionManifestStore,
    projection.clock,
    projection.decompositionManifestWriter,
    projection.repositoryRoot,
    projection.planningHydrator,
  )

  @Provides @JvmSynthetic
  fun goalRunnerManifestPersistenceDependencies(
    database: DatabaseSessionFactory,
    workflowSnapshotValidator: WorkflowSnapshotValidator,
    decompositionManifestValidator: DecompositionManifestValidator,
    decompositionManifestStore: DecompositionManifestStore,
  ): GoalRunnerManifestPersistenceDependencies = GoalRunnerManifestPersistenceDependencies(
    database,
    workflowSnapshotValidator,
    decompositionManifestValidator,
    decompositionManifestStore,
  )

  @Provides @JvmSynthetic
  fun goalRunnerManifestProjectionDependencies(
    clock: Clock,
    decompositionManifestWriter: DecompositionManifestProjectionWriter,
    repositoryRoot: RepositoryRoot,
    planningHydrator: GoalChildPlanningHydratorPort,
  ): GoalRunnerManifestProjectionDependencies = GoalRunnerManifestProjectionDependencies(
    clock,
    decompositionManifestWriter,
    repositoryRoot,
    planningHydrator,
  )

  @Provides @JvmSynthetic
  fun goalRunnerWorkflowOutcomeStore(
    database: DatabaseSessionFactory,
    validation: GoalRunnerOutcomeValidationDependencies,
    execution: GoalRunnerOutcomeExecutionDependencies,
    persistence: GoalRunnerOutcomePersistenceDependencies,
  ): GoalRunnerWorkflowOutcomeStore = WorkflowGoalRunnerOutcomeStore(
    database,
    WorkflowGoalRunnerOutcomeStoreDependencies(
      validation.workflowSnapshotValidator,
      validation.goalObservabilityEventValidator,
      validation.goalProgressEventValidator,
      validation.gitOperations,
      execution.phaseOutputValidator,
      execution.workerSupervisor,
      execution.clock,
      execution.decompositionManifestValidator,
      persistence.decompositionManifestStore,
      persistence.decompositionManifestWriter,
      persistence.childRepairExecutor,
    ),
  )

  @Provides @JvmSynthetic
  fun goalRunnerOutcomeValidationDependencies(
    workflowSnapshotValidator: WorkflowSnapshotValidator,
    goalObservabilityEventValidator: GoalObservabilityEventValidator,
    goalProgressEventValidator: GoalProgressEventValidator,
    gitOperations: WorkflowGitOperations,
  ): GoalRunnerOutcomeValidationDependencies = GoalRunnerOutcomeValidationDependencies(
    workflowSnapshotValidator,
    goalObservabilityEventValidator,
    goalProgressEventValidator,
    gitOperations,
  )

  @Provides @JvmSynthetic
  fun goalRunnerOutcomeExecutionDependencies(
    phaseOutputValidator: FeatureTaskRuntimePhaseOutputValidator,
    workerSupervisor: FeatureTaskRuntimeWorkerSupervisor,
    clock: Clock,
    decompositionManifestValidator: DecompositionManifestValidator,
  ): GoalRunnerOutcomeExecutionDependencies = GoalRunnerOutcomeExecutionDependencies(
    phaseOutputValidator,
    workerSupervisor,
    clock,
    decompositionManifestValidator,
  )

  @Provides @JvmSynthetic
  fun goalRunnerOutcomePersistenceDependencies(
    decompositionManifestStore: DecompositionManifestStore,
    decompositionManifestWriter: DecompositionManifestProjectionWriter,
    childRepairExecutor: GoalRunnerChildRepairRunnerPort,
  ): GoalRunnerOutcomePersistenceDependencies = GoalRunnerOutcomePersistenceDependencies(
    decompositionManifestStore,
    decompositionManifestWriter,
    childRepairExecutor,
  )

  @Provides @JvmSynthetic
  fun goalRunnerChildRepairExecutorPort(operations: GoalRunnerChildRepairOperations): GoalRunnerChildRepairRunnerPort =
    operations

  @Provides @JvmSynthetic
  fun goalChildPlanningHydratorPort(adapter: GoalChildPlanningHydratorPortAdapter): GoalChildPlanningHydratorPort =
    adapter
}
