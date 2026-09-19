package skillbill.engine.goalrunner.planning

import me.tatarka.inject.annotations.Inject
import skillbill.engine.featuretask.phase.record.FeatureTaskRuntimePhaseRecorder
import skillbill.goalrunner.model.ExecutionLiveness
import skillbill.ports.goalrunner.runner.model.GoalRunnerManifestState
import skillbill.workflow.decomposition.model.DecompositionSubtask
import skillbill.workflow.model.FeatureTaskWorkflowMode
import java.time.Clock

fun interface GoalPlanningRefreshLiveness {
  fun resolve(state: GoalRunnerManifestState): ExecutionLiveness

  companion object {
    val IDLE: GoalPlanningRefreshLiveness = GoalPlanningRefreshLiveness { _ -> ExecutionLiveness.IDLE }
  }
}

@Inject
class ChildAwareGoalPlanningRefreshLiveness(
  private val phaseRecorder: FeatureTaskRuntimePhaseRecorder,
  private val clock: Clock,
) : GoalPlanningRefreshLiveness {
  override fun resolve(state: GoalRunnerManifestState): ExecutionLiveness {
    val currentSubtask = state.manifest.subtasks.firstOrNull { subtask ->
      subtask.id == state.manifest.currentSubtaskIntent.subtaskId
    }
    return resolveChildExecutionLiveness(currentSubtask, phaseRecorder, clock)
  }
}

fun resolveChildExecutionLiveness(
  currentSubtask: DecompositionSubtask?,
  phaseRecorder: FeatureTaskRuntimePhaseRecorder,
  clock: Clock,
): ExecutionLiveness {
  val workflowId = currentSubtask?.workflowId?.takeIf(String::isNotBlank) ?: return ExecutionLiveness.IDLE
  return runCatching {
    if (phaseRecorder.existingWorkflowMode(workflowId) != FeatureTaskWorkflowMode.RUNTIME) {
      ExecutionLiveness.UNKNOWN
    } else {
      val ownership = phaseRecorder.workerOwnership(workflowId)
      if (ownership != null && ownership.expiresAtInstant.isAfter(clock.instant())) {
        ExecutionLiveness.LIVE
      } else {
        ExecutionLiveness.IDLE
      }
    }
  }.getOrDefault(ExecutionLiveness.UNKNOWN)
}

fun refuseRefreshReason(issueKey: String, liveness: ExecutionLiveness): String? = when (liveness) {
  ExecutionLiveness.LIVE ->
    "Goal '$issueKey' is live; refuse shared-preplan refresh while the current child run is active."
  ExecutionLiveness.UNKNOWN ->
    "Goal '$issueKey' has unknown execution liveness; refuse shared-preplan refresh."
  ExecutionLiveness.IDLE -> null
}
