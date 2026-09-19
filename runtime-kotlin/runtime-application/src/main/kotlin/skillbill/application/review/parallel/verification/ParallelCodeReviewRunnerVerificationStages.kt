package skillbill.application.review.parallel.verification
import skillbill.application.review.model.ParallelCodeReviewResult
import skillbill.application.review.model.ParallelReviewLaneStatus
import skillbill.application.review.model.ReviewClaimVerificationOutcome
import skillbill.application.review.model.ReviewClaimVerificationRunRequest
import skillbill.application.review.model.ReviewSpecAdjudicationOutcome
import skillbill.application.review.model.ReviewSpecAdjudicationRunRequest
import skillbill.application.review.packet.launch
import skillbill.application.review.packet.packet
import skillbill.application.review.parallel.core.code.review.bundled.review
import skillbill.application.review.parallel.core.code.review.claim.findings
import skillbill.application.review.parallel.core.code.review.claim.result
import skillbill.application.review.parallel.core.code.review.end.expected
import skillbill.application.review.parallel.core.code.review.end.result
import skillbill.application.review.parallel.core.code.review.end.run
import skillbill.application.review.parallel.core.code.review.evidence.result
import skillbill.application.review.parallel.core.code.review.inline.failureReason
import skillbill.application.review.parallel.core.code.review.inline.findings
import skillbill.application.review.parallel.core.code.review.inline.run
import skillbill.application.review.parallel.core.code.review.integration.result
import skillbill.application.review.parallel.core.code.review.regression.result
import skillbill.application.review.parallel.core.code.review.runner.ParallelCodeReviewInitialRun
import skillbill.application.review.parallel.core.code.review.runner.all
import skillbill.application.review.parallel.core.code.review.runner.citationDiagnostics
import skillbill.application.review.parallel.core.code.review.runner.compiledLaunchRequests
import skillbill.application.review.parallel.core.code.review.runner.delegatedStageLaunch
import skillbill.application.review.parallel.core.code.review.runner.findings
import skillbill.application.review.parallel.core.code.review.runner.initial
import skillbill.application.review.parallel.core.code.review.runner.lane1
import skillbill.application.review.parallel.core.code.review.runner.launch
import skillbill.application.review.parallel.core.code.review.runner.packet
import skillbill.application.review.parallel.core.code.review.runner.request
import skillbill.application.review.parallel.core.code.review.runner.resolvedMode
import skillbill.application.review.parallel.core.code.review.runner.result
import skillbill.application.review.parallel.core.code.review.runner.run
import skillbill.application.review.parallel.core.code.review.runner.seam
import skillbill.application.review.parallel.core.code.review.runner.specIntentResolution
import skillbill.application.review.parallel.core.code.review.spec.request
import skillbill.application.review.parallel.core.code.review.standalone.result
import skillbill.application.review.parallel.core.review.launch
import skillbill.application.review.parallel.core.review.packet
import skillbill.application.review.parallel.core.review.run
import skillbill.application.review.parallel.planning.input
import skillbill.application.review.parallel.planning.request
import skillbill.application.review.parallel.planning.resolvedMode
import skillbill.application.review.parallel.planning.result
import skillbill.application.review.parallel.planning.specIntentResolution
import skillbill.application.review.preparation.expected
import skillbill.application.review.preparation.launch
import skillbill.application.review.preparation.packet
import skillbill.application.review.preparation.reached
import skillbill.application.review.preparation.request
import skillbill.application.review.preparation.result
import skillbill.application.review.review.result
import skillbill.application.review.review.reviews
import skillbill.application.review.review.unitOfWork
import skillbill.application.review.service.review
import skillbill.application.review.spec.ReviewSpecAdjudicationRunner
import skillbill.application.review.spec.SPEC_CONTEXT_NONE
import skillbill.application.review.spec.citationDiagnostics
import skillbill.application.review.spec.existingVerdicts
import skillbill.application.review.spec.findings
import skillbill.application.review.spec.launch
import skillbill.application.review.spec.packet
import skillbill.application.review.spec.reason
import skillbill.application.review.spec.recordedAt
import skillbill.application.review.spec.run
import skillbill.application.review.stats.result
import skillbill.application.review.verification.ReviewClaimVerificationRunner
import skillbill.application.review.verification.citationDiagnostics
import skillbill.application.review.verification.findings
import skillbill.application.review.verification.launch
import skillbill.application.review.verification.mode
import skillbill.application.review.verification.packet
import skillbill.application.review.verification.recordedAt
import skillbill.application.review.verification.reviewOutput
import skillbill.application.review.verification.reviewOutputNeedsProseVerification
import skillbill.application.review.verification.run
import skillbill.application.review.verification.verificationReviewOutput
import skillbill.application.runtimepersistence.RuntimeOwnedPersistenceBoundary
import skillbill.contracts.review.REVIEW_CONTEXT_CONTRACT_VERSION
import skillbill.ports.goalrunner.runner.GoalRunnerSubtaskLauncher
import skillbill.review.context.ReviewContextEnvelopeValidator
import skillbill.review.context.model.execution.SpecIntentResolution
import skillbill.review.context.model.packet.ReviewLaneReviewDisposition
import skillbill.review.model.ParallelReviewMergedFinding
import skillbill.review.model.ReviewFindingVerdict
import skillbill.review.model.ReviewStage
import skillbill.review.model.ReviewStageBoundary
import skillbill.review.model.ReviewStageDegradationReason
import skillbill.review.model.ReviewStageReached
import skillbill.review.model.ReviewVerificationNonSuccess
import java.time.Clock

