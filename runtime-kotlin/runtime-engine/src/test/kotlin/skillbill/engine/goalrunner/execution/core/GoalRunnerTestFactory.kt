package skillbill.engine.goalrunner.execution.core
import skillbill.engine.goalrunner.manifest.TestNoopGoalPlanningManifestStore


import skillbill.application.TestRepositoryEnclosingRoot
import skillbill.application.idestatus.AgentActivityStampWriter
import skillbill.application.idestatus.WorktreeEditJournalWriter
import skillbill.application.realPlanningProjectionValidator
import skillbill.application.telemetry.GoalLifecycleTelemetryEmitter
import skillbill.engine.featuretask.phase.record.FeatureTaskRuntimePhaseRecorder
import skillbill.engine.goalplanning.GoalPlanningPreparationCheckpoint
import skillbill.engine.goalrunner.findings.UnaddressedFindingsLedgerService
import skillbill.engine.goalrunner.planning.sweep.DefaultGoalPlanningSweep
import skillbill.engine.goalrunner.planning.attempt.GoalPlanningAttemptRecorder
import skillbill.engine.goalrunner.planning.recovery.GoalPlanningRefreshLiveness
import skillbill.engine.goalrunner.planning.remedies.GoalPlanningRejectionRecorder
import skillbill.engine.goalrunner.planning.sweep.GoalPlanningSweep
import skillbill.engine.goalrunner.planning.sweep.GoalPlanningSweepCheckpointBoundaries
import skillbill.engine.goalrunner.planning.sweep.GoalPlanningSweepLaunchBoundaries
import skillbill.engine.goalrunner.planning.model.GoalPlanningBurstSchedule
import skillbill.ports.concurrency.BoundedWorkFanOutPort
import skillbill.ports.concurrency.SequentialBoundedWorkFanOutPort
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.diagnostics.NoopRuntimeDiagnostics
import skillbill.ports.goalrunner.EmptyGoalPlanningPreparationRepository
import skillbill.ports.goalrunner.EmptyGoalRunnerControlRepository
import skillbill.ports.goalrunner.planning.GoalPlanningContextDiscovery
import skillbill.ports.goalrunner.planning.model.GoalPlanningContext
import skillbill.ports.goalrunner.runner.GoalPullRequestPort
import skillbill.ports.goalrunner.runner.GoalRunnerManifestStore
import skillbill.ports.goalrunner.runner.GoalRunnerSubtaskLauncher
import skillbill.ports.goalrunner.runner.GoalRunnerWorkflowOutcomeStore
import skillbill.ports.learning.LearningRepository
import skillbill.ports.persistence.UnitOfWork
import skillbill.ports.persistence.UnitOfWorkDefaults
import skillbill.ports.review.ReviewRepository
import skillbill.ports.taskruntime.FeatureTaskRuntimeRunInvariantsSource
import skillbill.ports.telemetry.LifecycleTelemetryRepository
import skillbill.ports.telemetry.TelemetryOutboxRepository
import skillbill.ports.telemetry.TelemetryReconciliationRepository
import skillbill.ports.time.NoopRuntimeTimingPort
import skillbill.ports.time.RuntimeTimingPort
import skillbill.ports.work.EmptyWorkListRepository
import skillbill.ports.workflow.WorkflowStateRepository
import skillbill.ports.workflow.decomposition.DecompositionManifestStore
import skillbill.ports.workflow.gitops.NoopWorkflowGitOperations
import skillbill.ports.workflow.gitops.WorkflowGitOperations
import skillbill.ports.workflow.specscratch.SpecScratchStore
import skillbill.ports.workflow.specscratch.UnavailableSpecScratchStore
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseOutputValidator
import skillbill.workflow.taskruntime.FeatureTaskRuntimeWireArtifactValidator
import java.nio.file.Path
import java.time.Clock
import skillbill.engine.goalrunner.GoalRunner
import skillbill.engine.goalrunner.launch.GoalRunnerLaunchReconciler
import skillbill.engine.goalrunner.launch.GoalRunnerSubtaskLaunchPrepare

internal fun testActivityStampWriter(
  database: DatabaseSessionFactory = TestGoalActivityStampDatabase,
): AgentActivityStampWriter = AgentActivityStampWriter(database, Clock.systemUTC(), NoopRuntimeDiagnostics)

internal fun testWorktreeEditJournalWriter(
  database: DatabaseSessionFactory = TestGoalActivityStampDatabase,
): WorktreeEditJournalWriter = WorktreeEditJournalWriter(
  database,
  Clock.systemUTC(),
  NoopRuntimeDiagnostics,
  NoopWorkflowGitOperations,
)

