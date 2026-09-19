package skillbill.application.workflow.service
import skillbill.agentaddon.model.AgentAddonSelection
import skillbill.agentaddon.model.PersistedAgentAddonSelectionEntry
import skillbill.application.workflow.decomposition.artifactsJson
import skillbill.application.workflow.decomposition.error
import skillbill.application.workflow.decomposition.existing
import skillbill.application.workflow.decomposition.workflowId
import skillbill.application.workflow.persist.decodeWorkflowArtifacts
import skillbill.application.workflow.persist.keys
import skillbill.application.workflow.persist.workflowId
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.workflow.identity.task.FeatureTaskRuntimeGoalContinuationArtifactPayloadKeys
import skillbill.error.shellcontent.InvalidAgentAddonSelectionError
import skillbill.ports.goalrunner.GoalRunnerPersistenceSession
import skillbill.ports.goalrunner.runner.model.GoalRunnerOutOfBandAcceptance
import skillbill.ports.goalrunner.runner.model.GoalRunnerReviewPolicy
import skillbill.review.context.model.launch.CodeReviewExecutionMode
import skillbill.workflow.engine.model.DurableWorkflowArtifacts
import skillbill.workflow.engine.model.WorkflowStateSnapshot

const val GOAL_REVIEW_POLICY_ARTIFACT_KEY = "goal_review_policy"
const val GOAL_OUT_OF_BAND_ACCEPTANCE_ARTIFACT_KEY = "goal_out_of_band_acceptances"

fun migrateLegacyGoalRunnerControls(unitOfWork: GoalRunnerPersistenceSession, existing: WorkflowStateSnapshot) {
  val artifacts = decodeWorkflowArtifacts(existing.artifactsJson)
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
  val raw = artifacts[GOAL_REVIEW_POLICY_ARTIFACT_KEY] ?: return null
  val policy = JsonCodec.anyToStringAnyMap(raw)
    ?: error("Goal review policy artifact '$GOAL_REVIEW_POLICY_ARTIFACT_KEY' must be a map.")
  val allowedKeys = setOf("code_review_mode", "parallel_review_agent", "agent_addon_selection")
  policy.keys.forEach { key ->
    require(key in allowedKeys) {
      "Goal review policy artifact '$GOAL_REVIEW_POLICY_ARTIFACT_KEY' has unsupported field '$key'."
    }
  }
  val mode = policy["code_review_mode"] as? String
    ?: error("Goal review policy artifact '$GOAL_REVIEW_POLICY_ARTIFACT_KEY' is missing code_review_mode.")
  val codeReviewMode = try {
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
  val raw = artifacts[GOAL_OUT_OF_BAND_ACCEPTANCE_ARTIFACT_KEY] ?: return emptyMap()
  val entries = raw as? List<*>
    ?: error("Goal acceptance artifact '$GOAL_OUT_OF_BAND_ACCEPTANCE_ARTIFACT_KEY' must be a list.")
  return entries.associate { element ->
    val entry = JsonCodec.anyToStringAnyMap(element)
      ?: error("Goal acceptance artifact '$GOAL_OUT_OF_BAND_ACCEPTANCE_ARTIFACT_KEY' entries must be maps.")
    val acceptance = GoalRunnerOutOfBandAcceptance(
      subtaskId = (entry[SharedPayloadKeys.SUBTASK_ID] as? Number)?.toInt()
        ?: error("Goal acceptance artifact entry is missing a numeric subtask_id."),
      commitSha = entry["commit_sha"] as? String
        ?: error("Goal acceptance artifact entry is missing commit_sha."),
      reason = entry["reason"] as? String
        ?: error("Goal acceptance artifact entry is missing reason."),
      acceptedAt = entry["accepted_at"] as? String
        ?: error("Goal acceptance artifact entry is missing accepted_at."),
    )
    acceptance.subtaskId to acceptance
  }
}

private fun decodeGoalAgentAddonSelection(raw: Any?): AgentAddonSelection {
  val values = raw ?: return AgentAddonSelection()
  val entries = values as? List<*>
    ?: throw InvalidAgentAddonSelectionError("Goal review policy agent_addon_selection must be a list.")
  return AgentAddonSelection(
    entries.mapIndexed(::decodeGoalAgentAddonSelectionEntry),
  )
}

private fun decodeGoalAgentAddonSelectionEntry(index: Int, value: Any?): PersistedAgentAddonSelectionEntry {
  val entry = JsonCodec.anyToStringAnyMap(value)
    ?: throw InvalidAgentAddonSelectionError(
      "Goal review policy agent_addon_selection entry $index must be a map.",
    )
  val expectedKeys = setOf(
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

private fun requiredAddonField(entry: Map<String, Any?>, index: Int, key: String, label: String): String =
  entry[key] as? String
    ?: throw InvalidAgentAddonSelectionError("Goal review policy add-on entry $index is missing $label.")
