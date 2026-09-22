package skillbill.contracts.review

import skillbill.contracts.JsonPayloadContract

object GovernedReviewEvidenceContracts {
  const val READ_EVIDENCE: String = "read_evidence"
  const val REQUEST_EXPANSION: String = "request_expansion"
  val OPERATIONS: List<String> = listOf(READ_EVIDENCE, REQUEST_EXPANSION)
  const val SERVER_NAME: String = "skill-bill-review-evidence"
  const val SOCKET_ENV: String = "SKILL_BILL_REVIEW_EVIDENCE_SOCKET"
  const val TOKEN_ENV: String = "SKILL_BILL_REVIEW_EVIDENCE_TOKEN"
  const val LANE_ENV: String = "SKILL_BILL_REVIEW_EVIDENCE_LANE"
  const val REQUEST_BYTES: Int = 64 * 1024
  const val RESPONSE_FRAME_BYTES: Int = 2 * 1024 * 1024
}

class GovernedReviewWirePayload private constructor(
  private val delegate: Map<String, Any?>,
) : JsonPayloadContract {
  override fun toPayload(): Map<String, Any?> = LinkedHashMap(delegate)

  companion object {
    fun from(map: Map<String, Any?>): GovernedReviewWirePayload = GovernedReviewWirePayload(LinkedHashMap(map))
  }
}

class GovernedReviewToolSpecList private constructor(
  private val specs: List<GovernedReviewWirePayload>,
) : JsonPayloadContract {
  override fun toPayload(): Map<String, Any?> =
    linkedMapOf(
      GovernedReviewEvidencePayloadKeys.TOOLS to specs.map(GovernedReviewWirePayload::toPayload),
    )

  fun asToolPayloads(): List<Map<String, Any?>> = specs.map(GovernedReviewWirePayload::toPayload)

  companion object {
    fun from(specMaps: List<Map<String, Any?>>): GovernedReviewToolSpecList =
      GovernedReviewToolSpecList(specMaps.map(GovernedReviewWirePayload::from))
  }
}
