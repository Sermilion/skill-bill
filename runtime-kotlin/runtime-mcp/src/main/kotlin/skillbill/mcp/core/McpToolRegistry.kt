package skillbill.mcp.core

import skillbill.contracts.mcp.McpToolPayloadKeys
import skillbill.mcp.shared.McpProtocolFramer

internal data class McpTool(
  val name: String,
  val description: String,
  val inputSchema: Map<String, Any?>,
  val handler: McpToolHandler,
  val normalize: ((Map<String, Any?>) -> Map<String, Any?>)? = null,
) {
  fun toPayload(): Map<String, Any?> =
    linkedMapOf(
      McpProtocolFramer.NAME_KEY to name,
      McpProtocolFramer.DESCRIPTION_KEY to description,
      McpProtocolFramer.INPUT_SCHEMA_KEY to inputSchema,
    )
}

internal typealias McpToolSpec = McpTool

internal object McpToolRegistry {
  private val orderedToolNames: List<String> =
    listOf(
      McpToolPayloadKeys.ADD_LEARNING,
      "doctor",
      "feature_task_phase_block",
      "feature_task_phase_complete",
      "feature_verify_finished",
      "feature_verify_stats",
      "feature_verify_started",
      "feature_verify_workflow_get",
      "feature_verify_workflow_latest",
      "feature_verify_workflow_list",
      "feature_verify_workflow_continue",
      "feature_verify_workflow_open",
      "feature_verify_workflow_resume",
      "feature_verify_workflow_update",
      "goal_stats",
      "import_review",
      "new_skill_scaffold",
      "pr_description_generated",
      McpToolPayloadKeys.QUALITY_CHECK_FINISHED,
      "quality_check_started",
      "resolve_learnings",
      "review_stats",
      "telemetry_proxy_capabilities",
      "telemetry_remote_stats",
      "triage_findings",
      "update_check",
    )

  private val toolDescriptions: Map<String, String> =
    mapOf(
      McpToolPayloadKeys.ADD_LEARNING to
        "Create a learning from a rejected review finding after user confirmation.",
      "doctor" to "Check skill-bill installation health.",
      "feature_task_phase_block" to
        "Durable-block a prose feature-task phase (preplan|plan|implement|simplify|audit).",
      "feature_task_phase_complete" to
        "Complete a prose feature-task phase (preplan|plan|implement|simplify|audit) via durable settlement.",
      "feature_verify_finished" to "Record completion of a feature-verify session.",
      "feature_verify_stats" to "Show aggregate bill-feature-verify metrics.",
      "feature_verify_started" to "Record start of a feature-verify session.",
      "feature_verify_workflow_continue" to "Continue durable bill-feature-verify workflow state.",
      "feature_verify_workflow_get" to "Fetch read-only full durable bill-feature-verify workflow state.",
      "feature_verify_workflow_latest" to "Fetch the latest bill-feature-verify workflow.",
      "feature_verify_workflow_list" to "List bill-feature-verify workflows.",
      "feature_verify_workflow_open" to "Open durable bill-feature-verify workflow state.",
      "feature_verify_workflow_resume" to "Summarize bill-feature-verify workflow resume state.",
      "feature_verify_workflow_update" to
        "Update durable bill-feature-verify workflow state and return a compact acknowledgement.",
      "goal_stats" to "Show aggregate decomposed-goal runtime metrics.",
      "import_review" to "Import code review output into the local telemetry store.",
      "new_skill_scaffold" to "Scaffold a new skill from a validated payload.",
      "pr_description_generated" to "Record PR description generation telemetry.",
      McpToolPayloadKeys.QUALITY_CHECK_FINISHED to "Record completion of a quality-check session.",
      "quality_check_started" to "Record start of a quality-check session.",
      "resolve_learnings" to "Resolve active learnings for a review context.",
      "review_stats" to "Show review acceptance metrics.",
      "telemetry_proxy_capabilities" to "Show configured telemetry proxy capabilities.",
      "telemetry_remote_stats" to "Fetch aggregate org-wide workflow metrics.",
      "triage_findings" to "Record triage decisions for imported review findings.",
      "update_check" to "Check whether the installed skill-bill runtime is up to date.",
    )

  private val projectedInputSchemas: Map<String, Map<String, Any?>> by lazy {
    orderedToolNames.associateWith(McpInputSchemaProjection::projectedInputSchema)
  }

  val tools: List<McpTool> =
    orderedToolNames.map { name ->
      McpTool(
        name = name,
        description = toolDescriptions.getValue(name),
        inputSchema = projectedInputSchemas.getValue(name),
        handler = McpToolDispatcher.handlerFor(name),
        normalize =
          when (name) {
            McpToolPayloadKeys.QUALITY_CHECK_FINISHED -> McpToolDispatcher::normalizeQualityCheckFinished
            McpToolPayloadKeys.FEATURE_VERIFY_FINISHED -> McpToolDispatcher::normalizeFeatureVerifyFinished
            else -> null
          },
      )
    }

  fun toolNamed(name: String): McpToolSpec? = tools.firstOrNull { it.name == name }
}