class ParallelCodeReviewRunnerVerificationStages(
  val parentReviewLauncher: GoalRunnerSubtaskLauncher,
  val reviewContextEnvelopeValidator: ReviewContextEnvelopeValidator,
  val runtimeOwnedPersistence: RuntimeOwnedPersistenceBoundary,
  val clock: Clock,
) {
  fun recordedFindingVerdicts(reviewRunId: String?, inMemory: List<ReviewFindingVerdict>): List<ReviewFindingVerdict> {
    if (reviewRunId == null) return inMemory
    return runtimeOwnedPersistence.requiredRead(
      seam = "ParallelCodeReviewRunner.recordedFindingVerdicts",
      expected = "runtime-owned finding verdicts",
    ) { unitOfWork -> unitOfWork.reviews.fetchFindingVerdicts(reviewRunId) }
  }

  internal fun runClaimVerification(
    initial: ParallelCodeReviewInitialRun,
    result: ParallelCodeReviewResult,
  ): ReviewClaimVerificationOutcome {
    val reviewRunId = initial.request.reviewRunId
    val boundaries = reviewStageBoundaries(reviewRunId)
    val claims = claimVerificationClaims(reviewRunId, boundaries, result.mergeResult.findings)
    val existing = reviewFindingVerdicts(reviewRunId)
    val verifiedRefs = existing
      .filter { it.stage == ReviewStage.VERIFICATION }
      .map { it.findingRef }
      .toSet()
    if (claims.isNotEmpty() && claims.all { it.fNumber in verifiedRefs }) {
      if (reviewRunId != null) recordVerificationBoundary(reviewRunId)
      return ReviewClaimVerificationOutcome(verdicts = existing)
    }
    val verificationInput = verificationReviewOutput(result.output, claims)
    if (claims.isEmpty()) {
      emptyClaimsVerificationShortCircuit(
        EmptyClaimsShortCircuitInput(
          reviewRunId = reviewRunId,
          boundaries = boundaries,
          verificationInput = verificationInput,
          existing = existing,
          lane = result.lane1,
        ),
      )?.let { return it }
    }
    val outcome = ReviewClaimVerificationRunner(parentReviewLauncher, reviewContextEnvelopeValidator, clock).run(
      ReviewClaimVerificationRunRequest(
        packet = initial.compiledLaunchRequests.firstOrNull()?.packet,
        reviewOutput = verificationInput,
        findings = claims,
        existingVerdicts = existing,
        mode = initial.resolvedMode,
        launch = initial.delegatedStageLaunch(),
      ),
    )
    val verdicts = persistClaimVerificationOutcome(
      PersistClaimVerificationInput(
        reviewRunId = reviewRunId,
        claims = claims,
        existing = existing,
        outcome = outcome,
        lane = result.lane1,
      ),
    )
    return ReviewClaimVerificationOutcome(
      verdicts = verdicts,
      output = outcome.output,
      skipReason = outcome.skipReason,
      citationDiagnostics = outcome.citationDiagnostics,
      nonSuccess = outcome.nonSuccess ?: withheldReviewPassNonSuccess(claims, outcome, result.lane1),
    )
  }

  internal fun runSpecAdjudication(
    initial: ParallelCodeReviewInitialRun,
    result: ParallelCodeReviewResult,
  ): ReviewSpecAdjudicationOutcome {
    val reviewRunId = initial.request.reviewRunId
    durableAdjudication(reviewRunId)?.let { return ReviewSpecAdjudicationOutcome(verdicts = it) }
    val projection = (initial.specIntentResolution as? SpecIntentResolution.Resolved)?.projection
    val claims = if (reviewRunId == null) {
      result.mergeResult.findings
    } else {
      runtimeOwnedPersistence.requiredRead(
        seam = "ParallelCodeReviewRunner.runSpecAdjudication.claims",
        expected = "runtime-owned review pass claims",
      ) { unitOfWork -> unitOfWork.reviews.fetchReviewPassClaims(reviewRunId) }
        ?.findings
        .orEmpty()
    }
    val existing = if (reviewRunId == null) {
      emptyList()
    } else {
      runtimeOwnedPersistence.requiredRead(
        seam = "ParallelCodeReviewRunner.runSpecAdjudication.verdicts",
        expected = "runtime-owned finding verdicts",
      ) { unitOfWork -> unitOfWork.reviews.fetchFindingVerdicts(reviewRunId) }
    }
    val outcome = ReviewSpecAdjudicationRunner(parentReviewLauncher, reviewContextEnvelopeValidator, clock).run(
      ReviewSpecAdjudicationRunRequest(
        packet = initial.compiledLaunchRequests.firstOrNull()?.packet,
        findings = claims,
        existingVerdicts = existing,
        projection = projection,
        launch = initial.delegatedStageLaunch(),
      ),
    )
    val verdicts = persistAdjudication(reviewRunId, outcome)
    return outcome.copy(verdicts = verdicts)
  }

  fun recordAdjudicationBoundary(reviewRunId: String) {
    runtimeOwnedPersistence.requiredWrite(
      seam = "ParallelCodeReviewRunner.recordAdjudicationBoundary",
      expected = "runtime-owned adjudication stage boundary",
    ) { unitOfWork ->
      unitOfWork.reviews.recordStageBoundary(
        reviewRunId,
        ReviewStageBoundary(
          stage = ReviewStage.ADJUDICATION,
          reached = ReviewStageReached.REACHED,
          recordedAt = clock.instant().toString(),
          contractVersion = REVIEW_CONTEXT_CONTRACT_VERSION,
        ),
      )
    }
  }
}

