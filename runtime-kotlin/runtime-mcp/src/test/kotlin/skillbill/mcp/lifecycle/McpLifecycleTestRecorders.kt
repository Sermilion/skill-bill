package skillbill.mcp.lifecycle

import skillbill.mcp.shared.McpRuntimeContext
import skillbill.mcp.shared.callToolPayload

internal fun recordQualityCheckLifecycle(context: McpRuntimeContext) {
  val started =
    context.callToolPayload(
      "quality_check_started",
      mapOf(
        "routed_skill" to "bill-kotlin-code-check",
        "detected_stack" to "kotlin",
        "fallback" to false,
        "scope_type" to "branch_diff",
        "initial_failure_count" to 1,
        "orchestrated" to false,
      ),
    )
  context.callToolPayload(
    "quality_check_finished",
    mapOf(
      "session_id" to started["session_id"],
      "final_failure_count" to 0,
      "iterations" to 2,
      "result" to "pass",
      "failing_check_names" to emptyList<String>(),
      "unsupported_reason" to "",
      "orchestrated" to false,
      "routed_skill" to "bill-kotlin-code-check",
      "detected_stack" to "kotlin",
      "fallback" to false,
      "scope_type" to "branch_diff",
      "initial_failure_count" to 1,
      "duration_seconds" to 0,
    ),
  )
}

internal fun recordFeatureVerifyLifecycle(context: McpRuntimeContext) {
  val started =
    context.callToolPayload(
      "feature_verify_started",
      mapOf(
        "acceptance_criteria_count" to 2,
        "rollout_relevant" to true,
        "spec_summary" to "Verify native lifecycle",
        "orchestrated" to false,
      ),
    )
  context.callToolPayload(
    "feature_verify_finished",
    mapOf(
      "session_id" to started["session_id"],
      "feature_flag_audit_performed" to true,
      "review_iterations" to 1,
      "audit_result" to "had_gaps",
      "completion_status" to "completed",
      "history_relevance" to "low",
      "history_helpfulness" to "medium",
      "gaps_found" to listOf("missing QA"),
      "orchestrated" to false,
      "acceptance_criteria_count" to 0,
      "rollout_relevant" to false,
      "spec_summary" to "",
      "duration_seconds" to 0,
    ),
  )
}

internal fun recordPrDescriptionLifecycle(context: McpRuntimeContext) {
  context.callToolPayload(
    "pr_description_generated",
    mapOf(
      "commit_count" to 3,
      "files_changed_count" to 8,
      "was_edited_by_user" to false,
      "pr_created" to false,
      "pr_title" to "SKILL-27 native lifecycle",
      "orchestrated" to false,
    ),
  )
}