internal data class GoalRunnerTestWiring(
  val runBoundaries: GoalRunnerRunBoundaries,
  val launchBoundaries: GoalRunnerSubtaskLaunchBoundaries,
  val finalizationBoundaries: GoalRunnerFinalizationBoundaries,
)

internal data class GoalRunnerTestWiringParams(
  val manifestStore: GoalRunnerManifestStore,
  val subtaskLauncher: GoalRunnerSubtaskLauncher,
  val outcomeStore: GoalRunnerWorkflowOutcomeStore,
  val pullRequestPort: GoalPullRequestPort,
  val phaseRecorder: FeatureTaskRuntimePhaseRecorder = goalRunnerDefaultPhaseRecorder(),
  val unaddressedFindingsLedgerService: UnaddressedFindingsLedgerService? = null,
)

internal fun testGoalRunnerWiring(params: GoalRunnerTestWiringParams): GoalRunnerTestWiring {
  val clock = Clock.systemUTC()
  val diagnostics = NoopRuntimeDiagnostics
  val runBoundaries = GoalRunnerRunBoundaries(
    manifestStore = params.manifestStore,
    outcomeStore = params.outcomeStore,
    goalPlanningSweep = GoalPlanningSweep.NONE,
    telemetry = GoalLifecycleTelemetryEmitter.NONE,
    clock = clock,
    diagnostics = diagnostics,
    executionCoordinator = GoalRunnerExecutionCoordinator.NONE,
    phaseRecorder = params.phaseRecorder,
    unaddressedFindingsLedgerService = params.unaddressedFindingsLedgerService,
  )
  val launchBoundaries = GoalRunnerSubtaskLaunchBoundaries(
    manifestStore = params.manifestStore,
    outcomeStore = params.outcomeStore,
    subtaskLauncher = params.subtaskLauncher,
    gitOperations = NoopWorkflowGitOperations,
  )
  val finalizationBoundaries = GoalRunnerFinalizationBoundaries(
    manifestStore = params.manifestStore,
    outcomeStore = params.outcomeStore,
    pullRequestPort = params.pullRequestPort,
    specScratchStore = UnavailableSpecScratchStore,
    gitOperations = NoopWorkflowGitOperations,
    diagnostics = diagnostics,
    unaddressedFindingsLedgerService = params.unaddressedFindingsLedgerService,
  )
  return GoalRunnerTestWiring(runBoundaries, launchBoundaries, finalizationBoundaries)
}

internal data class GoalRunnerTestInputs(
  val manifestStore: GoalRunnerManifestStore,
  val subtaskLauncher: GoalRunnerSubtaskLauncher,
  val outcomeStore: GoalRunnerWorkflowOutcomeStore,
  val pullRequestPort: GoalPullRequestPort,
  val goalPlanningSweep: GoalPlanningSweep = GoalPlanningSweep.NONE,
  val specScratchStore: SpecScratchStore = UnavailableSpecScratchStore,
  val gitOperations: WorkflowGitOperations = NoopWorkflowGitOperations,
  val telemetry: GoalLifecycleTelemetryEmitter = GoalLifecycleTelemetryEmitter.NONE,
  val clock: Clock = Clock.systemUTC(),
  val unaddressedFindingsLedgerService: UnaddressedFindingsLedgerService? = null,
  val executionCoordinator: GoalRunnerExecutionCoordinator = GoalRunnerExecutionCoordinator.NONE,
  val phaseRecorder: FeatureTaskRuntimePhaseRecorder = goalRunnerDefaultPhaseRecorder(),
) {
  fun toWiring(): GoalRunnerTestWiring = GoalRunnerTestWiring(
    runBoundaries = GoalRunnerRunBoundaries(
      manifestStore = manifestStore,
      outcomeStore = outcomeStore,
      goalPlanningSweep = goalPlanningSweep,
      telemetry = telemetry,
      clock = clock,
      diagnostics = NoopRuntimeDiagnostics,
      executionCoordinator = executionCoordinator,
      phaseRecorder = phaseRecorder,
      unaddressedFindingsLedgerService = unaddressedFindingsLedgerService,
    ),
    launchBoundaries = GoalRunnerSubtaskLaunchBoundaries(
      manifestStore = manifestStore,
      outcomeStore = outcomeStore,
      subtaskLauncher = subtaskLauncher,
      gitOperations = gitOperations,
    ),
    finalizationBoundaries = GoalRunnerFinalizationBoundaries(
      manifestStore = manifestStore,
      outcomeStore = outcomeStore,
      pullRequestPort = pullRequestPort,
      specScratchStore = specScratchStore,
      gitOperations = gitOperations,
      diagnostics = NoopRuntimeDiagnostics,
      unaddressedFindingsLedgerService = unaddressedFindingsLedgerService,
    ),
  )
}

