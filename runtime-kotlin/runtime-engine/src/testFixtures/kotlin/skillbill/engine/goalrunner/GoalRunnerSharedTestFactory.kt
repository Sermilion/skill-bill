package skillbill.engine.goalrunner

import skillbill.application.FakeDatabaseSessionFactory
import skillbill.application.InMemoryWorkflowStates
import skillbill.application.TestRepositoryEnclosingRoot
import skillbill.application.decomposition.DecompositionManifestWriter
import skillbill.application.realFeatureTaskRuntimePhaseOutputValidator
import skillbill.application.realPlanningProjectionValidator
import skillbill.application.testDecompositionManifestValidator
import skillbill.application.testDecompositionManifestWriter
import skillbill.application.testHarnessClock
import skillbill.application.testRepositoryRoot
import skillbill.application.testWorkflowSnapshotValidator
import skillbill.config.model.RepoLocalConfig
import skillbill.engine.featuretask.lifecycle.core.AcceptingFeatureTaskRuntimeWireArtifactValidator
import skillbill.engine.featuretask.phase.record.FeatureTaskRuntimePhaseRecorder
import skillbill.engine.featuretask.runner.FeatureTaskRuntimeStatusService
import skillbill.engine.featuretask.phase.record.featureTaskRuntimePhaseRecorder
import skillbill.engine.featuretask.validation.ValidationGateResolver
import skillbill.engine.goalrunner.planning.GoalChildPlanningHydratorPortAdapter
import skillbill.engine.goalrunner.planning.GoalPlanningStatusReasonCoherence
import skillbill.ports.config.RepoLocalConfigPort
import skillbill.ports.config.model.ReadRepoLocalConfigRequest
import skillbill.ports.config.model.ReadRepoLocalConfigResult
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.diagnostics.NoopRuntimeDiagnostics
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.ports.goalrunner.persistence.GoalRunnerChildRepairStore
import skillbill.ports.goalrunner.runner.GoalRunnerAttemptLedgerStore
import skillbill.ports.goalrunner.runner.GoalRunnerManifestStore
import skillbill.ports.goalrunner.runner.GoalRunnerWorkflowOutcomeStore
import skillbill.ports.goalrunner.runner.NoopGoalRunnerAttemptLedgerStore
import skillbill.ports.scaffold.install.InstalledPlatformPackCatalogPort
import skillbill.ports.taskruntime.FeatureTaskRuntimeWorkerSupervisor
import skillbill.ports.taskruntime.NoopFeatureTaskRuntimeWorkerSupervisor
import skillbill.ports.workflow.decomposition.DecompositionManifestStore
import skillbill.ports.workflow.decomposition.UnavailableDecompositionManifestStore
import skillbill.ports.workflow.gitops.NoopWorkflowGitOperations
import skillbill.ports.workflow.gitops.WorkflowGitOperations
import skillbill.scaffold.model.PlatformManifest
import skillbill.workflow.decomposition.DecompositionManifestValidator
import skillbill.workflow.engine.WorkflowSnapshotValidator
import skillbill.workflow.taskruntime.FeatureTaskRuntimeWireArtifactValidator
import java.time.Clock

fun goalRunnerDefaultPhaseRecorder(): FeatureTaskRuntimePhaseRecorder = testPhaseRecorder(
  FakeDatabaseSessionFactory(InMemoryWorkflowStates()),
  testWorkflowSnapshotValidator,
)

data class GoalRunnerStatusTestPorts(
  val gitOperations: WorkflowGitOperations = NoopWorkflowGitOperations,
  val workerSupervisor: FeatureTaskRuntimeWorkerSupervisor = NoopFeatureTaskRuntimeWorkerSupervisor,
  val childRepairStore: GoalRunnerChildRepairStore = NoopGoalRunnerChildRepairStore,
  val attemptLedgerStore: GoalRunnerAttemptLedgerStore = NoopGoalRunnerAttemptLedgerStore,
  val diagnostics: RuntimeDiagnostics = NoopRuntimeDiagnostics,
  val runtimeStatusService: FeatureTaskRuntimeStatusService? = null,
  val validationGatePlatformManifests: List<PlatformManifest> = emptyList(),
  val repoLocalConfig: RepoLocalConfigPort = object : RepoLocalConfigPort {
    override fun readRepoLocalConfig(request: ReadRepoLocalConfigRequest) =
      ReadRepoLocalConfigResult(RepoLocalConfig.defaults())
  },
)

