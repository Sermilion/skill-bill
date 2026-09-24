package skillbill.infrastructure.sqlite.review.accounting

import skillbill.contracts.JsonCodec
import skillbill.contracts.review.ReviewAccountingPayloadKeys
import skillbill.review.context.model.accounting.ReviewAccountingCounters
import skillbill.review.context.model.accounting.ReviewAccountingNode
import skillbill.review.context.model.accounting.ReviewAccountingSummary
import skillbill.review.context.model.accounting.ReviewAccountingTerminalOutcome
import skillbill.review.context.model.accounting.ReviewCommitRoutingAccounting
import skillbill.review.context.model.accounting.ReviewIntegrationAccounting
import skillbill.review.context.model.accounting.ReviewParentAnalysisConsumption
import skillbill.review.context.model.packet.ReviewLaneSegmentAccounting

internal fun decodeReviewAccountingSummary(payload: Map<String, Any?>): ReviewAccountingSummary =
  ReviewAccountingSummary(
    reviewId = requiredString(payload, ReviewAccountingPayloadKeys.REVIEW_ID),
    packetDigest = requiredString(payload, ReviewAccountingPayloadKeys.PACKET_DIGEST),
    parent = decodeAccountingNode(requiredMap(payload, ReviewAccountingPayloadKeys.PARENT)),
    lanes = requiredList(payload, ReviewAccountingPayloadKeys.LANES).map { decodeAccountingNode(it) },
    aggregateCounters = decodeCounters(requiredMap(payload, ReviewAccountingPayloadKeys.AGGREGATE_COUNTERS)),
    commitRouting =
      optionalMap(payload, ReviewAccountingPayloadKeys.COMMIT_ROUTING_ACCOUNTING)
        ?.let(::decodeCommitRoutingAccounting),
    parentAnalysis =
      optionalMap(payload, ReviewAccountingPayloadKeys.PARENT_ANALYSIS_CONSUMPTION)
        ?.let(::decodeParentAnalysisConsumption),
    integration = optionalMap(payload, ReviewAccountingPayloadKeys.INTEGRATION)?.let(::decodeIntegrationAccounting),
    contractVersion = requiredString(payload, ReviewAccountingPayloadKeys.CONTRACT_VERSION),
  )

private fun decodeAccountingNode(node: Map<String, Any?>): ReviewAccountingNode {
  val counters =
    ReviewAccountingCounters(
      launchBytes = requiredLong(node, ReviewAccountingPayloadKeys.LAUNCH_BYTES),
      evidenceBytes = requiredLong(node, ReviewAccountingPayloadKeys.EVIDENCE_BYTES),
      resultBytes = requiredLong(node, ReviewAccountingPayloadKeys.RESULT_BYTES),
      expansions = requiredInt(node, ReviewAccountingPayloadKeys.EXPANSIONS),
      toolCalls = requiredInt(node, ReviewAccountingPayloadKeys.TOOL_CALLS),
      modelTurns = requiredInt(node, ReviewAccountingPayloadKeys.MODEL_TURNS),
    )
  val terminalOutcome =
    requireNotNull(
      ReviewAccountingTerminalOutcome.fromWire(requiredString(node, ReviewAccountingPayloadKeys.TERMINAL_OUTCOME)),
    ) { "Unknown accounting terminal outcome." }
  return ReviewAccountingNode(
    lane = requiredString(node, ReviewAccountingPayloadKeys.LANE),
    assignmentDigest = requiredString(node, ReviewAccountingPayloadKeys.ASSIGNMENT_DIGEST),
    counters = counters,
    inclusiveCounters = decodeCounters(requiredMap(node, ReviewAccountingPayloadKeys.INCLUSIVE_COUNTERS)),
    terminalOutcome = terminalOutcome,
    bundleCompositionDigest = optionalString(node, ReviewAccountingPayloadKeys.BUNDLE_COMPOSITION_DIGEST),
    segmentAccounting =
      optionalList(node, ReviewAccountingPayloadKeys.SEGMENT_ACCOUNTING)
        ?.map(::decodeLaneSegmentAccounting)
        ?: emptyList(),
    unreviewedSegmentIds = optionalStringList(node, ReviewAccountingPayloadKeys.UNREVIEWED_SEGMENT_IDS),
    children = emptyList(),
    evidenceDelivery = null,
  )
}

private fun decodeCounters(map: Map<String, Any?>): ReviewAccountingCounters =
  ReviewAccountingCounters(
    launchBytes = requiredLong(map, ReviewAccountingPayloadKeys.LAUNCH_BYTES),
    evidenceBytes = requiredLong(map, ReviewAccountingPayloadKeys.EVIDENCE_BYTES),
    resultBytes = requiredLong(map, ReviewAccountingPayloadKeys.RESULT_BYTES),
    expansions = requiredInt(map, ReviewAccountingPayloadKeys.EXPANSIONS),
    toolCalls = requiredInt(map, ReviewAccountingPayloadKeys.TOOL_CALLS),
    modelTurns = requiredInt(map, ReviewAccountingPayloadKeys.MODEL_TURNS),
  )

