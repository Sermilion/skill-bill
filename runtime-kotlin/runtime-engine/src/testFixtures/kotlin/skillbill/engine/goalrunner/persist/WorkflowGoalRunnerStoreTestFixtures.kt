package skillbill.engine.goalrunner.persist
import skillbill.application.TestRepositoryEnclosingRoot
import skillbill.application.realFeatureTaskRuntimePhaseOutputValidator
import skillbill.engine.featuretask.lifecycle.core.AcceptingFeatureTaskRuntimeWireArtifactValidator
import skillbill.engine.goalrunner.repair.GoalRunnerChildRepairOperations
import skillbill.infrastructure.sqlite.goalrunner.manifest.WorkflowGoalRunnerManifestStore
import skillbill.infrastructure.sqlite.goalrunner.outcome.WorkflowGoalRunnerOutcomeStore
import skillbill.infrastructure.sqlite.goalrunner.outcome.WorkflowGoalRunnerOutcomeStoreDependencies
import skillbill.model.RepositoryRoot
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.decomposition.DecompositionManifestProjectionWriter
import skillbill.ports.goalrunner.persistence.GoalChildPlanningHydratorPort
import skillbill.ports.goalrunner.runner.GoalRunnerManifestStore
import skillbill.ports.repository.RepositoryEnclosingRootPort
import skillbill.ports.taskruntime.FeatureTaskRuntimePhaseOutputValidator
import skillbill.ports.taskruntime.FeatureTaskRuntimeWireArtifactValidator
import skillbill.ports.taskruntime.FeatureTaskRuntimeWorkerSupervisor
import skillbill.ports.workflow.WorkflowSnapshotValidator
import skillbill.ports.workflow.decomposition.DecompositionManifestStore
import skillbill.ports.workflow.decomposition.DecompositionManifestValidator
import skillbill.ports.workflow.decomposition.UnavailableDecompositionManifestStore
import skillbill.ports.workflow.gitops.WorkflowGitOperations
import java.time.Clock

data class OutcomeStoreTestArtifactPorts(
  val goalObservabilityEventValidator: FeatureTaskRuntimeWireArtifactValidator =
    AcceptingFeatureTaskRuntimeWireArtifactValidator,
  val goalProgressEventValidator: FeatureTaskRuntimeWireArtifactValidator =
    AcceptingFeatureTaskRuntimeWireArtifactValidator,
  val phaseOutputValidator: FeatureTaskRuntimePhaseOutputValidator = realFeatureTaskRuntimePhaseOutputValidator,
  val decompositionManifestStore: DecompositionManifestStore = UnavailableDecompositionManifestStore,
)

fun sqliteWorkflowGoalRunnerManifestStore(
  database: DatabaseSessionFactory,
  workflowSnapshotValidator: WorkflowSnapshotValidator,
  decompositionManifestValidator: DecompositionManifestValidator,
  decompositionManifestStore: DecompositionManifestStore,
  clock: Clock,
  decompositionManifestWriter: DecompositionManifestProjectionWriter,
  repositoryRoot: RepositoryRoot,
  planningHydrator: GoalChildPlanningHydratorPort,
  repositoryEnclosingRootPort: RepositoryEnclosingRootPort = TestRepositoryEnclosingRoot,
): GoalRunnerManifestStore =
  WorkflowGoalRunnerManifestStore(
    database = database,
    workflowSnapshotValidator = workflowSnapshotValidator,
    decompositionManifestValidator = decompositionManifestValidator,
    decompositionManifestStore = decompositionManifestStore,
    clock = clock,
    decompositionManifestWriter = decompositionManifestWriter,
    repositoryRoot = repositoryRoot,
    planningHydrator = planningHydrator,
    repositoryEnclosingRootPort = repositoryEnclosingRootPort,
  )

fun sqliteWorkflowGoalRunnerOutcomeStore(
  database: DatabaseSessionFactory,
  workflowSnapshotValidator: WorkflowSnapshotValidator,
  gitOperations: WorkflowGitOperations,
  workerSupervisor: FeatureTaskRuntimeWorkerSupervisor,
  clock: Clock,
  artifactPorts: OutcomeStoreTestArtifactPorts,
  decompositionManifestValidator: DecompositionManifestValidator,
  decompositionManifestWriter: DecompositionManifestProjectionWriter,
  childRepairExecutor: GoalRunnerChildRepairOperations,
): WorkflowGoalRunnerOutcomeStore =
  WorkflowGoalRunnerOutcomeStore(
    database = database,
    dependencies =
      WorkflowGoalRunnerOutcomeStoreDependencies(
        workflowSnapshotValidator = workflowSnapshotValidator,
        goalObservabilityEventValidator = artifactPorts.goalObservabilityEventValidator,
        goalProgressEventValidator = artifactPorts.goalProgressEventValidator,
        gitOperations = gitOperations,
        phaseOutputValidator = artifactPorts.phaseOutputValidator,
        workerSupervisor = workerSupervisor,
        clock = clock,
        decompositionManifestValidator = decompositionManifestValidator,
        decompositionManifestStore = artifactPorts.decompositionManifestStore,
        decompositionManifestWriter = decompositionManifestWriter,
        childRepairExecutor = childRepairExecutor,
      ),
  )