internal fun ParallelCodeReviewRunnerVerificationStages.reviewStageBoundaries(
  reviewRunId: String?,
): List<ReviewStageBoundary> = if (reviewRunId == null) {
  emptyList()
} else {
  runtimeOwnedPersistence.requiredRead(
    seam = "ParallelCodeReviewRunner.reviewStageBoundaries",
    expected = "runtime-owned review stage boundaries",
  ) { unitOfWork -> unitOfWork.reviews.fetchStageBoundaries(reviewRunId) }
}

internal fun ParallelCodeReviewRunnerVerificationStages.claimVerificationClaims(
  reviewRunId: String?,
  boundaries: List<ReviewStageBoundary>,
  mergedFindings: List<ParallelReviewMergedFinding>,
): List<ParallelReviewMergedFinding> = if (reviewRunId == null) {
  mergedFindings
} else {
  val reviewReached = boundaries.any {
    it.stage == ReviewStage.REVIEW && it.reached == ReviewStageReached.REACHED
  }
  if (!reviewReached) {
    emptyList()
  } else {
    runtimeOwnedPersistence.requiredRead(
      seam = "ParallelCodeReviewRunner.claimVerificationClaims",
      expected = "runtime-owned review pass claims",
    ) { unitOfWork -> unitOfWork.reviews.fetchReviewPassClaims(reviewRunId) }
      ?.findings
      .orEmpty()
  }
}

