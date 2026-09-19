package skillbill.application.review.verification
import skillbill.application.review.parallel.core.code.review.bundled.finding
import skillbill.application.review.parallel.core.code.review.bundled.review
import skillbill.application.review.parallel.core.code.review.claim.findings
import skillbill.application.review.parallel.core.code.review.end.finding
import skillbill.application.review.parallel.core.code.review.inline.findings
import skillbill.application.review.parallel.core.code.review.integration.finding
import skillbill.application.review.parallel.core.code.review.runner.claims
import skillbill.application.review.parallel.core.code.review.runner.findings
import skillbill.application.review.parallel.verification.claims
import skillbill.application.review.preparation.commits
import skillbill.application.review.review.commits
import skillbill.application.review.service.parse
import skillbill.application.review.service.review
import skillbill.application.review.spec.finding
import skillbill.application.review.spec.findings
import skillbill.review.model.ParallelReviewMergedFinding
import skillbill.review.parallel.ParallelReviewFindingParser

private val REVIEW_VERDICT_LINE_PATTERN =
  Regex("""(?m)^\s*verdict:\s*(approved|changes_requested|needs_fix)\s*$""", RegexOption.IGNORE_CASE)

internal fun verificationReviewOutput(reviewOutput: String, claims: List<ParallelReviewMergedFinding>): String {
  if (reviewOutput.isNotBlank() && !reviewOutput.startsWith("Review completed with no prose")) {
    return reviewOutput
  }
  return claims.joinToString("\n", transform = ::formatMergedFindingAsRegisterLine)
}

private fun formatMergedFindingAsRegisterLine(finding: ParallelReviewMergedFinding): String {
  val specialist = finding.specialistSkillNames.firstOrNull().orEmpty()
  val path = finding.repositoryPath ?: finding.location.substringBefore(':')
  val line = finding.line ?: finding.location.substringAfter(':', "").toIntOrNull() ?: 1
  val commitsSegment = finding.commitShas.takeIf { it.isNotEmpty() }
    ?.joinToString(",")
    ?.let { commits -> "commits=$commits | " }
    .orEmpty()
  return "- [${finding.fNumber}] ${finding.severity.displayName} | ${finding.confidence} | " +
    "specialist=$specialist | ${commitsSegment}path=\"$path\" | line=$line | ${finding.description}"
}

internal fun reviewOutputNeedsProseVerification(reviewOutput: String): Boolean {
  val trimmed = reviewOutput.trim()
  if (trimmed.isEmpty() || trimmed == "NO_FINDINGS") {
    return false
  }
  val parsed = ParallelReviewFindingParser.parse(reviewOutput)
  return parsed.findings.isEmpty() && (
    ParallelReviewFindingParser.countRegisterCandidates(reviewOutput) > 0 ||
      parsed.rejections.isNotEmpty() ||
      REVIEW_VERDICT_LINE_PATTERN.containsMatchIn(reviewOutput)
    )
}
