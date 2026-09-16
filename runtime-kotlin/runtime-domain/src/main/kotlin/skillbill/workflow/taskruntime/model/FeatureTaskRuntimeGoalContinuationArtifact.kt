package skillbill.workflow.taskruntime.model

import skillbill.agentaddon.model.AgentAddonSelection
import skillbill.agentaddon.model.PersistedAgentAddonSelectionEntry
import skillbill.contracts.SharedPayloadKeys
import skillbill.error.InvalidWorkflowStateSchemaError
import skillbill.review.context.model.CodeReviewExecutionMode
import skillbill.workflow.goal.model.ValidationDepth

data class FeatureTaskRuntimeGoalContinuationArtifact(
  val issueKey: String,
  val subtaskId: Int,
  val suppressPr: Boolean,
  val goalBranch: String,
  val parentWorkflowId: String? = null,
  val codeReviewMode: CodeReviewExecutionMode,

  val validationDepth: ValidationDepth? = null,

  val qualityGateSelection: FeatureTaskRuntimeQualityGateSelection? = null,
  val parallelReviewAgent: String? = null,

  val subtaskName: String? = null,
  val agentAddonSelection: AgentAddonSelection = AgentAddonSelection(),
) {
  init {
    require(issueKey.isNotBlank()) { "FeatureTaskRuntimeGoalContinuationArtifact.issueKey must be non-blank." }
    require(subtaskId > 0) { "FeatureTaskRuntimeGoalContinuationArtifact.subtaskId must be positive." }
    require(goalBranch.isNotBlank()) { "FeatureTaskRuntimeGoalContinuationArtifact.goalBranch must be non-blank." }
    parallelReviewAgent?.let {
      require(it.isNotBlank()) { "FeatureTaskRuntimeGoalContinuationArtifact.parallelReviewAgent must be non-blank." }
    }
    subtaskName?.let {
      require(it.isNotBlank()) { "FeatureTaskRuntimeGoalContinuationArtifact.subtaskName must be non-blank." }
    }
  }
  internal fun toArtifactMap(): Map<String, Any?> = linkedMapOf<String, Any?>(
    SharedPayloadKeys.ISSUE_KEY to issueKey,
    SharedPayloadKeys.SUBTASK_ID to subtaskId,
    "suppress_pr" to suppressPr,
    "goal_branch" to goalBranch,
    "code_review_mode" to codeReviewMode.wireValue,
  ).apply {
    parentWorkflowId?.let { put("parent_workflow_id", it) }
    validationDepth?.let { put("validation_depth", it.wireValue) }
    qualityGateSelection?.let { put("quality_gate_selection", it.wireValue) }
    subtaskName?.let { put("subtask_name", it) }
    if (agentAddonSelection.entries.isNotEmpty()) {
      put(
        "agent_addon_selection",
        agentAddonSelection.entries.map { entry ->
          linkedMapOf(
            "slug" to entry.slug,
            "source_identity" to entry.sourceIdentity,
            "content_sha256" to entry.contentSha256,
          )
        },
      )
    }
  }

  companion object {
    internal fun fromArtifactMap(raw: Map<String, Any?>): FeatureTaskRuntimeGoalContinuationArtifact {
      rejectUnknownGoalContinuationKeys(raw)
      val reader = durableArtifactMapReader(raw)
      val subtaskId = reader.requiredInt(SharedPayloadKeys.SUBTASK_ID)
      if (subtaskId < 1) {
        throw InvalidWorkflowStateSchemaError(
          "Goal-continuation artifact field '${SharedPayloadKeys.SUBTASK_ID}' must be positive.",
        )
      }
      return FeatureTaskRuntimeGoalContinuationArtifact(
        issueKey = reader.requiredString(SharedPayloadKeys.ISSUE_KEY),
        subtaskId = subtaskId,
        suppressPr = reader.optionalBoolean("suppress_pr")
          ?: throw InvalidWorkflowStateSchemaError(
            "Goal-continuation artifact field 'suppress_pr' must be a boolean.",
          ),
        goalBranch = reader.requiredString("goal_branch"),
        parentWorkflowId = reader.optionalString("parent_workflow_id"),
        codeReviewMode = reader.requiredString("code_review_mode").let { rawValue ->
          CodeReviewExecutionMode.entries.firstOrNull { it.wireValue == rawValue }
            ?: goalContinuationSchemaError(
              "Goal-continuation artifact code_review_mode has unsupported value '$rawValue'.",
            )
        },
        validationDepth = reader.optionalString("validation_depth")?.let { rawValue ->
          try {
            ValidationDepth.fromWire(rawValue)
          } catch (error: IllegalArgumentException) {
            goalContinuationSchemaError("Goal-continuation artifact validation_depth is invalid.", error)
          }
        },
        qualityGateSelection = reader.optionalString("quality_gate_selection")?.let { rawValue ->
          FeatureTaskRuntimeQualityGateSelection.entries.firstOrNull { it.wireValue == rawValue }
            ?: goalContinuationSchemaError("Goal-continuation artifact quality_gate_selection is invalid.")
        },
        parallelReviewAgent = reader.optionalString("parallel_review_agent"),
        subtaskName = reader.optionalString("subtask_name"),
        agentAddonSelection = raw.optionalGoalAgentAddonSelection(),
      )
    }
  }
}

