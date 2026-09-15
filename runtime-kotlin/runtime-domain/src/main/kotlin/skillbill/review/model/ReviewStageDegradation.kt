package skillbill.review.model

enum class ReviewStageDegradationReason(val wireValue: String) {
  SPEC_CONTEXT_NONE("spec_context_none"),
  ADJUDICATION_SKIPPED("adjudication_skipped"),
  // SKILL-236: one bucket could not tell a worker that never started from one that produced output
  // nobody could read, so every worker failure looked like the same defect. Each cause routes to a
  // different fix, so each gets its own token.
  WORKER_PROCESS_FAILED("worker_process_failed"),
  WORKER_TIMED_OUT("worker_timed_out"),
  WORKER_OUTPUT_UNUSABLE("worker_output_unusable"),
  WORKER_LAUNCH_BUDGET_EXCEEDED("worker_launch_budget_exceeded"),
  WORKER_LAUNCH_OR_RETURN_FAILED("worker_launch_or_return_failed"),
  STAGE_BOUNDARY_UNREACHED("stage_boundary_unreached"),
  EVIDENCE_BOUNDARY_UNBOUND_BROKER("evidence_boundary_unbound_broker"),
  EVIDENCE_BOUNDARY_UNEXERCISED("evidence_boundary_unexercised"),
  EVIDENCE_BOUNDARY_OPERATION_REFUSED("evidence_boundary_operation_refused"),
  REGISTER_CANDIDATES_REJECTED("register_candidates_rejected"),
  ACCOUNTING_CONTRACT_QUARANTINED("accounting_contract_quarantined"),
}

data class ReviewEvidenceBoundaryAccounting(
  val governedLaunchCount: Int = 0,
  val authorizedReadCount: Int = 0,
  val refusedOperationCount: Int = 0,
  val refusedCategories: List<String> = emptyList(),
  val evidenceBytes: Long = 0,
  val expansionCount: Int = 0,
  val rejectedCandidateCount: Int = 0,
  val unboundSeam: String? = null,
) {
  init {
    require(governedLaunchCount >= 0 && authorizedReadCount >= 0 && refusedOperationCount >= 0)
    require(refusedCategories.none(String::isBlank)) { "A refused-operation category must not be blank." }
    require(evidenceBytes >= 0 && expansionCount >= 0 && rejectedCandidateCount >= 0)
    unboundSeam?.let { require(it.isNotBlank()) { "Review evidence unbound seam must not be blank." } }
  }

  companion object {
    const val GOVERNED_EVIDENCE_SEAM: String = "review.evidence.broker"
    val NONE: ReviewEvidenceBoundaryAccounting = ReviewEvidenceBoundaryAccounting()
  }
}

data class ReviewStageDegradationMeasurement(
  val reviewRunId: String,
  val seam: String,
  val expected: String,
  val actual: String,
  val reason: ReviewStageDegradationReason,
) {
  init {
    require(reviewRunId.isNotBlank()) { "Review stage degradation review_run_id must not be blank." }
    require(seam.isNotBlank()) { "Review stage degradation seam must not be blank." }
    require(expected.isNotBlank()) { "Review stage degradation expected must not be blank." }
    require(actual.isNotBlank()) { "Review stage degradation actual must not be blank." }
  }
}

const val REVIEW_STAGE_DEGRADATION_EVENT_NAME: String = "skillbill_review_stage_degradation"
const val REVIEW_STAGE_DEGRADATION_CONTRACT_VERSION: String = "1.11.0"
const val REVIEW_FINISHED_LEGACY_CONTRACT_VERSION: String = "1.8.0"
const val REVIEW_FINISHED_LEGACY_REGENERATED_EVENT_NAME: String = "skillbill_review_finished_legacy_regenerated"