internal fun ParallelCodeReviewRunnerVerificationStages.reviewFindingVerdicts(
  reviewRunId: String?,
): List<ReviewFindingVerdict> = if (reviewRunId == null) {
  emptyList()
} else {
  runtimeOwnedPersistence.requiredRead(
    seam = "ParallelCodeReviewRunner.reviewFindingVerdicts",
    expected = "runtime-owned finding verdicts",
  ) { unitOfWork -> unitOfWork.reviews.fetchFindingVerdicts(reviewRunId) }
}

internal data class EmptyClaimsShortCircuitInput(
  val reviewRunId: String?,
  val boundaries: List<ReviewStageBoundary>,
  val verificationInput: String,
  val existing: List<ReviewFindingVerdict>,
  val lane: ParallelReviewLaneStatus,
)

internal data class PersistClaimVerificationInput(
  val reviewRunId: String?,
  val claims: List<ParallelReviewMergedFinding>,
  val existing: List<ReviewFindingVerdict>,
  val outcome: ReviewClaimVerificationOutcome,
  val lane: ParallelReviewLaneStatus,
)

internal fun reviewPassNonSuccess(lane: ParallelReviewLaneStatus): ReviewVerificationNonSuccess? {
  if (lane.success && lane.reviewDisposition == ReviewLaneReviewDisposition.COMPLETE) return null
  return ReviewVerificationNonSuccess(
    reason = ReviewStageDegradationReason.REVIEW_PASS_OUTPUT_ABSENT,
    detail = lane.failureReason?.takeIf(String::isNotBlank)
      ?: "the review pass returned ${lane.reviewDisposition.wireValue} output, so there is no disposition to verify",
  )
}

internal fun withheldReviewPassNonSuccess(
  claims: List<ParallelReviewMergedFinding>,
  outcome: ReviewClaimVerificationOutcome,
  lane: ParallelReviewLaneStatus,
): ReviewVerificationNonSuccess? {
  if (claims.isNotEmpty() || outcome.skipReason != null) return null
  return reviewPassNonSuccess(lane)
}

internal fun ParallelCodeReviewRunnerVerificationStages.emptyClaimsVerificationShortCircuit(
  input: EmptyClaimsShortCircuitInput,
): ReviewClaimVerificationOutcome? {
  val reviewRunId = input.reviewRunId
  if (
    reviewRunId != null &&
    input.boundaries.any { it.stage == ReviewStage.VERIFICATION && it.reached == ReviewStageReached.REACHED }
  ) {
    return ReviewClaimVerificationOutcome(verdicts = input.existing)
  }
  if (reviewOutputNeedsProseVerification(input.verificationInput)) return null
  val nonSuccess = reviewPassNonSuccess(input.lane)
  if (nonSuccess == null && reviewRunId != null) recordVerificationBoundary(reviewRunId)
  return ReviewClaimVerificationOutcome(
    verdicts = input.existing,
    skipReason = nonSuccess?.detail,
    nonSuccess = nonSuccess,
  )
}

