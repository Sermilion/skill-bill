package skillbill.ports.review.model

import skillbill.error.InvalidReviewContextSchemaError
import skillbill.review.context.model.ReviewAssignment
import skillbill.review.context.model.ReviewEvidenceLimits

const val REVIEW_EVIDENCE_BATCH_SIZE: Int = 32

data class ReviewEvidenceOwner(
  val lane: String,
  val assignmentDigest: String,
  val rubricId: String,
  val unitId: String,
) {
  init {
    listOf(lane, rubricId, unitId).forEach(ReviewEvidenceLimits::field)
    if (listOf(lane, rubricId, unitId).any(String::isBlank) || !assignmentDigest.matches(Regex("[a-f0-9]{64}"))) {
      throw InvalidReviewContextSchemaError(
        "review-evidence-owner",
        "Evidence ownership must retain complete provenance.",
      )
    }
  }
}

data class ReviewEvidenceSource(
  val assignment: ReviewAssignment,
  val rubricId: String,
  val namedDependencies: Set<String> = emptySet(),
  val coordinates: ReviewEvidenceCoordinates = ReviewEvidenceCoordinates.Committed(assignment.headRevision),
)
