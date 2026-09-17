package skillbill.engine.goalrunner.planning

import me.tatarka.inject.annotations.Inject
import skillbill.engine.goalplanning.GoalPlanningPreparationCheckpoint
import skillbill.engine.goalrunner.planning.model.GoalPlanningBurstSchedule
import skillbill.ports.concurrency.BoundedWorkFanOutPort
import skillbill.ports.goalrunner.planning.GoalPlanningContextDiscovery
import skillbill.ports.goalrunner.runner.GoalRunnerManifestStore
import skillbill.ports.goalrunner.runner.GoalRunnerSubtaskLauncher
import skillbill.ports.taskruntime.FeatureTaskRuntimeRunInvariantsSource
import skillbill.ports.time.RuntimeTimingPort
import skillbill.ports.workflow.decomposition.DecompositionManifestStore
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseOutputValidator
import skillbill.workflow.taskruntime.FeatureTaskRuntimeWireArtifactValidator
import skillbill.engine.goalrunner.planning.model.GoalPlanningBurstSchedule

@Inject
data class GoalPlanningSweepCheckpointBoundaries(
  val checkpoint: GoalPlanningPreparationCheckpoint,
  val outputValidator: FeatureTaskRuntimePhaseOutputValidator,
  val invariantsSource: FeatureTaskRuntimeRunInvariantsSource,
  val manifestFileStore: DecompositionManifestStore,
  val contextDiscovery: GoalPlanningContextDiscovery,
  val planningProjectionValidator: FeatureTaskRuntimeWireArtifactValidator,
)

@Inject
data class GoalPlanningSweepLaunchBoundaries(
  val subtaskLauncher: GoalRunnerSubtaskLauncher,
  val manifestStore: GoalRunnerManifestStore,
  val planningAttemptRecorder: GoalPlanningAttemptRecorder,
  val planningRejectionRecorder: GoalPlanningRejectionRecorder,
  val timingPort: RuntimeTimingPort,
  val fanOutPort: BoundedWorkFanOutPort,
  val burstSchedule: GoalPlanningBurstSchedule,
  val refreshLiveness: GoalPlanningRefreshLiveness,
)
