package skillbill.application.review.packet
import skillbill.application.review.parallel.core.code.review.bundled.review
import skillbill.application.review.parallel.core.code.review.end.lanes
import skillbill.application.review.parallel.core.code.review.evidence.entries
import skillbill.application.review.parallel.core.code.review.inline.completion
import skillbill.application.review.parallel.core.code.review.regression.lanes
import skillbill.application.review.parallel.core.code.review.runner.completion
import skillbill.application.review.parallel.core.code.review.runner.hunk
import skillbill.application.review.parallel.core.code.review.runner.lanes
import skillbill.application.review.parallel.core.code.review.runner.packet
import skillbill.application.review.parallel.core.review.completion
import skillbill.application.review.parallel.core.review.decisions
import skillbill.application.review.parallel.core.review.hunk
import skillbill.application.review.parallel.core.review.lanes
import skillbill.application.review.parallel.core.review.packet
import skillbill.application.review.parallel.core.review.segmentation
import skillbill.application.review.parallel.core.review.source
import skillbill.application.review.parallel.planning.baseRevision
import skillbill.application.review.parallel.planning.decisions
import skillbill.application.review.parallel.planning.headRevision
import skillbill.application.review.parallel.planning.lane
import skillbill.application.review.parallel.planning.lanes
import skillbill.application.review.parallel.verification.completion
import skillbill.application.review.parallel.verification.lane
import skillbill.application.review.parallel.verification.path
import skillbill.application.review.preparation.decisions
import skillbill.application.review.preparation.hunk
import skillbill.application.review.preparation.packet
import skillbill.application.review.review.commitSha
import skillbill.application.review.review.hunkId
import skillbill.application.review.review.lane
import skillbill.application.review.review.lanes
import skillbill.application.review.service.decisions
import skillbill.application.review.service.review
import skillbill.application.review.spec.hunk
import skillbill.application.review.spec.lane
import skillbill.application.review.spec.lanes
import skillbill.application.review.spec.packet
import skillbill.application.review.spec.path
import skillbill.application.review.spec.reason
import skillbill.application.review.stats.lane
import skillbill.application.review.stats.segments
import skillbill.application.review.verification.hunk
import skillbill.application.review.verification.lanes
import skillbill.application.review.verification.packet
import skillbill.application.review.verification.path
import skillbill.review.context.model.bundle.ReviewLaneBundle
import skillbill.review.context.model.commit.ReviewCommitCoverageFact
import skillbill.review.context.model.commit.ReviewCommitLaneDecision
import skillbill.review.context.model.commit.ReviewCommitLaneRoutingMatrix
import skillbill.review.context.model.commit.ReviewCommitUnit
import skillbill.review.context.model.packet.ReviewLaneAssembledBundle
import skillbill.review.context.model.packet.ReviewLaneAssembledEntry
import skillbill.review.context.model.packet.ReviewLaneBundleSegment
import skillbill.review.context.model.packet.ReviewLaneBundleSegmentation
import skillbill.review.context.model.packet.ReviewLaneCompletionState

internal fun ReviewCommitUnit.toEnvelope(): Map<String, Any?> = linkedMapOf(
  "commit_unit_id" to commitUnitId,
  "commit_sha" to commitSha,
  "parent_sha" to parentSha,
  "subject" to subject.normalizeLineEndings(),
  "order_index" to orderIndex,
  "source" to source.name.lowercase(),
  "hunk_ids" to hunkIds,
)

internal fun ReviewCommitUnit.toAssignedEnvelope(): Map<String, Any?> = linkedMapOf(
  "commit_unit_id" to commitUnitId,
  "commit_sha" to commitSha,
  "parent_sha" to parentSha,
  "subject" to subject.normalizeLineEndings(),
  "order_index" to orderIndex,
  "source" to source.name.lowercase(),
)

internal fun ReviewCommitCoverageFact.toEnvelope(): Map<String, Any?> = linkedMapOf(
  "base_revision" to baseRevision,
  "head_revision" to headRevision,
  "commit_count" to commitCount,
  "chain_verified" to chainVerified,
  "path_coverage_verified" to pathCoverageVerified,
  "degraded_reason" to degradedReason,
)

internal fun ReviewCommitLaneDecision.toEnvelope(): Map<String, Any?> = linkedMapOf(
  "commit_sha" to commitSha,
  "order_index" to orderIndex,
  "lane" to lane,
  "disposition" to disposition.name.lowercase(),
  "reason" to reason.normalizeLineEndings(),
  "signals" to signals.sorted(),
)

internal fun ReviewCommitLaneRoutingMatrix.toEnvelope(): Map<String, Any?> = linkedMapOf(
  "routing_digest" to routingDigest,
  "commit_shas" to commitShas,
  "lanes" to lanes,
  "decisions" to decisions.sortedWith(compareBy({ it.orderIndex }, { it.lane })).map { it.toEnvelope() },
)

internal fun ReviewLaneBundle.toEnvelope(): Map<String, Any?> = linkedMapOf(
  "bundle_digest" to bundleDigest,
  "entries" to entries.map {
    linkedMapOf("commit_sha" to it.commitSha, "order_index" to it.orderIndex, "hunk_ids" to it.hunkIds)
  },
)

internal fun ReviewLaneAssembledBundle.toLaunchEnvelope(
  segmentation: ReviewLaneBundleSegmentation,
  completion: ReviewLaneCompletionState,
): Map<String, Any?> = linkedMapOf<String, Any?>(
  "composition_digest" to compositionDigest,
  "lane_disposition" to completion.disposition.wireValue,
  "unreviewed_segment_ids" to completion.unreviewedSegmentIds,
  "entries" to segmentation.segments.flatMap { it.entries }.map { it.toEnvelope() },
  "segments" to segmentation.segments.map { it.toEnvelope() },
).apply {
  completion.budgetDimension?.let { put("budget_dimension", it) }
}

internal fun ReviewLaneAssembledEntry.toEnvelope(): Map<String, Any?> = linkedMapOf(
  "commit_sha" to commitSha,
  "parent_sha" to parentSha,
  "subject" to subject.normalizeLineEndings(),
  "order_index" to orderIndex,
  "hunk_id" to hunkId,
  "path" to hunk.path,
  "old_start" to hunk.oldStart,
  "old_count" to hunk.oldCount,
  "new_start" to hunk.newStart,
  "new_count" to hunk.newCount,
  "content_digest" to hunk.contentDigest,
  "evidence_locator" to hunk.evidenceLocator.toEnvelope(),
)

internal fun ReviewLaneBundleSegment.toEnvelope(): Map<String, Any?> = linkedMapOf(
  "segment_id" to segmentId,
  "measured_bytes" to measuredBytes,
  "composition_digest" to compositionDigest,
  "entries" to entries.map {
    linkedMapOf(
      "commit_sha" to it.commitSha,
      "order_index" to it.orderIndex,
      "hunk_id" to it.hunkId,
      "path" to it.hunk.path,
    )
  },
)