fun testGoalRunnerStatusService(
  manifestStore: GoalRunnerManifestStore,
  outcomeStore: GoalRunnerWorkflowOutcomeStore,
  phaseRecorder: FeatureTaskRuntimePhaseRecorder = goalRunnerDefaultPhaseRecorder(),
  clock: Clock = testHarnessClock,
  ports: GoalRunnerStatusTestPorts = GoalRunnerStatusTestPorts(),
  database: DatabaseSessionFactory = FakeDatabaseSessionFactory(InMemoryWorkflowStates()),
  decompositionManifestStore: DecompositionManifestStore = UnavailableDecompositionManifestStore,
): GoalRunnerStatusService {
  val projectionAssembler = GoalRunnerStatusProjectionAssembler(
    dataSources = GoalRunnerStatusProjectionDataSources(
      manifestStore = manifestStore,
      outcomeStore = outcomeStore,
      phaseRecorder = phaseRecorder,
      attemptLedgerStore = ports.attemptLedgerStore,
      database = database,
    ),
    gitOperations = ports.gitOperations,
    clock = clock,
    workerSupervisor = ports.workerSupervisor,
    planningStatusReasonCoherence = GoalPlanningStatusReasonCoherence.NONE,
    diagnostics = ports.diagnostics,
    runtimeStatusService = ports.runtimeStatusService,
    repositoryRoot = testRepositoryRoot,
    validationDependencies = GoalRunnerStatusProjectionValidationDependencies(
      validationGateResolver = ValidationGateResolver(
        InstalledPlatformPackCatalogPort { ports.validationGatePlatformManifests },
      ),
      repoLocalConfig = ports.repoLocalConfig,
    ),
  )
  return GoalRunnerStatusService(
    manifestStore = manifestStore,
    outcomeStore = outcomeStore,
    phaseRecorder = phaseRecorder,
    gitOperations = ports.gitOperations,
    clock = clock,
    workerSupervisor = ports.workerSupervisor,
    childRepairStore = ports.childRepairStore,
    repositoryEnclosingRootPort = TestRepositoryEnclosingRoot,
    projectionAssembler = projectionAssembler,
    resetReplanCoordinator = GoalRunnerResetReplanCoordinator(
      manifestStore = manifestStore,
      outcomeStore = outcomeStore,
      gitOperations = ports.gitOperations,
      diagnostics = ports.diagnostics,
      projectionAssembler = projectionAssembler,
      repositoryRoot = testRepositoryRoot,
      repositoryEnclosingRootPort = TestRepositoryEnclosingRoot,
    ),
    purgeCoordinator = GoalRunnerPurgeCoordinator(
      manifestStore = manifestStore,
      gitOperations = ports.gitOperations,
      projectionAssembler = projectionAssembler,
      manifestFileStore = decompositionManifestStore,
      manifestValidator = testDecompositionManifestValidator,
      database = database,
      repositoryEnclosingRootPort = TestRepositoryEnclosingRoot,
    ),
  )
}

private val testGoalChildPlanningHydratorPort = GoalChildPlanningHydratorPortAdapter(
  realFeatureTaskRuntimePhaseOutputValidator,
  realPlanningProjectionValidator,
  testHarnessClock,
)

fun testGoalRunnerChildRepairExecutor(
  database: DatabaseSessionFactory = FakeDatabaseSessionFactory(InMemoryWorkflowStates()),
  gitOperations: WorkflowGitOperations = NoopWorkflowGitOperations,
): GoalRunnerChildRepairOperations = GoalRunnerChildRepairOperations(
  database,
  testWorkflowSnapshotValidator,
  gitOperations,
  testDecompositionManifestValidator,
  testHarnessClock,
)

fun testWorkflowGoalRunnerManifestStore(
  database: DatabaseSessionFactory,
  decompositionManifestStore: DecompositionManifestStore,
  clock: Clock,
  decompositionManifestValidator: DecompositionManifestValidator = testDecompositionManifestValidator,
): GoalRunnerManifestStore = sqliteWorkflowGoalRunnerManifestStore(
  database = database,
  workflowSnapshotValidator = testWorkflowSnapshotValidator,
  decompositionManifestValidator = decompositionManifestValidator,
  decompositionManifestStore = decompositionManifestStore,
  clock = clock,
  decompositionManifestWriter = DecompositionManifestWriter(),
  repositoryRoot = testRepositoryRoot,
  planningHydrator = testGoalChildPlanningHydratorPort,
)

fun testWorkflowGoalRunnerOutcomeStore(
  database: DatabaseSessionFactory,
  workflowSnapshotValidator: WorkflowSnapshotValidator = testWorkflowSnapshotValidator,
  gitOperations: WorkflowGitOperations = NoopWorkflowGitOperations,
  workerSupervisor: FeatureTaskRuntimeWorkerSupervisor = NoopFeatureTaskRuntimeWorkerSupervisor,
  artifactPorts: OutcomeStoreTestArtifactPorts = OutcomeStoreTestArtifactPorts(),
) = sqliteWorkflowGoalRunnerOutcomeStore(
  database = database,
  workflowSnapshotValidator = workflowSnapshotValidator,
  gitOperations = gitOperations,
  workerSupervisor = workerSupervisor,
  clock = testHarnessClock,
  artifactPorts = artifactPorts,
  decompositionManifestValidator = testDecompositionManifestValidator,
  decompositionManifestWriter = testDecompositionManifestWriter,
  childRepairExecutor = testGoalRunnerChildRepairExecutor(database, gitOperations),
)

fun testPhaseRecorder(
  database: DatabaseSessionFactory,
  workflowSnapshotValidator: WorkflowSnapshotValidator,
  handoffEnvelopeValidator: FeatureTaskRuntimeWireArtifactValidator =
    AcceptingFeatureTaskRuntimeWireArtifactValidator,
  handoffFoundationValidator: FeatureTaskRuntimeWireArtifactValidator =
    AcceptingFeatureTaskRuntimeWireArtifactValidator,
  diagnostics: RuntimeDiagnostics = NoopRuntimeDiagnostics,
): FeatureTaskRuntimePhaseRecorder = featureTaskRuntimePhaseRecorder(
  database = database,
  workflowSnapshotValidator = workflowSnapshotValidator,
  handoffEnvelopeValidator = handoffEnvelopeValidator,
  handoffFoundationValidator = handoffFoundationValidator,
  clock = testHarnessClock,
  diagnostics = diagnostics,
)
