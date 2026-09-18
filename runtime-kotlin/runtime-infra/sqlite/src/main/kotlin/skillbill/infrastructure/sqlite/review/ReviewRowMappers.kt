package skillbill.infrastructure.sqlite.review
import skillbill.contracts.review.ReviewFindingPayloadKeys
import skillbill.contracts.review.ReviewVerificationSignalKeys
import skillbill.contracts.review.SqliteReviewTelemetryPayloadKeys
import skillbill.contracts.telemetry.LifecycleTelemetryPayloadKeys
import skillbill.review.model.ImportedFinding
import skillbill.review.model.NumberedFinding
import skillbill.review.model.ReviewClaimVerdict
import skillbill.review.model.ReviewExecutionMode
import skillbill.review.model.ReviewFindingCitation
import skillbill.review.model.ReviewScopeDisposition
import skillbill.review.model.ReviewSeverityAdjustment
import skillbill.review.model.ReviewSeverityAdjustmentDirection
import skillbill.review.model.ReviewSummary
import java.sql.ResultSet

internal fun ResultSet.toImportedFinding(): ImportedFinding = ImportedFinding(
  findingId = getString(ReviewFindingPayloadKeys.FINDING_ID),
  severity = getString(SqliteReviewTelemetryPayloadKeys.SEVERITY),
  confidence = getString(SqliteReviewTelemetryPayloadKeys.CONFIDENCE),
  issueCategory = getString(ReviewFindingPayloadKeys.ISSUE_CATEGORY),
  location = getString(SqliteReviewTelemetryPayloadKeys.LOCATION),
  description = getString(SqliteReviewTelemetryPayloadKeys.DESCRIPTION),
  findingText = getString("finding_text"),
  laneSkillName = getString("lane_skill_name"),
)

internal fun ResultSet.toReviewSummary(): ReviewSummary = ReviewSummary(
  reviewRunId = getString(ReviewVerificationSignalKeys.REVIEW_RUN_ID),
  reviewSessionId = getString("review_session_id"),
  routedSkill = getString(LifecycleTelemetryPayloadKeys.ROUTED_SKILL),
  detectedScope = getString("detected_scope"),
  detectedStack = getString(LifecycleTelemetryPayloadKeys.DETECTED_STACK),
  executionMode = getString("execution_mode")?.let(ReviewExecutionMode::fromWire),
  specialistReviewsRaw = getString("specialist_reviews"),
  reviewFinishedAt = getString("review_finished_at"),
  reviewFinishedEventEmittedAt = getString("review_finished_event_emitted_at"),
  orchestratedRun = getBoolean("orchestrated_run"),
  routedSkillCanonical = getString("routed_skill_canonical") ?: "unresolved",
  detectedStackCanonical = getString("detected_stack_canonical") ?: "unresolved",
  detectedScopeCanonical = getString("detected_scope_canonical") ?: "unresolved",
  detectedScopeDetail = getString("detected_scope_detail"),
)

internal fun ResultSet.toNumberedFinding(number: Int): NumberedFinding = NumberedFinding(
  number = number,
  findingId = getString(ReviewFindingPayloadKeys.FINDING_ID),
  severity = getString(SqliteReviewTelemetryPayloadKeys.SEVERITY),
  confidence = getString(SqliteReviewTelemetryPayloadKeys.CONFIDENCE),
  location = getString(SqliteReviewTelemetryPayloadKeys.LOCATION),
  description = getString(SqliteReviewTelemetryPayloadKeys.DESCRIPTION),
  claimVerdict = getString(ReviewFindingPayloadKeys.CLAIM_VERDICT)
    ?.trim()
    ?.takeIf(String::isNotBlank)
    ?.let(ReviewClaimVerdict::fromWire),
  scopeDisposition = getString(ReviewFindingPayloadKeys.SCOPE_DISPOSITION)?.trim()?.takeIf(String::isNotBlank)
    ?.let(ReviewScopeDisposition::fromWire),
  citations = ReviewFindingCitation.decodeList(getString(ReviewFindingPayloadKeys.CITATIONS)),
  severityAdjustment = numberedFindingAdjustment(
    getString("severity_adjustment_direction"),
    getString("severity_adjustment_justification"),
  ),
)

private fun numberedFindingAdjustment(direction: String?, justification: String?): ReviewSeverityAdjustment? {
  val parsedDirection = direction?.trim()?.takeIf(String::isNotBlank)?.let(ReviewSeverityAdjustmentDirection::fromWire)
    ?: return null
  val parsedJustification = justification?.trim()?.takeIf(String::isNotBlank) ?: return null
  return ReviewSeverityAdjustment(parsedDirection, parsedJustification)
}
