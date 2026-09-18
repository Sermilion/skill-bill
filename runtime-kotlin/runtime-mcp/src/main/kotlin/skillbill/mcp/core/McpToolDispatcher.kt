package skillbill.mcp.core

import skillbill.application.workflow.model.WorkflowFamilyKind
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.mcp.McpToolPayloadKeys
import skillbill.contracts.telemetry.LifecycleTelemetryPayloadKeys
import skillbill.error.InvalidMcpToolArgumentError
import skillbill.mcp.featuretask.featureTaskPhaseBlock
import skillbill.mcp.featuretask.featureTaskPhaseComplete
import skillbill.mcp.lifecycle.featureVerifyFinished
import skillbill.mcp.lifecycle.featureVerifyStarted
import skillbill.mcp.lifecycle.prDescriptionGenerated
import skillbill.mcp.lifecycle.qualityCheckFinished
import skillbill.mcp.lifecycle.qualityCheckStarted
import skillbill.mcp.shared.McpComponent
import skillbill.mcp.shared.McpRuntimeLifecycle
import skillbill.mcp.shared.boolean
import skillbill.mcp.shared.componentForLegacyContext
import skillbill.mcp.shared.optionalString
import skillbill.mcp.shared.string
import skillbill.mcp.shared.stringList
import skillbill.mcp.telemetry.TELEMETRY_EVENT_CONTRACT_VERSION
import skillbill.mcp.telemetry.TelemetryEventSchemaValidator
import skillbill.mcp.workflow.McpWorkflowRuntime
import skillbill.mcp.workflow.workflowContinue
import skillbill.mcp.workflow.workflowGet
import skillbill.mcp.workflow.workflowList
import skillbill.mcp.workflow.workflowOpen
import skillbill.mcp.workflow.workflowResume
import skillbill.mcp.workflow.workflowUpdate
import skillbill.telemetry.model.RemoteStatsRequest
import skillbill.mcp.shared.map as argumentMap

internal typealias McpToolHandler = (Map<String, Any?>, McpComponent) -> Map<String, Any?>

internal object McpToolDispatcher {
  fun call(toolName: String, arguments: Map<String, Any?>, context: Any): Map<String, Any?> =
    call(toolName, arguments, componentForLegacyContext(context))

  internal fun handlerFor(toolName: String): McpToolHandler =
    TOOL_HANDLERS[toolName] ?: throw InvalidMcpToolArgumentError(
      toolName = toolName,
      argumentKey = "tool",
      detail = "unknown tool",
    )

  fun call(toolName: String, arguments: Map<String, Any?>, component: McpComponent): Map<String, Any?> {
    val handler = handlerFor(toolName)
    val normalizedArguments = validateMcpToolArguments(toolName, arguments)
    return handler.invoke(normalizedArguments, component)
  }

  internal fun callValidated(
    toolName: String,
    arguments: Map<String, Any?>,
    component: McpComponent,
  ): Map<String, Any?> = handlerFor(toolName).invoke(arguments, component)

  internal fun validateMcpToolArguments(toolName: String, arguments: Map<String, Any?>): Map<String, Any?> {
    val tool = McpToolRegistry.toolNamed(toolName)
      ?: throw InvalidMcpToolArgumentError(
        toolName = toolName,
        argumentKey = "tool",
        detail = "unknown tool",
      )
    val normalizedArguments = tool.normalize?.invoke(arguments) ?: arguments
    TelemetryEventSchemaValidator.validate(
      envelope = telemetryEnvelope(toolName, normalizedArguments),
      eventName = toolName,
    )
    return normalizedArguments
  }

  internal fun telemetryEnvelope(toolName: String, arguments: Map<String, Any?>): Map<String, Any?> {
    val envelope = linkedMapOf<String, Any?>(
      McpToolPayloadKeys.EVENT_NAME to toolName,
      SharedPayloadKeys.CONTRACT_VERSION to TELEMETRY_EVENT_CONTRACT_VERSION,
    )
    arguments.forEach { (key, value) ->

      if (key != McpToolPayloadKeys.EVENT_NAME && key != SharedPayloadKeys.CONTRACT_VERSION) {
        envelope[key] = value
      }
    }
    return envelope
  }

