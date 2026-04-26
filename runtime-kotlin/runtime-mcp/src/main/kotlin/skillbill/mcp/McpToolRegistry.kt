package skillbill.mcp

import skillbill.workflow.implement.FeatureImplementWorkflowDefinition
import skillbill.workflow.model.WorkflowDefinition
import skillbill.workflow.verify.FeatureVerifyWorkflowDefinition

data class McpToolSpec(
  val name: String,
  val description: String,
  val inputSchema: Map<String, Any?> = objectSchema(),
) {
  fun toPayload(): Map<String, Any?> = linkedMapOf(
    "name" to name,
    "description" to description,
    "inputSchema" to inputSchema,
  )
}

object McpToolRegistry {
  private val toolNames: List<String> =
    listOf(
      "doctor",
      "feature_implement_finished",
      "feature_implement_stats",
      "feature_implement_started",
      "feature_implement_workflow_get",
      "feature_implement_workflow_latest",
      "feature_implement_workflow_list",
      "feature_implement_workflow_continue",
      "feature_implement_workflow_open",
      "feature_implement_workflow_resume",
      "feature_implement_workflow_update",
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
      "import_review",
      "new_skill_scaffold",
      "pr_description_generated",
      "quality_check_finished",
      "quality_check_started",
      "readian_auth_status",
      "readian_get_article",
      "readian_get_articles_for_topic_query",
      "readian_get_spotlight",
      "readian_mark_story_status",
      "readian_save_candidate",
      "resolve_learnings",
      "review_stats",
      "telemetry_proxy_capabilities",
      "telemetry_remote_stats",
      "triage_findings",
    )

  private val descriptions: Map<String, String> =
    mapOf(
      "doctor" to "Check skill-bill installation health.",
      "feature_implement_finished" to "Record completion of a feature-implement session.",
      "feature_implement_stats" to "Show aggregate bill-feature-implement metrics.",
      "feature_implement_started" to "Record start of a feature-implement session.",
      "feature_implement_workflow_continue" to "Continue durable bill-feature-implement workflow state.",
      "feature_implement_workflow_get" to "Fetch durable bill-feature-implement workflow state.",
      "feature_implement_workflow_latest" to "Fetch the latest bill-feature-implement workflow.",
      "feature_implement_workflow_list" to "List bill-feature-implement workflows.",
      "feature_implement_workflow_open" to "Open durable bill-feature-implement workflow state.",
      "feature_implement_workflow_resume" to "Summarize bill-feature-implement workflow resume state.",
      "feature_implement_workflow_update" to "Update durable bill-feature-implement workflow state.",
      "feature_verify_finished" to "Record completion of a feature-verify session.",
      "feature_verify_stats" to "Show aggregate bill-feature-verify metrics.",
      "feature_verify_started" to "Record start of a feature-verify session.",
      "feature_verify_workflow_continue" to "Continue durable bill-feature-verify workflow state.",
      "feature_verify_workflow_get" to "Fetch durable bill-feature-verify workflow state.",
      "feature_verify_workflow_latest" to "Fetch the latest bill-feature-verify workflow.",
      "feature_verify_workflow_list" to "List bill-feature-verify workflows.",
      "feature_verify_workflow_open" to "Open durable bill-feature-verify workflow state.",
      "feature_verify_workflow_resume" to "Summarize bill-feature-verify workflow resume state.",
      "feature_verify_workflow_update" to "Update durable bill-feature-verify workflow state.",
      "import_review" to "Import code review output into the local telemetry store.",
      "new_skill_scaffold" to "Scaffold a new skill from a validated payload.",
      "pr_description_generated" to "Record PR description generation telemetry.",
      "quality_check_finished" to "Record completion of a quality-check session.",
      "quality_check_started" to "Record start of a quality-check session.",
      "readian_auth_status" to "Report whether the Readian MCP boundary has an authenticated session.",
      "readian_get_article" to "Fetch a Readian article through the authenticated MCP boundary.",
      "readian_get_articles_for_topic_query" to
        "Fetch Readian articles for a topic query through the authenticated MCP boundary.",
      "readian_get_spotlight" to "Fetch Readian Spotlight articles through the authenticated MCP boundary.",
      "readian_mark_story_status" to "Mark story status through the authenticated Readian MCP boundary.",
      "readian_save_candidate" to "Save an editorial candidate through the authenticated Readian MCP boundary.",
      "resolve_learnings" to "Resolve active learnings for a review context.",
      "review_stats" to "Show review acceptance metrics.",
      "telemetry_proxy_capabilities" to "Show configured telemetry proxy capabilities.",
      "telemetry_remote_stats" to "Fetch aggregate org-wide workflow metrics.",
      "triage_findings" to "Record triage decisions for imported review findings.",
    )

