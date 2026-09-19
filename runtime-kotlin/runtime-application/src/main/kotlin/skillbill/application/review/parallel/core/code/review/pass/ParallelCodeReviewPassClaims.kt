package skillbill.application.review.parallel.core.code.review.pass
import skillbill.application.review.parallel.core.code.review.bundled.finding
import skillbill.application.review.parallel.core.code.review.bundled.review
import skillbill.application.review.parallel.core.code.review.end.finding
import skillbill.application.review.parallel.core.code.review.integration.finding
import skillbill.application.review.parallel.core.code.review.runner.existing
import skillbill.application.review.parallel.verification.existing
import skillbill.application.review.review.existing
import skillbill.application.review.review.incoming
import skillbill.application.review.service.review
import skillbill.application.review.spec.finding
import skillbill.application.review.verification.finding
import skillbill.review.model.ParallelReviewMergedFinding

internal fun unionReviewPassClaims(
  existing: List<ParallelReviewMergedFinding>,
  incoming: List<ParallelReviewMergedFinding>,
): List<ParallelReviewMergedFinding> {
  if (incoming.isEmpty()) return existing
  if (existing.isEmpty()) return incoming
  val known = existing.map { it.location to it.description }.toSet()
  val usedNumbers = existing.map { it.fNumber }.toMutableSet()
  val additions = incoming.filter { (it.location to it.description) !in known }.map { finding ->
    if (finding.fNumber in usedNumbers) {
      val next = nextPassClaimNumber(usedNumbers)
      usedNumbers += next
      finding.copy(fNumber = next)
    } else {
      usedNumbers += finding.fNumber
      finding
    }
  }
  return existing + additions
}

internal fun nextPassClaimNumber(used: Set<String>): String {
  var index = 1
  while ("F-%03d".format(index) in used) {
    index++
  }
  return "F-%03d".format(index)
}
