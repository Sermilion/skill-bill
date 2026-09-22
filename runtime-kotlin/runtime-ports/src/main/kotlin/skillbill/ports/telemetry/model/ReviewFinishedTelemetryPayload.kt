package skillbill.ports.telemetry.model

import skillbill.contracts.JsonPayloadContract
import skillbill.contracts.review.ReviewFindingPayloadKeys
import skillbill.contracts.review.ReviewFinishedTelemetryPayloadKeys
import skillbill.contracts.review.ReviewVerificationSignalKeys
import skillbill.review.model.ReviewFindingDetail
import skillbill.review.model.ReviewFinishedFindingStats
import skillbill.review.model.ReviewFinishedTelemetry
import skillbill.review.model.ReviewLearningsSummary
import skillbill.review.model.ReviewStageMetrics
import skillbill.review.model.ReviewStageVerdictDistribution

fun ReviewFinishedTelemetry.toReviewFinishedTelemetryPayload(): JsonPayloadContract =
  ReviewFinishedTelemetryPayloadContract(this)

private class ReviewFinishedTelemetryPayloadContract(
  private val telemetry: ReviewFinishedTelemetry,
) : JsonPayloadContract {
  override fun toPayload(): Map<String, Any?> =
    LinkedHashMap<String, Any?>().apply {
      putAll(telemetry.findingStats.toPayload())
      put(ReviewVerificationSignalKeys.REVIEW_RUN_ID, telemetry.reviewRunId)
      put(ReviewFinishedTelemetryPayloadKeys.REVIEW_SESSION_ID, telemetry.reviewSessionId)
      put(ReviewFinishedTelemetryPayloadKeys.ROUTED_SKILL, telemetry.routedSkill)
      put(ReviewFinishedTelemetryPayloadKeys.REVIEW_SUBSKILLS, telemetry.reviewSubskills)
      put(ReviewFinishedTelemetryPayloadKeys.REVIEW_SCOPE, telemetry.reviewScope)
      put(ReviewFinishedTelemetryPayloadKeys.REVIEW_PLATFORM, telemetry.reviewPlatform)
      put(ReviewFinishedTelemetryPayloadKeys.DETECTED_STACK, telemetry.detectedStack)
      telemetry.detectedStackDetail?.let { put(ReviewFinishedTelemetryPayloadKeys.DETECTED_STACK_DETAIL, it) }
      put(ReviewFinishedTelemetryPayloadKeys.FALLBACK, telemetry.fallback)
      telemetry.fallbackReason?.let { put(ReviewFinishedTelemetryPayloadKeys.FALLBACK_REASON, it) }
      put(ReviewFinishedTelemetryPayloadKeys.PLATFORM_SLUG, telemetry.platformSlug)
      put(ReviewFinishedTelemetryPayloadKeys.SCOPE_TYPE, telemetry.scopeType)
      put(ReviewFinishedTelemetryPayloadKeys.EXECUTION_MODE, telemetry.executionMode?.wireValue)
      put(ReviewFinishedTelemetryPayloadKeys.REVIEW_FINISHED_AT, telemetry.reviewFinishedAt)
      put(ReviewFinishedTelemetryPayloadKeys.LEARNINGS, telemetry.learnings.toPayload())
      putAll(telemetry.stageMetrics.toStageMetricsPayload())
    }
}

private fun ReviewFinishedFindingStats.toPayload(): Map<String, Any?> =
  linkedMapOf(
    ReviewFinishedTelemetryPayloadKeys.TOTAL_FINDINGS to totalFindings,
    ReviewFinishedTelemetryPayloadKeys.ACCEPTED_FINDINGS to acceptedFindings,
    ReviewFinishedTelemetryPayloadKeys.REJECTED_FINDINGS to rejectedFindings,
    ReviewFinishedTelemetryPayloadKeys.UNRESOLVED_FINDINGS to unresolvedFindings,
    ReviewFinishedTelemetryPayloadKeys.ACCEPTED_RATE to acceptedRate,
    ReviewFinishedTelemetryPayloadKeys.REJECTED_RATE to rejectedRate,
    ReviewFinishedTelemetryPayloadKeys.ACCEPTED_FINDING_DETAILS to
      acceptedFindingDetails.map(
        ReviewFindingDetail::toReviewFinishedPayload,
      ),
    ReviewFinishedTelemetryPayloadKeys.REJECTED_FINDING_DETAILS to
      rejectedFindingDetails.map(
        ReviewFindingDetail::toReviewFinishedPayload,
      ),
  )

