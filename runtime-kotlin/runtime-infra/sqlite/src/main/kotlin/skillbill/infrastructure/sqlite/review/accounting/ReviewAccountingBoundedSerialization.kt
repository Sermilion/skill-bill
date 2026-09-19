package skillbill.infrastructure.sqlite.review.accounting
import skillbill.contracts.review.ReviewAccountingPayloadKeys
import skillbill.review.context.model.accounting.ReviewAccountingCounters
import skillbill.review.context.model.accounting.ReviewAccountingNode
import skillbill.review.context.model.accounting.ReviewAccountingSummary
import skillbill.review.context.model.accounting.ReviewCommitRoutingAccounting
import skillbill.review.context.model.accounting.ReviewIntegrationAccounting
import skillbill.review.context.model.accounting.ReviewParentAnalysisConsumption
import skillbill.review.context.model.packet.ReviewLaneSegmentAccounting

internal fun encodeReviewAccountingBoundedPayload(summary: ReviewAccountingSummary): Map<String, Any?> = linkedMapOf(
  ReviewAccountingPayloadKeys.CONTRACT_VERSION to summary.contractVersion,
  ReviewAccountingPayloadKeys.KIND to ReviewAccountingPayloadKeys.ACCOUNTING_SUMMARY_KIND,
  ReviewAccountingPayloadKeys.REVIEW_ID to summary.reviewId,
  ReviewAccountingPayloadKeys.PACKET_DIGEST to summary.packetDigest,
  ReviewAccountingPayloadKeys.PARENT to summary.parent.toPayload(),
  ReviewAccountingPayloadKeys.LANES to summary.lanes.map(ReviewAccountingNode::toPayload),
  ReviewAccountingPayloadKeys.COMMIT_ROUTING_ACCOUNTING to summary.commitRouting?.toPayload(),
  ReviewAccountingPayloadKeys.PARENT_ANALYSIS_CONSUMPTION to summary.parentAnalysis?.toPayload(),
  ReviewAccountingPayloadKeys.INTEGRATION to summary.integration?.toPayload(),
  ReviewAccountingPayloadKeys.AGGREGATE_COUNTERS to summary.aggregateCounters.toPayload(),
)

internal fun ReviewAccountingNode.toPayload(): Map<String, Any?> = linkedMapOf(
  ReviewAccountingPayloadKeys.LANE to lane,
  ReviewAccountingPayloadKeys.ASSIGNMENT_DIGEST to assignmentDigest,
  ReviewAccountingPayloadKeys.LAUNCH_BYTES to counters.launchBytes,
  ReviewAccountingPayloadKeys.EVIDENCE_BYTES to counters.evidenceBytes,
  ReviewAccountingPayloadKeys.RESULT_BYTES to counters.resultBytes,
  ReviewAccountingPayloadKeys.EXPANSIONS to counters.expansions.toLong(),
  ReviewAccountingPayloadKeys.TOOL_CALLS to counters.toolCalls.toLong(),
  ReviewAccountingPayloadKeys.MODEL_TURNS to counters.modelTurns.toLong(),
  ReviewAccountingPayloadKeys.INCLUSIVE_COUNTERS to inclusiveCounters.toPayload(),
  ReviewAccountingPayloadKeys.TERMINAL_OUTCOME to terminalOutcome.wireValue,
).apply {
  bundleCompositionDigest?.let { put(ReviewAccountingPayloadKeys.BUNDLE_COMPOSITION_DIGEST, it) }
  segmentAccounting.takeIf { it.isNotEmpty() }
    ?.let { segments -> put(ReviewAccountingPayloadKeys.SEGMENT_ACCOUNTING, segments.map { it.toPayload() }) }
  unreviewedSegmentIds.takeIf { it.isNotEmpty() }
    ?.let { put(ReviewAccountingPayloadKeys.UNREVIEWED_SEGMENT_IDS, it) }
}

private fun ReviewCommitRoutingAccounting.toPayload(): Map<String, Any?> = linkedMapOf(
  ReviewAccountingPayloadKeys.COMMIT_SEQUENCE_DIGEST to commitSequenceDigest,
  ReviewAccountingPayloadKeys.ROUTING_DIGEST to routingDigest,
  ReviewAccountingPayloadKeys.COMMIT_COUNT to commitCount,
  ReviewAccountingPayloadKeys.LANE_COUNT to laneCount,
  ReviewAccountingPayloadKeys.FOCUSED_COMMIT_COUNT to focusedCommitCount,
  ReviewAccountingPayloadKeys.SKIPPED_COMMIT_COUNT to skippedCommitCount,
  ReviewAccountingPayloadKeys.FOCUSED_PAIR_COUNT to focusedPairCount,
  ReviewAccountingPayloadKeys.SKIPPED_PAIR_COUNT to skippedPairCount,
  ReviewAccountingPayloadKeys.INCOMPLETE_LANES to incompleteLanes,
)

private fun ReviewParentAnalysisConsumption.toPayload(): Map<String, Any?> = linkedMapOf(
  ReviewAccountingPayloadKeys.ANALYZED_PAIRS to analyzedPairs,
  ReviewAccountingPayloadKeys.ANALYZED_BYTES to analyzedBytes,
  ReviewAccountingPayloadKeys.MAX_ANALYSIS_PAIRS to maxAnalysisPairs,
  ReviewAccountingPayloadKeys.MAX_ANALYSIS_BYTES to maxAnalysisBytes,
)

private fun ReviewIntegrationAccounting.toPayload(): Map<String, Any?> = linkedMapOf(
  ReviewAccountingPayloadKeys.COMMIT_SEQUENCE_DIGEST to commitSequenceDigest,
  ReviewAccountingPayloadKeys.TERMINAL_OUTCOME to terminalOutcome.wireValue,
  ReviewAccountingPayloadKeys.SUMMARIZED_LANE_COUNT to summarizedLaneCount,
  ReviewAccountingPayloadKeys.FINDING_COUNT to findingCount,
  ReviewAccountingPayloadKeys.COUNTERS to counters.toPayload(),
).apply {
  skipReason?.let { put(ReviewAccountingPayloadKeys.SKIP_REASON, it) }
}

private fun ReviewLaneSegmentAccounting.toPayload(): Map<String, Any?> = linkedMapOf(
  ReviewAccountingPayloadKeys.SEGMENT_ID to segmentId,
  ReviewAccountingPayloadKeys.MEASURED_BYTES to measuredBytes,
  ReviewAccountingPayloadKeys.ENTRY_COUNT to entryCount,
  ReviewAccountingPayloadKeys.COMPOSITION_DIGEST to compositionDigest,
)

private fun ReviewAccountingCounters.toPayload(): Map<String, Long> = linkedMapOf(
  ReviewAccountingPayloadKeys.LAUNCH_BYTES to launchBytes,
  ReviewAccountingPayloadKeys.EVIDENCE_BYTES to evidenceBytes,
  ReviewAccountingPayloadKeys.RESULT_BYTES to resultBytes,
  ReviewAccountingPayloadKeys.EXPANSIONS to expansions.toLong(),
  ReviewAccountingPayloadKeys.TOOL_CALLS to toolCalls.toLong(),
  ReviewAccountingPayloadKeys.MODEL_TURNS to modelTurns.toLong(),
)
