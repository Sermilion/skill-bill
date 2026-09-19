package skillbill.application.review.packet
import skillbill.application.review.parallel.core.code.review.bundled.review
import skillbill.application.review.parallel.core.code.review.evidence.entries
import skillbill.application.review.parallel.core.code.review.runner.assignment
import skillbill.application.review.parallel.core.code.review.runner.packet
import skillbill.application.review.parallel.core.review.assignment
import skillbill.application.review.parallel.core.review.packet
import skillbill.application.review.preparation.assignment
import skillbill.application.review.preparation.packet
import skillbill.application.review.review.commitSha
import skillbill.application.review.service.review
import skillbill.application.review.spec.packet
import skillbill.application.review.verification.packet
import skillbill.review.context.model.commit.ReviewCommitUnit
import skillbill.review.context.model.hunk.ReviewBaselineUntrackedPolicy
import skillbill.review.context.model.launch.GovernedReviewLaunch

internal fun String.normalizeLineEndings(): String = replace("\r\n", "\n")

internal fun ReviewBaselineUntrackedPolicy.toEnvelope() = linkedMapOf(
  "included_paths" to includedPaths.sorted(),
  "excluded_paths" to excludedPaths.sorted(),
)

internal fun GovernedReviewLaunch.assignedCommitUnits(): List<ReviewCommitUnit> {
  val unitsBySha = packet.commitUnits.associateBy { it.commitSha }
  return assignment.assignedBundle.entries.map { entry -> unitsBySha.getValue(entry.commitSha) }
}