  internal fun normalizeQualityCheckFinished(arguments: Map<String, Any?>): Map<String, Any?> {
    RUNTIME_OWNED_QUALITY_CHECK_KEYS.firstOrNull(arguments::containsKey)?.let { key ->
      throw InvalidMcpToolArgumentError(McpToolPayloadKeys.QUALITY_CHECK_FINISHED, key, "is runtime-owned")
    }
    val stack = arguments[McpToolPayloadKeys.DETECTED_STACK]?.toString()?.trim().orEmpty().ifBlank { "unknown" }
    val fallback = arguments[McpToolPayloadKeys.FALLBACK] == true
    return arguments.toMutableMap().apply {
      put(
        McpToolPayloadKeys.ROUTED_SKILL,
        normalizeQualityCheckRoutedSkill(
          arguments[McpToolPayloadKeys.ROUTED_SKILL]?.toString(),
        ),
      )
      put(McpToolPayloadKeys.DETECTED_STACK, stack)
      put(McpToolPayloadKeys.FALLBACK, fallback)
      val fallbackReason = arguments[McpToolPayloadKeys.FALLBACK_REASON]?.toString()?.takeIf(String::isNotBlank)
      if (fallback && !fallbackReason.isNullOrBlank()) {
        put(McpToolPayloadKeys.FALLBACK_REASON, fallbackReason)
      }
    }
  }
}

private val TOOL_HANDLERS: Map<String, McpToolHandler> = mapOf(
  "doctor" to { _, context -> McpRuntime.doctor(context) },
  "feature_task_phase_block" to ::featureTaskPhaseBlock,
  "feature_task_phase_complete" to ::featureTaskPhaseComplete,
  "feature_verify_finished" to ::featureVerifyFinished,
  "feature_verify_started" to ::featureVerifyStarted,
  "feature_verify_stats" to { _, context -> McpRuntime.featureVerifyStats(context) },
  "feature_verify_workflow_continue" to
    { arguments, context -> workflowContinue(WorkflowFamilyKind.VERIFY, arguments, context) },
  "feature_verify_workflow_get" to
    { arguments, context -> workflowGet(WorkflowFamilyKind.VERIFY, arguments, context) },
  "feature_verify_workflow_latest" to
    { _, context -> McpWorkflowRuntime.latest(WorkflowFamilyKind.VERIFY, context) },
  "feature_verify_workflow_list" to
    { arguments, context -> workflowList(WorkflowFamilyKind.VERIFY, arguments, context) },
  "feature_verify_workflow_open" to
    { arguments, context -> workflowOpen(WorkflowFamilyKind.VERIFY, arguments, context) },
  "feature_verify_workflow_resume" to
    { arguments, context -> workflowResume(WorkflowFamilyKind.VERIFY, arguments, context) },
  "feature_verify_workflow_update" to
    { arguments, context -> workflowUpdate(WorkflowFamilyKind.VERIFY, arguments, context) },
  "goal_stats" to { _, context -> McpRuntime.goalStats(context) },
  "import_review" to ::importReview,
  "new_skill_scaffold" to ::newSkillScaffold,
  "pr_description_generated" to ::prDescriptionGenerated,
  McpToolPayloadKeys.QUALITY_CHECK_FINISHED to ::qualityCheckFinished,
  "quality_check_started" to ::qualityCheckStarted,
  "resolve_learnings" to ::resolveLearnings,
  "review_stats" to
    { arguments, context ->
      McpRuntime.reviewStats(arguments.optionalString(McpToolPayloadKeys.REVIEW_RUN_ID), context)
    },
  "telemetry_proxy_capabilities" to
    { _, context -> McpRuntimeLifecycle.telemetryProxyCapabilities(context) },
  "telemetry_remote_stats" to ::telemetryRemoteStats,
  "triage_findings" to ::triageFindings,
  "update_check" to { _, context -> McpRuntime.updateCheck(context) },
)