private val goalContinuationKeys: Set<String> = setOf(
  SharedPayloadKeys.ISSUE_KEY,
  SharedPayloadKeys.SUBTASK_ID,
  "suppress_pr",
  "goal_branch",
  "parent_workflow_id",
  "code_review_mode",
  "validation_depth",
  "quality_gate_selection",
  "parallel_review_agent",
  "subtask_name",
  "agent_addon_selection",
)

private fun Map<String, Any?>.optionalGoalAgentAddonSelection(): AgentAddonSelection {
  val rawEntries = this["agent_addon_selection"] ?: return AgentAddonSelection()
  val entries = rawEntries as? List<*>
    ?: goalContinuationSchemaError("Goal-continuation agent_addon_selection must be a list.")
    val parsed = entries.mapIndexed(::parseGoalAgentAddonEntry)
    if (parsed.map { it.slug }.distinct().size != parsed.size) {
      goalContinuationSchemaError("Goal-continuation agent_addon_selection must not contain duplicate slugs.")
    }
    return AgentAddonSelection(parsed)
}

private fun parseGoalAgentAddonEntry(index: Int, value: Any?): PersistedAgentAddonSelectionEntry {
  val entry = value as? Map<*, *>
    ?: goalContinuationSchemaError("Goal-continuation agent_addon_selection entry $index is invalid.")
  if (entry.keys != setOf("slug", "source_identity", "content_sha256")) {
    goalContinuationSchemaError("Goal-continuation agent_addon_selection entry $index has invalid fields.")
  }
  val slug = (entry["slug"] as? String)?.takeIf(String::isNotBlank)
    ?: goalContinuationSchemaError("Goal-continuation add-on entry $index is missing slug.")
  val sourceIdentity = (entry["source_identity"] as? String)?.takeIf(String::isNotBlank)
    ?: goalContinuationSchemaError("Goal-continuation add-on entry $index is missing source_identity.")
  val contentSha256 = (entry["content_sha256"] as? String)?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
    ?: goalContinuationSchemaError("Goal-continuation add-on entry $index has an invalid content_sha256.")
  if (!slug.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))) {
    goalContinuationSchemaError("Goal-continuation add-on entry $index has an invalid slug.")
  }
  return PersistedAgentAddonSelectionEntry(slug, sourceIdentity, contentSha256)
}

private fun goalContinuationSchemaError(detail: String, cause: Throwable? = null): Nothing {
  throw InvalidWorkflowStateSchemaError(detail, cause)
}

private fun rejectUnknownGoalContinuationKeys(raw: Map<String, Any?>) {
  raw.keys.firstOrNull { it !in goalContinuationKeys }?.let { key ->
    throw InvalidWorkflowStateSchemaError("Goal-continuation artifact field '$key' is not supported.")
  }
}

