package skillbill.contracts.review

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
