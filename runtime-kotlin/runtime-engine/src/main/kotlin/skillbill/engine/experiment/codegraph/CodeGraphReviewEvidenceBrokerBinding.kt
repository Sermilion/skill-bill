package skillbill.engine.experiment.codegraph

import skillbill.application.reviewevidence.model.ReviewDiffEvidence
import skillbill.ports.review.model.ReviewEvidenceBrokerBinding
import skillbill.ports.workflow.gitops.model.GoalSubtaskReviewInput
import skillbill.review.context.model.commit.ReviewAssignment
import skillbill.review.context.model.execution.ReviewLaneDecision
import skillbill.review.context.model.hunk.ReviewChangedHunk
import skillbill.review.context.model.hunk.ReviewContextBudgetPolicy
import skillbill.review.context.model.hunk.ReviewRevision
import java.nio.file.Path

private const val CODEGRAPH_REVIEW_HINTS_LANE: String = "codegraph-hints"
private const val PACKET_DIGEST_LENGTH: Int = 64

internal object CodeGraphReviewEvidenceBrokerBinding {
  fun fromReviewInput(repoRoot: Path, input: GoalSubtaskReviewInput): ReviewEvidenceBrokerBinding? {
    val hunks = runCatching { ReviewDiffEvidence.parse(input.reviewText).hunks }
      .getOrNull()
      ?.filter { it.path.isNotBlank() }
      .orEmpty()
    if (hunks.isEmpty()) return null
    val paths = hunks.map { it.path }.distinct()
    val assignment = ReviewAssignment(
      reviewId = "codegraph-review-hints",
      packetDigest = "c".repeat(PACKET_DIGEST_LENGTH),
      lane = CODEGRAPH_REVIEW_HINTS_LANE,
      baseRevision = input.reviewBaseSha,
      headRevision = input.currentHeadSha,
      assignedPaths = paths,
      assignedHunks = hunks.map(ReviewChangedHunk::hunkId),
      reviewRevision = ReviewRevision("codegraph-review-hints", 1),
      laneDecision = ReviewLaneDecision(
        lane = CODEGRAPH_REVIEW_HINTS_LANE,
        included = true,
        reason = "codegraph review hint lane",
        ownedPaths = paths,
        originLayerChains = listOf(listOf("kotlin")),
        owningPack = "kotlin",
        specialistSkillName = "bill-kotlin-code-review-security",
      ),
    )
    return ReviewEvidenceBrokerBinding(
      repoRoot = repoRoot,
      assignment = assignment,
      laneRubricId = CODEGRAPH_REVIEW_HINTS_LANE,
      projectedHunks = hunks,
      budget = ReviewContextBudgetPolicy(
        maxParentPacketBytes = 32_768,
        maxLaneLaunchBytes = 16_384,
        maxLaneEvidenceBytes = 32_768,
        maxEvidenceResultBytes = 8_192,
        maxLaneResultBytes = 16_384,
        maxAssignmentExpansions = 8,
      ),
    )
  }
}
