package skillbill.mcp.lifecycle

import skillbill.application.telemetry.model.FeatureVerifyFinishedRequest
import skillbill.application.telemetry.model.FeatureVerifyStartedRequest
import skillbill.application.telemetry.model.PrDescriptionGeneratedRequest
import skillbill.application.telemetry.model.QualityCheckFinishedRequest
import skillbill.application.telemetry.model.QualityCheckStartedRequest
import skillbill.application.telemetry.validation.historySignalValues
import skillbill.contracts.mcp.McpToolPayloadKeys
import skillbill.mcp.shared.McpComponent
import skillbill.mcp.shared.McpRuntimeLifecycle
import skillbill.mcp.shared.boolean
import skillbill.mcp.shared.int
import skillbill.mcp.shared.optionalString
import skillbill.mcp.shared.string
import skillbill.mcp.shared.stringList

internal fun qualityCheckStarted(
  arguments: Map<String, Any?>,
  component: McpComponent,
): Map<String, Any?> {
  return McpRuntimeLifecycle.qualityCheckStarted(
    QualityCheckStartedRequest(
      routedSkill = arguments.string(McpToolPayloadKeys.ROUTED_SKILL),
      detectedStack = arguments.string(McpToolPayloadKeys.DETECTED_STACK),
      fallback = arguments.boolean(McpToolPayloadKeys.FALLBACK),
      fallbackReason = arguments.optionalString(McpToolPayloadKeys.FALLBACK_REASON),
      scopeType = arguments.string(McpToolPayloadKeys.SCOPE_TYPE),
      initialFailureCount = arguments.int(McpToolPayloadKeys.INITIAL_FAILURE_COUNT, 0),
      orchestrated = arguments.boolean(McpToolPayloadKeys.ORCHESTRATED),
    ),
    component,
  )
}

internal fun qualityCheckFinished(
  arguments: Map<String, Any?>,
  component: McpComponent,
): Map<String, Any?> {
  return McpRuntimeLifecycle.qualityCheckFinished(
    QualityCheckFinishedRequest(
      finalFailureCount = arguments.int(McpToolPayloadKeys.FINAL_FAILURE_COUNT, 0),
      iterations = arguments.int(McpToolPayloadKeys.ITERATIONS, 0),
      result = arguments.string(McpToolPayloadKeys.RESULT),
      sessionId = arguments.string(McpToolPayloadKeys.SESSION_ID),
      failingCheckNames = arguments.stringList(McpToolPayloadKeys.FAILING_CHECK_NAMES),
      unsupportedReason = arguments.string(McpToolPayloadKeys.UNSUPPORTED_REASON),
      orchestrated = arguments.boolean(McpToolPayloadKeys.ORCHESTRATED),
      routedSkill = arguments.string(McpToolPayloadKeys.ROUTED_SKILL),
      detectedStack = arguments.string(McpToolPayloadKeys.DETECTED_STACK),
      fallback = arguments.boolean(McpToolPayloadKeys.FALLBACK),
      fallbackReason = arguments.optionalString(McpToolPayloadKeys.FALLBACK_REASON),
      scopeType = arguments.string(McpToolPayloadKeys.SCOPE_TYPE),
      initialFailureCount = arguments.int(McpToolPayloadKeys.INITIAL_FAILURE_COUNT, 0),
      durationSeconds = arguments.int(McpToolPayloadKeys.DURATION_SECONDS, 0),
    ),
    component,
  )
}

internal fun featureVerifyStarted(
  arguments: Map<String, Any?>,
  component: McpComponent,
): Map<String, Any?> =
  McpRuntimeLifecycle.featureVerifyStarted(
    FeatureVerifyStartedRequest(
      acceptanceCriteriaCount = arguments.int(McpToolPayloadKeys.ACCEPTANCE_CRITERIA_COUNT, 0),
      rolloutRelevant = arguments.boolean(McpToolPayloadKeys.ROLLOUT_RELEVANT),
      specSummary = arguments.string(McpToolPayloadKeys.SPEC_SUMMARY),
      orchestrated = arguments.boolean(McpToolPayloadKeys.ORCHESTRATED),
    ),
    component,
  )

internal fun featureVerifyFinished(
  arguments: Map<String, Any?>,
  component: McpComponent,
): Map<String, Any?> =
  McpRuntimeLifecycle.featureVerifyFinished(
    FeatureVerifyFinishedRequest(
      featureFlagAuditPerformed = arguments.boolean(McpToolPayloadKeys.FEATURE_FLAG_AUDIT_PERFORMED),
      reviewIterations = arguments.int(McpToolPayloadKeys.REVIEW_ITERATIONS, 0),
      auditResult = arguments.string(McpToolPayloadKeys.AUDIT_RESULT),
      completionStatus = arguments.string(McpToolPayloadKeys.COMPLETION_STATUS),
      historyRelevance = arguments.optionalString(McpToolPayloadKeys.HISTORY_RELEVANCE) ?: historySignalValues.first(),
      historyHelpfulness =
        arguments.optionalString(
          McpToolPayloadKeys.HISTORY_HELPFULNESS,
        ) ?: historySignalValues.first(),
      sessionId = arguments.string(McpToolPayloadKeys.SESSION_ID),
      gapsFound = arguments.stringList(McpToolPayloadKeys.GAPS_FOUND),
      orchestrated = arguments.boolean(McpToolPayloadKeys.ORCHESTRATED),
      acceptanceCriteriaCount = arguments.int(McpToolPayloadKeys.ACCEPTANCE_CRITERIA_COUNT, 0),
      rolloutRelevant = arguments.boolean(McpToolPayloadKeys.ROLLOUT_RELEVANT),
      specSummary = arguments.string(McpToolPayloadKeys.SPEC_SUMMARY),
      durationSeconds = arguments.int(McpToolPayloadKeys.DURATION_SECONDS, 0),
    ),
    component,
  )

internal fun prDescriptionGenerated(
  arguments: Map<String, Any?>,
  component: McpComponent,
): Map<String, Any?> =
  McpRuntimeLifecycle.prDescriptionGenerated(
    PrDescriptionGeneratedRequest(
      commitCount = arguments.int(McpToolPayloadKeys.COMMIT_COUNT, 0),
      filesChangedCount = arguments.int(McpToolPayloadKeys.FILES_CHANGED_COUNT, 0),
      wasEditedByUser = arguments.boolean(McpToolPayloadKeys.WAS_EDITED_BY_USER),
      prCreated = arguments.boolean(McpToolPayloadKeys.PR_CREATED),
      prTitle = arguments.string(McpToolPayloadKeys.PR_TITLE),
      orchestrated = arguments.boolean(McpToolPayloadKeys.ORCHESTRATED),
      generatedDescription = arguments.optionalString(McpToolPayloadKeys.GENERATED_DESCRIPTION),
      finalPrBody = arguments.optionalString(McpToolPayloadKeys.FINAL_PR_BODY),
    ),
    component,
  )