internal fun goalRunnerDeps(
  manifestStore: GoalRunnerManifestStore,
  subtaskLauncher: GoalRunnerSubtaskLauncher,
  outcomeStore: GoalRunnerWorkflowOutcomeStore,
  pullRequestPort: GoalPullRequestPort,
): GoalRunnerTestInputs = GoalRunnerTestInputs(
  manifestStore = manifestStore,
  subtaskLauncher = subtaskLauncher,
  outcomeStore = outcomeStore,
  pullRequestPort = pullRequestPort,
)

internal fun testGoalRunner(deps: GoalRunnerTestInputs): GoalRunner = testGoalRunner(deps.toWiring())

internal fun testGoalRunner(wiring: GoalRunnerTestWiring): GoalRunner {
  val progressReader = GoalRunnerProgressReader(wiring.runBoundaries.outcomeStore)
  val finalization = GoalRunnerFinalization(wiring.finalizationBoundaries, progressReader)
  val workerRequestHandler = GoalRunnerWorkerRequestHandler(
    wiring.runBoundaries.manifestStore,
    wiring.runBoundaries.outcomeStore,
  )
  val reconciler = GoalRunnerLaunchReconciler(
    wiring.runBoundaries.manifestStore,
    wiring.runBoundaries.outcomeStore,
    progressReader,
    testActivityStampWriter(),
    testWorktreeEditJournalWriter(),
    wiring.runBoundaries.clock,
    wiring.runBoundaries.diagnostics,
  )
  val pauseBoundary = GoalRunnerPauseBoundary(wiring.runBoundaries.manifestStore)
  val launchPrepare = GoalRunnerSubtaskLaunchPrepare(
    wiring.launchBoundaries,
    TestRepositoryEnclosingRoot,
    wiring.runBoundaries.clock,
  )
  val perRunLoopAssembler = GoalRunnerPerRunLoopAssembler(
    runBoundaries = wiring.runBoundaries,
    launchBoundaries = wiring.launchBoundaries,
    workerRequestHandler = workerRequestHandler,
    reconciler = reconciler,
    progressReader = progressReader,
    pauseBoundary = pauseBoundary,
    launchPrepare = launchPrepare,
    finalization = finalization,
  )
  return GoalRunner(
    runBoundaries = wiring.runBoundaries,
    runPreparation = GoalRunnerRunPreparation(wiring.runBoundaries.manifestStore, TestRepositoryEnclosingRoot),
    perRunLoopAssembler = perRunLoopAssembler,
    pauseBoundary = pauseBoundary,
  )
}

internal fun testGoalRunner(
  manifestStore: GoalRunnerManifestStore,
  subtaskLauncher: GoalRunnerSubtaskLauncher,
  outcomeStore: GoalRunnerWorkflowOutcomeStore,
  pullRequestPort: GoalPullRequestPort,
  phaseRecorder: FeatureTaskRuntimePhaseRecorder = goalRunnerDefaultPhaseRecorder(),
): GoalRunner = testGoalRunner(
  testGoalRunnerWiring(
    GoalRunnerTestWiringParams(
      manifestStore = manifestStore,
      subtaskLauncher = subtaskLauncher,
      outcomeStore = outcomeStore,
      pullRequestPort = pullRequestPort,
      phaseRecorder = phaseRecorder,
    ),
  ),
)

private object TestGoalActivityStampDatabase : DatabaseSessionFactory {
  private val dbPath = Path.of("/fake/goal-activity-stamp.db")

  override fun resolveDbPath(): Path = dbPath

  override fun databaseExists(): Boolean = true

  override fun <T> read(block: (UnitOfWork) -> T): T = block(unitOfWork())

  override fun <T> selfManagedWrite(block: (UnitOfWork) -> T): T = transaction(block)

  override fun <T> transaction(block: (UnitOfWork) -> T): T = block(unitOfWork())

  private fun unitOfWork(): UnitOfWork = object : UnitOfWorkDefaults() {
    override val dbPath: Path = this@TestGoalActivityStampDatabase.dbPath
    override val reviews: ReviewRepository get() = error("unused by goal activity stamp wiring")
    override val learnings: LearningRepository get() = error("unused by goal activity stamp wiring")
    override val lifecycleTelemetry: LifecycleTelemetryRepository
      get() = error("unused by goal activity stamp wiring")
    override val telemetryReconciliation: TelemetryReconciliationRepository
      get() = error("unused by goal activity stamp wiring")
    override val telemetryOutbox: TelemetryOutboxRepository get() = error("unused by goal activity stamp wiring")
    override val workflowStates: WorkflowStateRepository get() = error("unused by goal activity stamp wiring")
    override val workList = EmptyWorkListRepository
    override val goalPlanningPreparations = EmptyGoalPlanningPreparationRepository
    override val goalRunnerControls = EmptyGoalRunnerControlRepository
  }
}

