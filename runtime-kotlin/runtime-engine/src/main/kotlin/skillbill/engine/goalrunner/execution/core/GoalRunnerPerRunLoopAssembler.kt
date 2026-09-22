package skillbill.engine.goalrunner.execution.core

import me.tatarka.inject.annotations.Inject
import skillbill.engine.goalrunner.execution.support.GoalRunnerIterationPendingState
import skillbill.engine.goalrunner.launch.GoalRunnerLaunchReconciler
import skillbill.engine.goalrunner.launch.GoalRunnerSubtaskLaunchPrepare

@Inject
class GoalRunnerPerRunLoopAssembler(
  private val runBoundaries: GoalRunnerRunBoundaries,
  private val launchBoundaries: GoalRunnerSubtaskLaunchBoundaries,
  private val workerRequestHandler: GoalRunnerWorkerRequestHandler,
  private val reconciler: GoalRunnerLaunchReconciler,
  private val progressReader: GoalRunnerProgressReader,
  private val pauseBoundary: GoalRunnerPauseBoundary,
  private val launchPrepare: GoalRunnerSubtaskLaunchPrepare,
  private val finalization: GoalRunnerFinalization,
) {
  internal fun assemble(pendingState: GoalRunnerIterationPendingState): GoalRunnerGoalLoop {
    val iterationOutcome =
      GoalRunnerIterationOutcome(
        manifestStore = runBoundaries.manifestStore,
        outcomeStore = runBoundaries.outcomeStore,
        finalization = finalization,
        unaddressedFindingsLedgerService = runBoundaries.unaddressedFindingsLedgerService,
        progressReader = progressReader,
        clock = runBoundaries.clock,
        phaseRecorder = runBoundaries.phaseRecorder,
        pendingState = pendingState,
      )
    val selectedSubtaskLoop =
      GoalRunnerSelectedSubtaskLoop(
        manifestStore = runBoundaries.manifestStore,
        subtaskLauncher = launchBoundaries.subtaskLauncher,
        reconciler = reconciler,
        workerRequestHandler = workerRequestHandler,
        iterationOutcome = iterationOutcome,
        pauseBoundary = pauseBoundary,
        launchPrepare = launchPrepare,
        clock = runBoundaries.clock,
        pendingState = pendingState,
      )
    return GoalRunnerGoalLoop(
      runBoundaries.manifestStore,
      runBoundaries.goalPlanningSweep,
      finalization,
      selectedSubtaskLoop,
      pauseBoundary,
      progressReader,
    )
  }
}
