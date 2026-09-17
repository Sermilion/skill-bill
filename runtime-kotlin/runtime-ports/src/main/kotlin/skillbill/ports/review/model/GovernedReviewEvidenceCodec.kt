package skillbill.ports.review.model

import skillbill.contracts.JsonCodec
import skillbill.contracts.JsonPayloadContract
import skillbill.contracts.review.GovernedReviewEvidencePayloadKeys
import skillbill.contracts.review.GovernedReviewToolSpecList
import skillbill.contracts.review.GovernedReviewWirePayload
import skillbill.error.InvalidReviewContextSchemaError
import skillbill.review.context.model.ReviewEvidenceLimits
import skillbill.review.context.model.ReviewExpansionRecord
import skillbill.review.context.model.GovernedReviewJsonRpcArguments

object GovernedReviewEvidenceCodec {
  const val REQUEST_BYTES: Int = ReviewEvidenceLimits.REQUEST_BYTES
  const val RESPONSE_FRAME_BYTES: Int = ReviewEvidenceLimits.RESPONSE_FRAME_BYTES

  const val READ_EVIDENCE: String = "read_evidence"
  const val REQUEST_EXPANSION: String = "request_expansion"
  const val SERVER_NAME: String = "skill-bill-review-evidence"
  const val SOCKET_ENV: String = "SKILL_BILL_REVIEW_EVIDENCE_SOCKET"
  const val TOKEN_ENV: String = "SKILL_BILL_REVIEW_EVIDENCE_TOKEN"
  const val LANE_ENV: String = "SKILL_BILL_REVIEW_EVIDENCE_LANE"

  val OPERATIONS: List<String> = listOf(READ_EVIDENCE, REQUEST_EXPANSION)

  fun toolSpecList(): GovernedReviewToolSpecList =
    GovernedReviewToolSpecList.from(GovernedReviewEvidenceCodecWire.toolSpecs())

  fun discoveryRequest(arguments: GovernedReviewJsonRpcArguments): ReviewEvidenceDiscoveryRequest {
    requestMetadata(arguments)
    if (arguments.keys.any {
        it !in setOf(
          GovernedReviewEvidencePayloadKeys.OPERATION,
          GovernedReviewEvidencePayloadKeys.CURSOR,
          GovernedReviewEvidencePayloadKeys.PAGE_SIZE,
        )
      } || arguments[GovernedReviewEvidencePayloadKeys.OPERATION] != "discover"
    ) {
      throw InvalidReviewContextSchemaError("review-discovery", "Malformed discovery request.")
    }
    val cursor = discoveryCursor(arguments)
    val size = discoveryPageSize(arguments)
    return ReviewEvidenceDiscoveryRequest(cursor, size)
  }

  fun discoveryPagePayload(page: ReviewEvidenceDiscoveryPage): JsonPayloadContract = GovernedReviewWirePayload.from(
    linkedMapOf(
      GovernedReviewEvidencePayloadKeys.ASSIGNMENT_DIGEST to page.assignmentDigest,
      GovernedReviewEvidencePayloadKeys.NEXT_CURSOR to page.nextCursor,
      GovernedReviewEvidencePayloadKeys.ENTRIES to page.entries.map { entry ->
        linkedMapOf(
          GovernedReviewEvidencePayloadKeys.SELECTOR to entry.selector,
          GovernedReviewEvidencePayloadKeys.PATH to entry.path,
          GovernedReviewEvidencePayloadKeys.OWNERS to entry.owners.map { owner ->
            linkedMapOf(
              GovernedReviewEvidencePayloadKeys.LANE to owner.lane,
              GovernedReviewEvidencePayloadKeys.ASSIGNMENT_DIGEST to owner.assignmentDigest,
              GovernedReviewEvidencePayloadKeys.RUBRIC_ID to owner.rubricId,
              GovernedReviewEvidencePayloadKeys.UNIT_ID to owner.unitId,
            )
          },
        ).apply { entry.expansionId?.let { put(GovernedReviewEvidencePayloadKeys.EXPANSION_ID, it) } }
      },
    ),
  )