internal fun ParallelCodeReviewRunnerVerificationStages.persistClaimVerificationOutcome(
  input: PersistClaimVerificationInput,
): List<ReviewFindingVerdict> {
  val reviewRunId = input.reviewRunId
  val outcome = input.outcome
  val claims = input.claims
  if (reviewRunId == null) return input.existing + outcome.verdicts
  if (outcome.verdicts.isNotEmpty()) {
    runtimeOwnedPersistence.requiredWrite(
      seam = "ParallelCodeReviewRunner.persistClaimVerificationOutcome",
      expected = "runtime-owned finding verification verdicts",
    ) { unitOfWork ->
      unitOfWork.reviews.recordFindingVerdicts(reviewRunId, outcome.verdicts)
    }
  }
  val recordedRefs = (input.existing + outcome.verdicts)
    .filter { it.stage == ReviewStage.VERIFICATION }
    .map { it.findingRef }
    .toSet()
  if (claims.isNotEmpty() && claims.all { it.fNumber in recordedRefs }) {
    recordVerificationBoundary(reviewRunId)
  } else if (claims.isEmpty() && outcome.skipReason == null && reviewPassNonSuccess(input.lane) == null) {
    recordVerificationBoundary(reviewRunId)
  }
  return input.existing + outcome.verdicts
}

internal fun ParallelCodeReviewRunnerVerificationStages.recordVerificationBoundary(reviewRunId: String) {
  runtimeOwnedPersistence.requiredWrite(
    seam = "ParallelCodeReviewRunner.recordVerificationBoundary",
    expected = "runtime-owned verification stage boundary",
  ) { unitOfWork ->
    unitOfWork.reviews.recordStageBoundary(
      reviewRunId,
      ReviewStageBoundary(
        stage = ReviewStage.VERIFICATION,
        reached = ReviewStageReached.REACHED,
        recordedAt = clock.instant().toString(),
        contractVersion = REVIEW_CONTEXT_CONTRACT_VERSION,
      ),
    )
  }
}

internal fun ParallelCodeReviewRunnerVerificationStages.durableAdjudication(
  reviewRunId: String?,
): List<ReviewFindingVerdict>? {
  if (reviewRunId == null) return null
  val boundaries = runtimeOwnedPersistence.requiredRead(
    seam = "ParallelCodeReviewRunner.durableAdjudication",
    expected = "runtime-owned adjudication stage boundaries",
  ) { unitOfWork ->
    unitOfWork.reviews.fetchStageBoundaries(reviewRunId)
  }
  val verificationReached = boundaries.any {
    it.stage == ReviewStage.VERIFICATION && it.reached == ReviewStageReached.REACHED
  }
  if (!verificationReached) return emptyList()
  val adjudicationReached = boundaries.any {
    it.stage == ReviewStage.ADJUDICATION && it.reached == ReviewStageReached.REACHED
  }
  if (!adjudicationReached) return null
  return runtimeOwnedPersistence.requiredRead(
    seam = "ParallelCodeReviewRunner.durableAdjudication.verdicts",
    expected = "runtime-owned finding verdicts",
  ) { unitOfWork -> unitOfWork.reviews.fetchFindingVerdicts(reviewRunId) }
}

internal fun ParallelCodeReviewRunnerVerificationStages.persistAdjudication(
  reviewRunId: String?,
  outcome: ReviewSpecAdjudicationOutcome,
): List<ReviewFindingVerdict> {
  if (reviewRunId == null) return outcome.verdicts
  if (outcome.skipReason == ReviewSpecAdjudicationRunner.SPEC_CONTEXT_NONE) return emptyList()
  if (outcome.verdicts.isNotEmpty()) {
    runtimeOwnedPersistence.requiredWrite(
      seam = "ParallelCodeReviewRunner.persistAdjudication",
      expected = "runtime-owned adjudication verdicts",
    ) { unitOfWork ->
      unitOfWork.reviews.recordFindingVerdicts(reviewRunId, outcome.verdicts)
    }
  }
  recordAdjudicationBoundary(reviewRunId)
  return outcome.verdicts
}