internal fun testDefaultGoalPlanningSweep(
  checkpointBoundaries: GoalPlanningSweepCheckpointBoundaries,
  launchBoundaries: GoalPlanningSweepLaunchBoundaries,
): DefaultGoalPlanningSweep =
  DefaultGoalPlanningSweep(checkpointBoundaries, launchBoundaries, TestRepositoryEnclosingRoot)

internal data class GoalPlanningSweepPortsParams(
  val checkpoint: GoalPlanningPreparationCheckpoint,
  val outputValidator: FeatureTaskRuntimePhaseOutputValidator,
  val subtaskLauncher: GoalRunnerSubtaskLauncher,
  val invariantsSource: FeatureTaskRuntimeRunInvariantsSource,
  val manifestFileStore: DecompositionManifestStore,
  val contextDiscovery: GoalPlanningContextDiscovery,
  val planningProjectionValidator: FeatureTaskRuntimeWireArtifactValidator = realPlanningProjectionValidator,
  val planningAttemptRecorder: GoalPlanningAttemptRecorder = GoalPlanningAttemptRecorder.NONE,
  val manifestStore: GoalRunnerManifestStore = TestNoopGoalPlanningManifestStore,
  val planningRejectionRecorder: GoalPlanningRejectionRecorder = GoalPlanningRejectionRecorder.NONE,
  val timingPort: RuntimeTimingPort = NoopRuntimeTimingPort,
  val fanOutPort: BoundedWorkFanOutPort = SequentialBoundedWorkFanOutPort,
  val burstSchedule: GoalPlanningBurstSchedule = GoalPlanningBurstSchedule(
    planFanOutCap = GoalPlanningBurstSchedule.DEFAULT_PLAN_FAN_OUT_CAP,
    emptyTurnBackoffBase = GoalPlanningBurstSchedule.DEFAULT_EMPTY_TURN_BACKOFF_BASE,
    emptyTurnBackoffFactor = GoalPlanningBurstSchedule.DEFAULT_EMPTY_TURN_BACKOFF_FACTOR,
    waitSlice = GoalPlanningBurstSchedule.DEFAULT_WAIT_SLICE,
  ),
  val refreshLiveness: GoalPlanningRefreshLiveness = GoalPlanningRefreshLiveness.IDLE,
)

internal fun testGoalPlanningSweepPorts(params: GoalPlanningSweepPortsParams): DefaultGoalPlanningSweep =
  testDefaultGoalPlanningSweep(
    GoalPlanningSweepCheckpointBoundaries(
      checkpoint = params.checkpoint,
      outputValidator = params.outputValidator,
      invariantsSource = params.invariantsSource,
      manifestFileStore = params.manifestFileStore,
      contextDiscovery = params.contextDiscovery,
      planningProjectionValidator = params.planningProjectionValidator,
    ),
    GoalPlanningSweepLaunchBoundaries(
      subtaskLauncher = params.subtaskLauncher,
      manifestStore = params.manifestStore,
      planningAttemptRecorder = params.planningAttemptRecorder,
      planningRejectionRecorder = params.planningRejectionRecorder,
      timingPort = params.timingPort,
      fanOutPort = params.fanOutPort,
      burstSchedule = params.burstSchedule,
      refreshLiveness = params.refreshLiveness,
    ),
  )

internal fun testGoalPlanningContextDiscovery(
  context: GoalPlanningContext = GoalPlanningContext(
    boundaryCatalog = emptyList(),
    boundaryCatalogTruncated = false,
    validationGuidance = "",
  ),
): GoalPlanningContextDiscovery = if (context == GoalPlanningContext(
    boundaryCatalog = emptyList(),
    boundaryCatalogTruncated = false,
    validationGuidance = "",
  )
) {
  GoalPlanningContextDiscovery.NONE
} else {
  object : GoalPlanningContextDiscovery {
    override fun loadPlanningContext(repoRoot: Path): GoalPlanningContext = context

    override fun discoverForFindingPaths(repoRoot: Path, findingPaths: List<String>, loudFailOnCapExceeded: Boolean) =
      GoalPlanningContextDiscovery.NONE.discoverForFindingPaths(
        repoRoot,
        findingPaths,
        loudFailOnCapExceeded,
      )
  }
}