  fun readRequest(
    lane: String,
    arguments: GovernedReviewJsonRpcArguments,
    expansionById: (String) -> ReviewExpansionRecord?,
  ): ReviewEvidenceBatchRequest {
    requestMetadata(arguments)
    if (
      arguments.keys.any {
        it !in setOf(GovernedReviewEvidencePayloadKeys.OPERATION, GovernedReviewEvidencePayloadKeys.REQUESTS)
      } ||
      (
        GovernedReviewEvidencePayloadKeys.OPERATION in arguments &&
          arguments[GovernedReviewEvidencePayloadKeys.OPERATION] != "read"
        )
    ) {
      throw InvalidReviewContextSchemaError("review-evidence", "Malformed read operation.")
    }
    val rawRequests = evidenceReadItems(arguments)
    return ReviewEvidenceBatchRequest(
      lane = lane,
      requests = rawRequests.map { raw ->
        GovernedReviewEvidenceCodecWire.evidenceRequest(lane, raw, expansionById)
      },
    )
  }

  fun expansionRequest(lane: String, arguments: GovernedReviewJsonRpcArguments): ReviewExpansionAuthorizationRequest {
    requestMetadata(arguments)
    if (arguments.keys.any {
        it !in setOf(
          GovernedReviewEvidencePayloadKeys.LANE,
          GovernedReviewEvidencePayloadKeys.PATH,
          GovernedReviewEvidencePayloadKeys.REACHABILITY_REASON,
        )
      }
    ) {
      throw InvalidReviewContextSchemaError("review-expansion", "Unknown expansion request field.")
    }
    return ReviewExpansionAuthorizationRequest(
      lane = if (GovernedReviewEvidencePayloadKeys.LANE in arguments) {
        GovernedReviewEvidenceCodecWire.requiredString(arguments, GovernedReviewEvidencePayloadKeys.LANE)
      } else {
        lane
      },
      path = GovernedReviewEvidenceCodecWire.requiredString(arguments, GovernedReviewEvidencePayloadKeys.PATH),
      reachabilityReason = GovernedReviewEvidenceCodecWire.requiredString(
        arguments,
        GovernedReviewEvidencePayloadKeys.REACHABILITY_REASON,
      ),
    )
  }

  fun batchResultPayload(result: ReviewEvidenceBatchResult): JsonPayloadContract = GovernedReviewWirePayload.from(
    linkedMapOf(
      GovernedReviewEvidencePayloadKeys.DELIVERY_RECEIPT to result.deliveryReceipt,
      GovernedReviewEvidencePayloadKeys.RESULTS to
        result.results.map(GovernedReviewEvidenceCodecWire::resultPayload),
      GovernedReviewEvidencePayloadKeys.CUMULATIVE_BYTES to result.cumulativeBytes,
      GovernedReviewEvidencePayloadKeys.EXPANSIONS to
        result.expansions.map(GovernedReviewEvidenceCodecWire::expansionPayload),
      GovernedReviewEvidencePayloadKeys.TERMINAL_OUTCOME to
        result.terminalOutcome?.let(GovernedReviewEvidenceCodecWire::budgetPayload),
    ),
  )

  fun expansionRecordPayload(record: ReviewExpansionRecord): JsonPayloadContract =
    GovernedReviewWirePayload.from(GovernedReviewEvidenceCodecWire.expansionPayload(record))

  fun requestMetadataBytes(request: ReviewEvidenceBatchRequest): Int = JsonCodec.mapToJsonString(
    mapOf(
      GovernedReviewEvidencePayloadKeys.LANE to request.lane,
      GovernedReviewEvidencePayloadKeys.REQUESTS to request.requests.map { item ->
        mapOf(
          GovernedReviewEvidencePayloadKeys.LANE to item.lane,
          GovernedReviewEvidencePayloadKeys.PATH to item.path,
          GovernedReviewEvidencePayloadKeys.SELECTOR to item.selector,
          GovernedReviewEvidencePayloadKeys.REACHABILITY_REASON to item.reachabilityReason,
          "authorized_expansion" to item.authorizedExpansion?.let { expansionRecordPayload(it).toPayload() },
          "offset" to item.offset,
          "limit" to item.limit,
          "pagination_token" to item.paginationToken,
        )
      },
    ),
  ).toByteArray(Charsets.UTF_8).size

  private fun requestMetadata(arguments: Map<String, Any?>) {
    if (JsonCodec.mapToJsonString(arguments).toByteArray(Charsets.UTF_8).size > ReviewEvidenceLimits.REQUEST_BYTES) {
      throw InvalidReviewContextSchemaError("review-evidence", "Request metadata exceeds its byte limit.")
    }
  }
}
