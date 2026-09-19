package skillbill.review.context.model.accounting
import skillbill.contracts.review.ReviewAccountingPayloadKeys
import skillbill.review.context.model.commit.commitCount
import skillbill.review.context.model.commit.expansions
import skillbill.review.context.model.commit.focusedPairCount
import skillbill.review.context.model.commit.lane
import skillbill.review.context.model.commit.lanes
import skillbill.review.context.model.commit.packetDigest
import skillbill.review.context.model.commit.reviewId
import skillbill.review.context.model.commit.routingDigest
import skillbill.review.context.model.execution.findingCount
import skillbill.review.context.model.execution.lane
import skillbill.review.context.model.hunk.assignmentDigest
import skillbill.review.context.model.hunk.lane
import skillbill.review.context.model.hunk.packetDigest
import skillbill.review.context.model.launch.assignmentDigest
import skillbill.review.context.model.launch.commitSequenceDigest
import skillbill.review.context.model.launch.findingCount
import skillbill.review.context.model.launch.incompleteLanes
import skillbill.review.context.model.launch.lane
import skillbill.review.context.model.launch.unreviewedSegmentIds
import skillbill.review.context.model.launch.wireValue
import skillbill.review.context.model.packet.ReviewLaneSegmentAccounting
import skillbill.review.context.model.packet.accounting
import skillbill.review.context.model.packet.assignmentDigest
import skillbill.review.context.model.packet.bundleCompositionDigest
import skillbill.review.context.model.packet.commitSequenceDigest
import skillbill.review.context.model.packet.compositionDigest
import skillbill.review.context.model.packet.entryCount
import skillbill.review.context.model.packet.lane
import skillbill.review.context.model.packet.measuredBytes
import skillbill.review.context.model.packet.reviewId
import skillbill.review.context.model.packet.segmentId
import skillbill.review.context.model.packet.segments
import skillbill.review.context.model.packet.unreviewedSegmentIds
import skillbill.review.context.model.packet.wireValue
import skillbill.review.context.model.review.lane

fun ReviewAccountingSummary.toBoundedPayload(): Map<String, Any?> = linkedMapOf(
  ReviewAccountingPayloadKeys.CONTRACT_VERSION to contractVersion,
  ReviewAccountingPayloadKeys.KIND to ReviewAccountingPayloadKeys.ACCOUNTING_SUMMARY_KIND,
  ReviewAccountingPayloadKeys.REVIEW_ID to reviewId,
  ReviewAccountingPayloadKeys.PACKET_DIGEST to packetDigest,
  ReviewAccountingPayloadKeys.PARENT to parent.toPayload(),
  ReviewAccountingPayloadKeys.LANES to lanes.map(ReviewAccountingNode::toPayload),
  ReviewAccountingPayloadKeys.COMMIT_ROUTING_ACCOUNTING to commitRouting?.toPayload(),
  ReviewAccountingPayloadKeys.PARENT_ANALYSIS_CONSUMPTION to parentAnalysis?.toPayload(),
  ReviewAccountingPayloadKeys.INTEGRATION to integration?.toPayload(),
  ReviewAccountingPayloadKeys.AGGREGATE_COUNTERS to aggregateCounters.toPayload(),
)

private fun ReviewAccountingNode.toPayload(): Map<String, Any?> = linkedMapOf(
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
