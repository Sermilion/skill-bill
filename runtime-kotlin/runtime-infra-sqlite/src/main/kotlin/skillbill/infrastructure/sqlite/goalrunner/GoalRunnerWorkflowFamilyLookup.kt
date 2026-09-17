package skillbill.infrastructure.sqlite.goalrunner
import skillbill.agentaddon.model.AgentAddonSelection
import skillbill.agentaddon.model.PersistedAgentAddonSelectionEntry
import skillbill.contracts.JsonCodec
import skillbill.contracts.workflow.FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys
import skillbill.error.InvalidAgentAddonSelectionError
import skillbill.error.LegacyProseWorkflowError
import skillbill.goalrunner.model.GOAL_PAUSE_REASON_OPERATOR_REQUEST
import skillbill.goalrunner.model.GOAL_PAUSE_REASON_STOP_AFTER_SUBTASK
import skillbill.goalrunner.model.GoalRunnerControlState
import skillbill.ports.workflow.WorkflowStateRepository
import skillbill.ports.workflow.model.FeatureTaskWorkflowMode
import skillbill.ports.workflow.model.WorkflowFamily

fun workflowFamilyFor(workflowStates: WorkflowStateRepository, workflowId: String): WorkflowFamily? {
  val featureTaskRow = workflowStates.getFeatureTaskWorkflow(workflowId)
  if (featureTaskRow != null) {
    return when (featureTaskRow.mode) {
      FeatureTaskWorkflowMode.RUNTIME -> WorkflowFamily.TASK_RUNTIME
      FeatureTaskWorkflowMode.PROSE, null -> throw LegacyProseWorkflowError(workflowId, featureTaskRow.issueKey)
    }
  }
  return if (workflowStates.getFeatureVerifyWorkflow(workflowId) != null) {
    WorkflowFamily.VERIFY
  } else {
    null
  }
}

fun GoalRunnerControlState.pauseAtOperatorBoundary(
  pausedAtNow: String,
  targetReached: Boolean = false,
): GoalRunnerControlState = when {
  paused -> copy(stopAfterConsumed = stopAfterConsumed || targetReached)
  pauseRequested -> copy(
    pauseConsumed = true,
    paused = true,
    pauseReason = pauseReason ?: GOAL_PAUSE_REASON_OPERATOR_REQUEST,
    pausedAt = pausedAtNow,
    stopAfterConsumed = stopAfterConsumed || targetReached,
  )
  targetReached -> copy(
    paused = true,
    pauseReason = GOAL_PAUSE_REASON_STOP_AFTER_SUBTASK,
    pausedAt = pausedAtNow,
    stopAfterConsumed = true,
  )
  else -> this
}

fun decodeGoalAgentAddonSelection(raw: Any?): AgentAddonSelection {
  val values = raw ?: return AgentAddonSelection()
  val entries = values as? List<*>
    ?: throw InvalidAgentAddonSelectionError("Goal review policy agent_addon_selection must be a list.")
  return AgentAddonSelection(
    entries.mapIndexed { index, value ->
      val entry = JsonCodec.anyToStringAnyMap(value)
        ?: throw InvalidAgentAddonSelectionError(
          "Goal review policy agent_addon_selection entry $index must be a map.",
        )
      if (entry.keys != setOf(
          FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.ADDON_SLUG,
          FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.ADDON_SOURCE_IDENTITY,
          FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.ADDON_CONTENT_SHA256,
        )
      ) {
        throw InvalidAgentAddonSelectionError(
          "Goal review policy agent_addon_selection entry $index has invalid fields.",
        )
      }
      PersistedAgentAddonSelectionEntry(
        entry[FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.ADDON_SLUG] as? String
          ?: throw InvalidAgentAddonSelectionError(
            "Goal review policy add-on entry $index is missing slug.",
          ),
        entry[FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.ADDON_SOURCE_IDENTITY] as? String
          ?: throw InvalidAgentAddonSelectionError(
            "Goal review policy add-on entry $index is missing source_identity.",
          ),
        entry[FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.ADDON_CONTENT_SHA256] as? String
          ?: throw InvalidAgentAddonSelectionError(
            "Goal review policy add-on entry $index is missing content_sha256.",
          ),
      )
    },
  )
}
