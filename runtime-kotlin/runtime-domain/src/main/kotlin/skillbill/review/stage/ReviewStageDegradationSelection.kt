package skillbill.review.stage
import skillbill.review.attribution.value
import skillbill.review.finding.findings
import skillbill.review.finding.value
import skillbill.review.model.ReviewEvidenceBoundaryAccounting
import skillbill.review.model.ReviewFindingVerdict
import skillbill.review.model.ReviewPassClaimSnapshot
import skillbill.review.model.ReviewSpecProjectionReference
import skillbill.review.model.ReviewStage
import skillbill.review.model.ReviewStageBoundary
import skillbill.review.model.ReviewStageDegradationMeasurement
import skillbill.review.model.ReviewStageDegradationReason
import skillbill.review.model.ReviewStageDegradationSelectionRequest
import skillbill.review.model.ReviewStageReached
import skillbill.review.model.ReviewVerificationNonSuccess
import skillbill.review.parallel.entries
import skillbill.review.parallel.findings
import skillbill.review.parsing.reviewRunId
import skillbill.review.review.accounting
import skillbill.review.review.review

object ReviewStageDegradationSelection {
  private val workerFailureReasons: Map<String, ReviewStageDegradationReason> = mapOf(
    "agent process failed to spawn" to ReviewStageDegradationReason.WORKER_PROCESS_FAILED,
    "agent was interrupted" to ReviewStageDegradationReason.WORKER_PROCESS_FAILED,
    "agent exited with unknown status" to ReviewStageDegradationReason.WORKER_PROCESS_FAILED,
    "agent timed out" to ReviewStageDegradationReason.WORKER_TIMED_OUT,
    "agent output exceeded the retention cap before completion" to
      ReviewStageDegradationReason.WORKER_LAUNCH_BUDGET_EXCEEDED,
    "verification launch exceeded max_lane_launch_bytes" to
      ReviewStageDegradationReason.WORKER_LAUNCH_BUDGET_EXCEEDED,
    "adjudication launch exceeded max_lane_launch_bytes" to
      ReviewStageDegradationReason.WORKER_LAUNCH_BUDGET_EXCEEDED,
    "unparseable verification output" to ReviewStageDegradationReason.WORKER_OUTPUT_UNUSABLE,
    "unparseable adjudication output" to ReviewStageDegradationReason.WORKER_OUTPUT_UNUSABLE,
  )

  private val workerFailureReasonPrefixes: Map<String, ReviewStageDegradationReason> = mapOf(
    "agent exited with status " to ReviewStageDegradationReason.WORKER_PROCESS_FAILED,
    "unsupported agent:" to ReviewStageDegradationReason.WORKER_LAUNCH_OR_RETURN_FAILED,
  )

  fun workerFailureReason(rejectionReason: String?): ReviewStageDegradationReason? {
    val reason = rejectionReason ?: return null
    return workerFailureReasons[reason]
      ?: workerFailureReasonPrefixes.entries.firstOrNull { reason.startsWith(it.key) }?.value
  }

  fun select(request: ReviewStageDegradationSelectionRequest): List<ReviewStageDegradationMeasurement> {
    val byStage = request.boundaries.associateBy { it.stage }
    val specNone = request.spec?.absenceReason != null
    return buildList {
      specAbsence(request.reviewRunId, request.spec)?.let(::add)
      if (adjudicationSkipped(specNone, byStage)) {
        add(adjudicationSkip(request.reviewRunId, specNone))
      }
      workerFailure(request.reviewRunId, request.verdicts)?.let(::add)
      verificationNonSuccess(request.reviewRunId, request.verificationNonSuccess)?.let(::add)
      addAll(unreachedBoundaries(request.reviewRunId, specNone, byStage, request.claims))
      request.evidenceBoundaries.forEach { addAll(evidenceBoundaryRecords(request.reviewRunId, it)) }
    }
  }

  private fun specAbsence(
    reviewRunId: String,
    spec: ReviewSpecProjectionReference?,
  ): ReviewStageDegradationMeasurement? {
    val absenceReason = spec?.absenceReason ?: return null
    return ReviewStageDegradationMeasurement(
      reviewRunId = reviewRunId,
      seam = "review.spec_intent",
      expected = "resolved",
      actual = absenceReason,
      reason = ReviewStageDegradationReason.SPEC_CONTEXT_NONE,
    )
  }

