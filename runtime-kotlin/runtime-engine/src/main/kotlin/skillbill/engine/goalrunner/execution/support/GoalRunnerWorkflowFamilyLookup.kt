package skillbill.engine.goalrunner.execution.support

import skillbill.agentaddon.model.AgentAddonSelection
import skillbill.agentaddon.model.PersistedAgentAddonSelectionEntry
import skillbill.contracts.JsonCodec
import skillbill.contracts.workflow.identity.task.FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys
import skillbill.error.shellcontent.InvalidAgentAddonSelectionError
import skillbill.error.shellcontent.LegacyProseWorkflowError
import skillbill.goalrunner.model.GOAL_PAUSE_REASON_OPERATOR_REQUEST
import skillbill.goalrunner.model.GOAL_PAUSE_REASON_STOP_AFTER_SUBTASK
import skillbill.goalrunner.model.GoalRunnerControlState
import skillbill.ports.workflow.WorkflowStateRepository
import skillbill.ports.workflow.model.WorkflowFamily
import skillbill.workflow.model.FeatureTaskWorkflowMode

fun workflowFamilyFor(
  workflowStates: WorkflowStateRepository,
  workflowId: String,
): WorkflowFamily? {
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
): GoalRunnerControlState =
  when {
    paused -> copy(stopAfterConsumed = stopAfterConsumed || targetReached)
    pauseRequested ->
      copy(
        pauseConsumed = true,
        paused = true,
        pauseReason = pauseReason ?: GOAL_PAUSE_REASON_OPERATOR_REQUEST,
        pausedAt = pausedAtNow,
        stopAfterConsumed = stopAfterConsumed || targetReached,
      )
    targetReached ->
      copy(
        paused = true,
        pauseReason = GOAL_PAUSE_REASON_STOP_AFTER_SUBTASK,
        pausedAt = pausedAtNow,
        stopAfterConsumed = true,
      )
    else -> this
  }

fun decodeGoalAgentAddonSelection(raw: Any?): AgentAddonSelection {
  val values = raw ?: return AgentAddonSelection()
  val entries =
    values as? List<*>
      ?: throw InvalidAgentAddonSelectionError("Goal review policy agent_addon_selection must be a list.")
  return AgentAddonSelection(
    entries.mapIndexed(::decodeGoalAgentAddonSelectionEntry),
  )
}

private fun decodeGoalAgentAddonSelectionEntry(
  index: Int,
  value: Any?,
): PersistedAgentAddonSelectionEntry {
  val entry =
    JsonCodec.anyToStringAnyMap(value)
      ?: throw InvalidAgentAddonSelectionError(
        "Goal review policy agent_addon_selection entry $index must be a map.",
      )
  val expectedKeys =
    setOf(
      FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.ADDON_SLUG,
      FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.ADDON_SOURCE_IDENTITY,
      FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.ADDON_CONTENT_SHA256,
    )
  if (entry.keys != expectedKeys) {
    throw InvalidAgentAddonSelectionError(
      "Goal review policy agent_addon_selection entry $index has invalid fields.",
    )
  }
  return PersistedAgentAddonSelectionEntry(
    requiredAddonField(entry, index, FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.ADDON_SLUG, "slug"),
    requiredAddonField(
      entry,
      index,
      FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.ADDON_SOURCE_IDENTITY,
      "source_identity",
    ),
    requiredAddonField(
      entry,
      index,
      FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys.ADDON_CONTENT_SHA256,
      "content_sha256",
    ),
  )
}

private fun requiredAddonField(
  entry: Map<String, Any?>,
  index: Int,
  key: String,
  label: String,
): String =
  entry[key] as? String
    ?: throw InvalidAgentAddonSelectionError("Goal review policy add-on entry $index is missing $label.")
