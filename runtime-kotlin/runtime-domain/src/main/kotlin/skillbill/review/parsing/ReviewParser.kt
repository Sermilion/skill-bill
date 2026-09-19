package skillbill.review.parsing
import skillbill.contracts.SharedPayloadKeys
import skillbill.review.attribution.normalizeRoutedSkill
import skillbill.review.attribution.resolveExecutionMode
import skillbill.review.attribution.resolveReviewIssueCategory
import skillbill.review.attribution.text
import skillbill.review.attribution.value
import skillbill.review.finding.extractSpecialistReviews
import skillbill.review.finding.extractSummaryValue
import skillbill.review.finding.finding
import skillbill.review.finding.findings
import skillbill.review.finding.map
import skillbill.review.finding.parseReviewFindings
import skillbill.review.finding.requireMatch
import skillbill.review.finding.value
import skillbill.review.model.ImportedReview
import skillbill.review.model.ReviewExecutionMode
import skillbill.review.model.ReviewIssueCategory
import skillbill.review.parallel.finding
import skillbill.review.parallel.findings
import skillbill.review.review.review

object ReviewParser {
  fun parseReview(text: String): ImportedReview {
    val reviewRunId =
      requireMatch(
        reviewRunIdPattern,
        text,
        "Review output is missing 'Review run ID: <review-run-id>'.",
      )
    val reviewSessionId =
      requireMatch(
        reviewSessionIdPattern,
        text,
        "Review output is missing 'Review session ID: <review-session-id>'.",
      )
    val rawRoutedSkill = extractSummaryValue(text, "routed_skill")
    val specialistReviews = extractSpecialistReviews(text)
    return ImportedReview(
      reviewRunId = reviewRunId,
      reviewSessionId = reviewSessionId,
      rawText = text,
      routedSkill = rawRoutedSkill,
      detectedScope = extractSummaryValue(text, "detected_scope"),
      detectedStack = extractSummaryValue(text, "detected_stack"),
      executionMode = resolveExecutionMode(parseExecutionMode(text), specialistReviews),
      specialistReviews = specialistReviews,
      findings = parseReviewFindings(text).map { finding ->
        finding.copy(
          issueCategory =
          resolveReviewIssueCategory(
            explicitCategory = finding.issueCategory.takeUnless { it == ReviewIssueCategory.OTHER.wireValue },
            routedSkill = normalizeRoutedSkill(rawRoutedSkill),
            specialistReviews = specialistReviews,
            finding = finding,
          ),
        )
      },
    )
  }

  private fun parseExecutionMode(text: String): ReviewExecutionMode? {
    val reported = reportedExecutionModePattern.find(text)?.groups?.get(SharedPayloadKeys.VALUE)?.value?.trim()
      ?: return null
    return ReviewExecutionMode.fromWire(extractSummaryValue(text, "execution_mode"))
      ?: throw IllegalArgumentException(
        "Review output reported an unknown execution mode '$reported'. Allowed: inline, delegated.",
      )
  }
}