  private fun adjudicationSkipped(specNone: Boolean, byStage: Map<ReviewStage, ReviewStageBoundary>): Boolean {
    val verificationReached = byStage[ReviewStage.VERIFICATION]?.reached == ReviewStageReached.REACHED
    val adjudicationReached = byStage[ReviewStage.ADJUDICATION]?.reached == ReviewStageReached.REACHED
    return specNone || (!verificationReached && !adjudicationReached)
  }

  private fun adjudicationSkip(reviewRunId: String, specNone: Boolean): ReviewStageDegradationMeasurement =
    ReviewStageDegradationMeasurement(
      reviewRunId = reviewRunId,
      seam = "review.adjudication",
      expected = "reached",
      actual = if (specNone) "skipped_spec_context_none" else "skipped",
      reason = ReviewStageDegradationReason.ADJUDICATION_SKIPPED,
    )

  private fun workerFailure(
    reviewRunId: String,
    verdicts: List<ReviewFindingVerdict>,
  ): ReviewStageDegradationMeasurement? {
    val failure = verdicts.firstNotNullOfOrNull { verdict ->
      workerFailureReason(verdict.rejectionReason)?.let { verdict to it }
    } ?: return null
    val (failedWorker, reason) = failure
    return ReviewStageDegradationMeasurement(
      reviewRunId = reviewRunId,
      seam = "review.${failedWorker.stage.wireValue}.worker",
      expected = "worker_returned",
      actual = reason.wireValue,
      reason = reason,
    )
  }

  private fun verificationNonSuccess(
    reviewRunId: String,
    nonSuccess: ReviewVerificationNonSuccess?,
  ): ReviewStageDegradationMeasurement? {
    val observed = nonSuccess ?: return null
    return ReviewStageDegradationMeasurement(
      reviewRunId = reviewRunId,
      seam = "review.${ReviewStage.VERIFICATION.wireValue}.worker",
      expected = "worker_returned",
      actual = observed.reason.wireValue,
      reason = observed.reason,
    )
  }

  private fun unreachedBoundaries(
    reviewRunId: String,
    specNone: Boolean,
    byStage: Map<ReviewStage, ReviewStageBoundary>,
    claims: ReviewPassClaimSnapshot?,
  ): List<ReviewStageDegradationMeasurement> {
    val verificationReached = byStage[ReviewStage.VERIFICATION]?.reached == ReviewStageReached.REACHED
    return ReviewStage.entries.mapNotNull { stage ->
      val boundary = byStage[stage]
      val unreached = boundary?.reached == ReviewStageReached.NOT_REACHED ||
        missingVerificationBoundary(stage, claims, boundary) ||
        missingAdjudicationBoundary(stage, verificationReached, specNone, boundary)
      if (!unreached) {
        null
      } else {
        ReviewStageDegradationMeasurement(
          reviewRunId = reviewRunId,
          seam = "review.${stage.wireValue}.boundary",
          expected = "reached",
          actual = boundary?.reached?.wireValue ?: "absent",
          reason = ReviewStageDegradationReason.STAGE_BOUNDARY_UNREACHED,
        )
      }
    }
  }

  private fun missingVerificationBoundary(
    stage: ReviewStage,
    claims: ReviewPassClaimSnapshot?,
    boundary: ReviewStageBoundary?,
  ): Boolean = stage == ReviewStage.VERIFICATION && !claims?.findings.isNullOrEmpty() && boundary == null

  private fun missingAdjudicationBoundary(
    stage: ReviewStage,
    verificationReached: Boolean,
    specNone: Boolean,
    boundary: ReviewStageBoundary?,
  ): Boolean = stage == ReviewStage.ADJUDICATION && verificationReached && !specNone && boundary == null

  private fun evidenceBoundaryRecords(
    reviewRunId: String,
    accounting: ReviewEvidenceBoundaryAccounting,
  ): List<ReviewStageDegradationMeasurement> = buildList {
    evidenceBoundaryUnboundRecord(reviewRunId, accounting)?.let(::add)
    evidenceBoundaryUnexercisedRecord(reviewRunId, accounting)?.let(::add)
    evidenceBoundaryRefusedRecord(reviewRunId, accounting)?.let(::add)
    evidenceBoundaryRejectedRecord(reviewRunId, accounting)?.let(::add)
  }
}