private fun decodeCommitRoutingAccounting(map: Map<String, Any?>): ReviewCommitRoutingAccounting =
  ReviewCommitRoutingAccounting(
    commitSequenceDigest = requiredString(map, ReviewAccountingPayloadKeys.COMMIT_SEQUENCE_DIGEST),
    routingDigest = requiredString(map, ReviewAccountingPayloadKeys.ROUTING_DIGEST),
    commitCount = requiredInt(map, ReviewAccountingPayloadKeys.COMMIT_COUNT),
    laneCount = requiredInt(map, ReviewAccountingPayloadKeys.LANE_COUNT),
    focusedCommitCount = requiredInt(map, ReviewAccountingPayloadKeys.FOCUSED_COMMIT_COUNT),
    skippedCommitCount = requiredInt(map, ReviewAccountingPayloadKeys.SKIPPED_COMMIT_COUNT),
    focusedPairCount = requiredInt(map, ReviewAccountingPayloadKeys.FOCUSED_PAIR_COUNT),
    skippedPairCount = requiredInt(map, ReviewAccountingPayloadKeys.SKIPPED_PAIR_COUNT),
    incompleteLanes = optionalStringList(map, ReviewAccountingPayloadKeys.INCOMPLETE_LANES),
  )

private fun decodeParentAnalysisConsumption(map: Map<String, Any?>): ReviewParentAnalysisConsumption =
  ReviewParentAnalysisConsumption(
    analyzedPairs = requiredInt(map, ReviewAccountingPayloadKeys.ANALYZED_PAIRS),
    analyzedBytes = requiredLong(map, ReviewAccountingPayloadKeys.ANALYZED_BYTES),
    maxAnalysisPairs = requiredInt(map, ReviewAccountingPayloadKeys.MAX_ANALYSIS_PAIRS),
    maxAnalysisBytes = requiredLong(map, ReviewAccountingPayloadKeys.MAX_ANALYSIS_BYTES),
  )

private fun decodeIntegrationAccounting(map: Map<String, Any?>): ReviewIntegrationAccounting =
  ReviewIntegrationAccounting(
    commitSequenceDigest = requiredString(map, ReviewAccountingPayloadKeys.COMMIT_SEQUENCE_DIGEST),
    terminalOutcome = requiredString(map, ReviewAccountingPayloadKeys.TERMINAL_OUTCOME),
    summarizedLaneCount = requiredInt(map, ReviewAccountingPayloadKeys.SUMMARIZED_LANE_COUNT),
    findingCount = requiredInt(map, ReviewAccountingPayloadKeys.FINDING_COUNT),
    counters = decodeCounters(requiredMap(map, ReviewAccountingPayloadKeys.COUNTERS)),
    skipReason = optionalString(map, ReviewAccountingPayloadKeys.SKIP_REASON),
  )

private fun decodeLaneSegmentAccounting(map: Map<String, Any?>): ReviewLaneSegmentAccounting =
  ReviewLaneSegmentAccounting(
    segmentId = requiredString(map, ReviewAccountingPayloadKeys.SEGMENT_ID),
    measuredBytes = requiredLong(map, ReviewAccountingPayloadKeys.MEASURED_BYTES),
    entryCount = requiredInt(map, ReviewAccountingPayloadKeys.ENTRY_COUNT),
    compositionDigest = requiredString(map, ReviewAccountingPayloadKeys.COMPOSITION_DIGEST),
  )

private fun requiredMap(
  payload: Map<String, Any?>,
  key: String,
): Map<String, Any?> =
  JsonCodec.anyToStringAnyMap(payload[key])
    ?: error("Review accounting field '$key' must be an object.")

private fun optionalMap(
  payload: Map<String, Any?>,
  key: String,
): Map<String, Any?>? = JsonCodec.anyToStringAnyMap(payload[key])

private fun requiredList(
  payload: Map<String, Any?>,
  key: String,
): List<Map<String, Any?>> =
  JsonCodec.anyToStringAnyMapList(payload[key])
    ?: error("Review accounting field '$key' must be a list of objects.")

private fun optionalList(
  payload: Map<String, Any?>,
  key: String,
): List<Map<String, Any?>>? = JsonCodec.anyToStringAnyMapList(payload[key])

private fun requiredString(
  payload: Map<String, Any?>,
  key: String,
): String =
  (payload[key] as? String)?.takeIf(String::isNotBlank)
    ?: error("Review accounting field '$key' must be a non-blank string.")

private fun optionalString(
  payload: Map<String, Any?>,
  key: String,
): String? = (payload[key] as? String)?.takeIf(String::isNotBlank)

private fun optionalStringList(
  payload: Map<String, Any?>,
  key: String,
): List<String> {
  val raw = payload[key] as? List<*> ?: return emptyList()
  return raw.map { item ->
    (item as? String)?.takeIf(String::isNotBlank)
      ?: error("Review accounting field '$key' entries must be non-blank strings.")
  }
}

private fun requiredLong(
  payload: Map<String, Any?>,
  key: String,
): Long = (payload[key] as? Number)?.toLong() ?: error("Review accounting field '$key' must be a number.")

private fun requiredInt(
  payload: Map<String, Any?>,
  key: String,
): Int {
  val value = requiredLong(payload, key)
  require(value in 0..Int.MAX_VALUE.toLong()) { "Review accounting field '$key' must fit in Int." }
  return value.toInt()
}
