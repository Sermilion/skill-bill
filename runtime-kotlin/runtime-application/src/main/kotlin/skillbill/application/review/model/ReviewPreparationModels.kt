package skillbill.application.review.model

import skillbill.review.context.model.commit.ReviewAssignment
import skillbill.review.context.model.execution.SpecIntentProjection
import skillbill.review.context.model.hunk.ReviewBaselineUntrackedPolicy
import skillbill.review.context.model.hunk.ReviewDependencyAllowlist
import skillbill.review.context.model.hunk.ReviewRevision
import skillbill.review.context.model.packet.ReviewContextPacket
import java.nio.file.Path

data class ReviewPreparationRequest(
  val reviewId: String,
  val reviewRevision: ReviewRevision,
  val criteriaReferences: Map<String, List<String>> = emptyMap(),
  val dependencyAllowlist: ReviewDependencyAllowlist = ReviewDependencyAllowlist.EMPTY,
  val baselineUntrackedPolicy: ReviewBaselineUntrackedPolicy = ReviewBaselineUntrackedPolicy.EMPTY,
  val specIntentProjection: SpecIntentProjection? = null,
  val evidenceStorePath: String? = null,
  val repoRoot: Path? = null,
)

data class ReviewPreparationResult(
  val packet: ReviewContextPacket,
  val assignments: List<ReviewAssignment>,
  val packetEnvelope: ReviewContextEnvelope,
  val assignmentEnvelopes: List<ReviewContextEnvelope>,
)