private fun ReviewFindingDetail.toReviewFinishedPayload(): Map<String, Any?> =
  linkedMapOf<String, Any?>(
    ReviewFindingPayloadKeys.FINDING_ID to findingId,
    ReviewFindingPayloadKeys.ISSUE_CATEGORY to issueCategory,
    ReviewFinishedTelemetryPayloadKeys.SEVERITY to severity,
    ReviewFinishedTelemetryPayloadKeys.CONFIDENCE to confidence,
    ReviewFinishedTelemetryPayloadKeys.OUTCOME_TYPE to outcomeType,
  ).apply {
    if (location.isNotEmpty()) put(ReviewFinishedTelemetryPayloadKeys.LOCATION, location)
    if (description.isNotEmpty()) put(ReviewFinishedTelemetryPayloadKeys.DESCRIPTION, description)
    if (note.isNotEmpty()) put(ReviewFinishedTelemetryPayloadKeys.NOTE, note)
  }

private fun ReviewLearningsSummary.toPayload(): Map<String, Any?> =
  linkedMapOf(
    ReviewFinishedTelemetryPayloadKeys.APPLIED_COUNT to appliedCount,
    ReviewFinishedTelemetryPayloadKeys.APPLIED_REFERENCES to appliedReferences,
    ReviewFinishedTelemetryPayloadKeys.APPLIED_SUMMARY to appliedSummary,
    ReviewFinishedTelemetryPayloadKeys.SCOPE_COUNTS to scopeCounts,
    ReviewFinishedTelemetryPayloadKeys.ENTRIES to
      entries.map { entry ->
        linkedMapOf<String, Any?>(
          ReviewFinishedTelemetryPayloadKeys.REFERENCE to entry.reference,
          ReviewFinishedTelemetryPayloadKeys.SCOPE to entry.scope,
        ).apply {
          entry.title?.let { put(ReviewFinishedTelemetryPayloadKeys.TITLE, it) }
          entry.ruleText?.let { put(ReviewFinishedTelemetryPayloadKeys.RULE_TEXT, it) }
        }.filterValues { it != null }
      },
  )

private fun ReviewStageMetrics.toStageMetricsPayload(): Map<String, Any?> =
  linkedMapOf(
    ReviewFinishedTelemetryPayloadKeys.VERIFICATION to verification.toStageMetricsPayload(),
    ReviewFinishedTelemetryPayloadKeys.ADJUDICATION to adjudication.toStageMetricsPayload(),
    ReviewFinishedTelemetryPayloadKeys.REFUTATION_RATE_BY_STAGE to
      linkedMapOf(
        ReviewFinishedTelemetryPayloadKeys.VERIFICATION to verificationRefutationRate,
        ReviewFinishedTelemetryPayloadKeys.ADJUDICATION to adjudicationRefutationRate,
      ),
    ReviewFinishedTelemetryPayloadKeys.REJECTED_VERDICT_COUNTS to
      linkedMapOf(
        ReviewFinishedTelemetryPayloadKeys.UNCITED_REFUTATIONS to rejectedVerdictCounts.uncitedRefutations,
        ReviewFinishedTelemetryPayloadKeys.UNCITED_DOWNGRADES to rejectedVerdictCounts.uncitedDowngrades,
        ReviewFinishedTelemetryPayloadKeys.FINDING_MUTATIONS to rejectedVerdictCounts.findingMutations,
      ),
    ReviewFinishedTelemetryPayloadKeys.SEVERITY_ADJUSTMENT_COUNTS to
      linkedMapOf(
        ReviewFinishedTelemetryPayloadKeys.RAISED to severityAdjustmentCounts.raised,
        ReviewFinishedTelemetryPayloadKeys.LOWERED to severityAdjustmentCounts.lowered,
      ),
    ReviewFinishedTelemetryPayloadKeys.RESOLVED_TIER to resolvedTier,
  )

private fun ReviewStageVerdictDistribution.toStageMetricsPayload(): Map<String, Any?> =
  linkedMapOf(
    ReviewFindingPayloadKeys.CLAIM_VERDICT to
      linkedMapOf(
        ReviewFinishedTelemetryPayloadKeys.CONFIRMED to confirmed,
        ReviewFinishedTelemetryPayloadKeys.REFUTED to refuted,
        ReviewFinishedTelemetryPayloadKeys.UNRESOLVED to unresolved,
      ),
    ReviewFindingPayloadKeys.SCOPE_DISPOSITION to
      linkedMapOf(
        ReviewFinishedTelemetryPayloadKeys.IN_SCOPE to inScope,
        ReviewFinishedTelemetryPayloadKeys.OUT_OF_SCOPE_PREEXISTING to outOfScopePreexisting,
        ReviewFinishedTelemetryPayloadKeys.SPEC_DEVIATION to specDeviation,
        ReviewFinishedTelemetryPayloadKeys.SPEC_ACCEPTED_TRADEOFF to specAcceptedTradeoff,
      ),
    ReviewFinishedTelemetryPayloadKeys.FINDING_COUNT to findingCount,
  )
