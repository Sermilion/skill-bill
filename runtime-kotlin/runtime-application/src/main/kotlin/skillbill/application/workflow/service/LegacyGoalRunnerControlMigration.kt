package skillbill.application.workflow.service
import skillbill.agentaddon.model.AgentAddonSelection
import skillbill.agentaddon.model.PersistedAgentAddonSelectionEntry
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.workflow.identity.task.FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys
import skillbill.error.shellcontent.InvalidAgentAddonSelectionError
import skillbill.ports.goalrunner.GoalRunnerPersistenceSession
import skillbill.ports.goalrunner.runner.model.GoalRunnerOutOfBandAcceptance
import skillbill.ports.goalrunner.runner.model.GoalRunnerReviewPolicy
import skillbill.review.context.model.launch.CodeReviewExecutionMode
import skillbill.workflow.engine.model.DurableWorkflowArtifactFamily
import skillbill.workflow.engine.model.DurableWorkflowArtifacts
import skillbill.workflow.engine.model.WorkflowStateSnapshot

internal fun migrateLegacyGoalRunnerControls(
  unitOfWork: GoalRunnerPersistenceSession,
  existing: WorkflowStateSnapshot,
) {
  val artifacts = existing.artifacts
  if (unitOfWork.goalRunnerControls.reviewPolicy(existing.workflowId) == null) {
    reviewPolicyFromLegacyArtifacts(artifacts)?.let {
      unitOfWork.goalRunnerControls.persistReviewPolicy(existing.workflowId, it)
    }
  }
  val durableAcceptances = unitOfWork.goalRunnerControls.outOfBandAcceptances(existing.workflowId)
  outOfBandAcceptancesFromLegacyArtifacts(artifacts)
    .filterKeys { it !in durableAcceptances }
    .values
    .forEach { acceptance ->
      unitOfWork.goalRunnerControls.persistOutOfBandAcceptance(existing.workflowId, acceptance)
    }
}

fun reviewPolicyFromLegacyArtifacts(artifacts: DurableWorkflowArtifacts): GoalRunnerReviewPolicy? {
  val artifactFamily = DurableWorkflowArtifactFamily.GOAL_REVIEW_POLICY
  val raw = artifactFamily.value(artifacts) ?: return null
  val policy =
    JsonCodec.anyToStringAnyMap(raw)
      ?: error("Goal review policy artifact '${artifactFamily.label()}' must be a map.")
  val allowedKeys = setOf("code_review_mode", "parallel_review_agent", "agent_addon_selection")
  policy.keys.forEach { key ->
    require(key in allowedKeys) {
      "Goal review policy artifact '${artifactFamily.label()}' has unsupported field '$key'."
    }
  }
  val mode =
    policy["code_review_mode"] as? String
      ?: error("Goal review policy artifact '${artifactFamily.label()}' is missing code_review_mode.")
  val codeReviewMode =
    try {
      CodeReviewExecutionMode.fromWire(mode)
    } catch (error: IllegalArgumentException) {
      throw IllegalStateException("Goal review policy artifact has invalid code_review_mode '$mode'.", error)
    }
  val agentAddonSelection = decodeGoalAgentAddonSelection(policy["agent_addon_selection"])
  return GoalRunnerReviewPolicy(codeReviewMode, agentAddonSelection)
}

fun outOfBandAcceptancesFromLegacyArtifacts(
  artifacts: DurableWorkflowArtifacts,
): Map<Int, GoalRunnerOutOfBandAcceptance> {
  val artifactFamily = DurableWorkflowArtifactFamily.GOAL_OUT_OF_BAND_ACCEPTANCE
  val raw = artifactFamily.value(artifacts) ?: return emptyMap()
  val entries =
    raw as? List<*>
      ?: error("Goal acceptance artifact '${artifactFamily.label()}' must be a list.")
  return entries.associate { element ->
    val entry =
      JsonCodec.anyToStringAnyMap(element)
        ?: error("Goal acceptance artifact '${artifactFamily.label()}' entries must be maps.")
    val acceptance =
      GoalRunnerOutOfBandAcceptance(
        subtaskId =
          (entry[SharedPayloadKeys.SUBTASK_ID] as? Number)?.toInt()
            ?: error("Goal acceptance artifact entry is missing a numeric subtask_id."),
        commitSha =
          entry["commit_sha"] as? String
            ?: error("Goal acceptance artifact entry is missing commit_sha."),
        reason =
          entry["reason"] as? String
            ?: error("Goal acceptance artifact entry is missing reason."),
        acceptedAt =
          entry["accepted_at"] as? String
            ?: error("Goal acceptance artifact entry is missing accepted_at."),
      )
    acceptance.subtaskId to acceptance
  }
}

private fun decodeGoalAgentAddonSelection(raw: Any?): AgentAddonSelection {
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