  private val inputSchemas: Map<String, Map<String, Any?>> =
    mapOf(
      "feature_implement_started" to objectSchema(
        required = listOf(
          "feature_size",
          "acceptance_criteria_count",
          "open_questions_count",
          "spec_input_types",
          "spec_word_count",
          "rollout_needed",
        ),
        properties =
        mapOf(
          "feature_size" to stringSchema(
            description = "Feature size from Step 1 assessment.",
            enum = listOf("SMALL", "MEDIUM", "LARGE"),
          ),
          "acceptance_criteria_count" to integerSchema(
            description = "Number of concrete acceptance criteria found in the spec.",
          ),
          "open_questions_count" to integerSchema(
            description = "Number of unresolved open questions after intake.",
          ),
          "spec_input_types" to arraySchema(
            description = "Spec input sources used during intake.",
            items = stringSchema(enum = listOf("raw_text", "pdf", "markdown_file", "image", "directory")),
          ),
          "spec_word_count" to integerSchema(
            description = "Approximate word count of the implementation spec.",
          ),
          "rollout_needed" to booleanSchema(
            description = "Whether rollout or feature-flag handling is relevant.",
          ),
          "feature_name" to stringSchema(description = "Short human-readable feature name."),
          "issue_key" to stringSchema(description = "Optional Jira, Linear, GitHub, or other issue key."),
          "issue_key_type" to stringSchema(
            description = "Type of issue key when issue_key is provided.",
            enum = listOf("jira", "linear", "github", "other", "none"),
          ),
          "spec_summary" to stringSchema(description = "Brief spec summary from Step 1 assessment."),
        ),
      ),
      "feature_implement_workflow_update" to workflowUpdateSchema(
        FeatureImplementWorkflowDefinition.definition,
      ),
      "feature_verify_workflow_update" to workflowUpdateSchema(
        FeatureVerifyWorkflowDefinition.definition,
      ),
      "readian_get_article" to objectSchema(
        required = listOf("article_id"),
        properties = mapOf(
          "article_id" to stringSchema(description = "Readian article id to fetch."),
        ),
      ),
      "readian_get_articles_for_topic_query" to objectSchema(
        required = listOf("topic_query"),
        properties = mapOf(
          "topic_query" to stringSchema(description = "Topic query to resolve against subscribed Readian topics."),
          "date" to stringSchema(description = "Optional local date filter in YYYY-MM-DD form."),
          "start_date" to stringSchema(description = "Optional inclusive local start date in YYYY-MM-DD form."),
          "end_date" to stringSchema(description = "Optional inclusive local end date in YYYY-MM-DD form."),
          "subscribed_only" to booleanSchema(description = "Whether to restrict matching to subscribed topics."),
          "limit" to integerSchema(description = "Maximum number of articles to return."),
        ),
      ),
      "readian_get_spotlight" to objectSchema(
        properties = mapOf(
          "date" to stringSchema(description = "Optional local date filter in YYYY-MM-DD form."),
          "limit" to integerSchema(description = "Maximum number of Spotlight articles to return."),
        ),
      ),
      "readian_save_candidate" to objectSchema(
        required = listOf("candidate_id"),
        properties = mapOf(
          "candidate_id" to stringSchema(description = "Editorial candidate id to save."),
          "notes" to stringSchema(description = "Optional log-safe editorial notes."),
        ),
      ),
      "readian_mark_story_status" to objectSchema(
        required = listOf("story_id", "status"),
        properties = mapOf(
          "story_id" to stringSchema(description = "Readian story id to update."),
          "status" to stringSchema(description = "New editorial story status."),
        ),
      ),
    )

  val tools: List<McpToolSpec> =
    toolNames.map { name ->
      McpToolSpec(
        name = name,
        description = descriptions.getValue(name),
        inputSchema = inputSchemas[name] ?: objectSchema(),
      )
    }
}

private fun objectSchema(
  required: List<String> = emptyList(),
  properties: Map<String, Any?> = emptyMap(),
): Map<String, Any?> = linkedMapOf<String, Any?>(
  "type" to "object",
  "additionalProperties" to true,
  "properties" to properties,
).apply {
  if (required.isNotEmpty()) {
    put("required", required)
  }
}

private fun workflowUpdateSchema(definition: WorkflowDefinition): Map<String, Any?> = objectSchema(
  required = listOf("workflow_id", "workflow_status", "current_step_id"),
  properties =
  mapOf(
    "workflow_id" to stringSchema(description = "Workflow ID returned by the workflow open tool."),
    "workflow_status" to stringSchema(
      description = "Overall durable workflow status.",
      enum = definition.workflowStatuses.toList(),
    ),
    "current_step_id" to stringSchema(
      description = "Current workflow step id.",
      enum = definition.stepIds,
    ),
    "step_updates" to arraySchema(
      description = "Step status updates. Every update must include attempt_count.",
      items = objectSchema(
        required = listOf("step_id", "status", "attempt_count"),
        properties =
        mapOf(
          "step_id" to stringSchema(
            description = "Workflow step id being updated.",
            enum = definition.stepIds,
          ),
          "status" to stringSchema(
            description = "Status for this step.",
            enum = definition.stepStatuses.toList(),
          ),
          "attempt_count" to integerSchema(
            description = "Attempt count for this step, starting at 1 once the step has run.",
          ),
        ),
      ),
    ),
    "artifacts_patch" to objectSchema(
      properties = mapOf(
        "note" to stringSchema(
          description = "Optional placeholder only; artifacts_patch accepts arbitrary artifact keys.",
        ),
      ),
    ),
    "session_id" to stringSchema(description = "Optional lifecycle telemetry session id."),
  ),
)

private fun stringSchema(description: String? = null, enum: List<String> = emptyList()): Map<String, Any?> =
  primitiveSchema("string", description, enum)

private fun integerSchema(description: String? = null): Map<String, Any?> = primitiveSchema("integer", description)

private fun booleanSchema(description: String? = null): Map<String, Any?> = primitiveSchema("boolean", description)

private fun arraySchema(description: String? = null, items: Map<String, Any?>): Map<String, Any?> =
  linkedMapOf<String, Any?>(
    "type" to "array",
    "items" to items,
  ).apply {
    if (description != null) {
      put("description", description)
    }
  }

private fun primitiveSchema(
  type: String,
  description: String? = null,
  enum: List<String> = emptyList(),
): Map<String, Any?> = linkedMapOf<String, Any?>(
  "type" to type,
).apply {
  if (description != null) {
    put("description", description)
  }
  if (enum.isNotEmpty()) {
    put("enum", enum)
  }
}