private val RUNTIME_OWNED_QUALITY_CHECK_KEYS: Set<String> = setOf(
  LifecycleTelemetryPayloadKeys.COMPLETION,
  LifecycleTelemetryPayloadKeys.FINAL_FAILURE_COUNT_AVAILABILITY,
  LifecycleTelemetryPayloadKeys.STALE_REASON,
)

private fun normalizeQualityCheckRoutedSkill(rawValue: String?): String {
  val value = rawValue?.trim().orEmpty()
  if (value.isEmpty()) return "unrouted"
  val withoutNamespace = value.substringAfter(':')
  return if (
    withoutNamespace != value &&
    withoutNamespace.matches(Regex("^[a-z0-9][a-z0-9-]*$")) &&
    value.substringBefore(':').matches(Regex("^[A-Za-z][A-Za-z0-9_-]*$"))
  ) {
    withoutNamespace
  } else {
    value
  }
}

internal fun importReview(arguments: Map<String, Any?>, component: McpComponent): Map<String, Any?> =
  McpRuntime.importReview(
    reviewText = arguments.string(McpToolPayloadKeys.REVIEW_TEXT),
    orchestrated = arguments.boolean(McpToolPayloadKeys.ORCHESTRATED),
    component = component,
  )

internal fun triageFindings(arguments: Map<String, Any?>, component: McpComponent): Map<String, Any?> =
  McpRuntime.triageFindings(
    reviewRunId = arguments.string(McpToolPayloadKeys.REVIEW_RUN_ID),
    decisions = arguments.stringList(McpToolPayloadKeys.DECISIONS),
    orchestrated = arguments.boolean(McpToolPayloadKeys.ORCHESTRATED),
    component = component,
  )

internal fun resolveLearnings(arguments: Map<String, Any?>, component: McpComponent): Map<String, Any?> =
  McpRuntime.resolveLearnings(
    repo = arguments.optionalString(McpToolPayloadKeys.REPO),
    skill = arguments.optionalString(McpToolPayloadKeys.SKILL),
    reviewSessionId = arguments.optionalString(McpToolPayloadKeys.REVIEW_SESSION_ID),
    component = component,
  )

internal fun telemetryRemoteStats(arguments: Map<String, Any?>, component: McpComponent): Map<String, Any?> =
  McpRuntimeLifecycle.telemetryRemoteStats(
    RemoteStatsRequest(
      workflow = mapRemoteStatsWorkflow(arguments.string(McpToolPayloadKeys.WORKFLOW)),
      since = arguments.optionalString(McpToolPayloadKeys.SINCE).orEmpty(),
      dateFrom = arguments.optionalString(McpToolPayloadKeys.DATE_FROM).orEmpty(),
      dateTo = arguments.optionalString(McpToolPayloadKeys.DATE_TO).orEmpty(),
      groupBy = arguments.optionalString(McpToolPayloadKeys.GROUP_BY).orEmpty(),
    ),
    component,
  )

private fun mapRemoteStatsWorkflow(workflow: String): String = when (workflow) {
  "verify" -> "bill-feature-verify"
  "bill-feature-verify", "feature-task-runtime" -> workflow
  else -> throw InvalidMcpToolArgumentError(
    toolName = "telemetry_remote_stats",
    argumentKey = "workflow",
    detail = "must be one of: verify, bill-feature-verify, feature-task-runtime",
  )
}

internal fun newSkillScaffold(arguments: Map<String, Any?>, component: McpComponent): Map<String, Any?> =
  McpRuntime.newSkillScaffold(
    payload = arguments.argumentMap(McpToolPayloadKeys.PAYLOAD),
    dryRun = arguments.boolean(McpToolPayloadKeys.DRY_RUN),
    orchestrated = arguments.boolean(McpToolPayloadKeys.ORCHESTRATED),
    component = component,
  )
