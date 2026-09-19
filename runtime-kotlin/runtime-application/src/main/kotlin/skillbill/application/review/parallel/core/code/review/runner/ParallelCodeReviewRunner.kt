package skillbill.application.review.parallel.core.code.review.runner
import me.tatarka.inject.annotations.Inject
import skillbill.application.review.model.ParallelCodeReviewRequest
import skillbill.application.review.model.ParallelCodeReviewResult
import skillbill.application.review.model.ReviewWorkerKind
import skillbill.application.review.packet.packet
import skillbill.application.review.parallel.core.code.review.bundled.review
import skillbill.application.review.parallel.core.code.review.bundled.scope
import skillbill.application.review.parallel.core.code.review.claim.findings
import skillbill.application.review.parallel.core.code.review.end.expected
import skillbill.application.review.parallel.core.code.review.end.repoRoot
import skillbill.application.review.parallel.core.code.review.end.reviewRunId
import skillbill.application.review.parallel.core.code.review.end.runner
import skillbill.application.review.parallel.core.code.review.inline.findings
import skillbill.application.review.parallel.core.code.review.regression.repoRoot
import skillbill.application.review.parallel.core.code.review.regression.runner
import skillbill.application.review.parallel.core.code.review.spec.request
import skillbill.application.review.parallel.core.review.packet
import skillbill.application.review.parallel.core.review.scope
import skillbill.application.review.parallel.planning.budget
import skillbill.application.review.parallel.planning.completeEmptySuppliedDelta
import skillbill.application.review.parallel.planning.hasSuppliedDiff
import skillbill.application.review.parallel.planning.prepareInitialRun
import skillbill.application.review.parallel.planning.repoRoot
import skillbill.application.review.parallel.planning.request
import skillbill.application.review.parallel.planning.resolveDiff
import skillbill.application.review.parallel.planning.resolveReviewRevisions
import skillbill.application.review.parallel.planning.resolvedMode
import skillbill.application.review.parallel.planning.reviewRunId
import skillbill.application.review.parallel.planning.scope
import skillbill.application.review.parallel.planning.workerKind
import skillbill.application.review.parallel.verification.nonSuccess
import skillbill.application.review.parallel.verification.recordAdjudicationBoundary
import skillbill.application.review.parallel.verification.recordedFindingVerdicts
import skillbill.application.review.parallel.verification.reviewRunId
import skillbill.application.review.parallel.verification.runClaimVerification
import skillbill.application.review.parallel.verification.runSpecAdjudication
import skillbill.application.review.parallel.verification.verdicts
import skillbill.application.review.preparation.expected
import skillbill.application.review.preparation.packet
import skillbill.application.review.preparation.packetDigest
import skillbill.application.review.preparation.request
import skillbill.application.review.preparation.scope
import skillbill.application.review.review.budget
import skillbill.application.review.review.repoRoot
import skillbill.application.review.review.reviews
import skillbill.application.review.review.unitOfWork
import skillbill.application.review.review.verdicts
import skillbill.application.review.service.review
import skillbill.application.review.spec.budget
import skillbill.application.review.spec.citationDiagnostics
import skillbill.application.review.spec.findings
import skillbill.application.review.spec.packet
import skillbill.application.review.spec.repoRoot
import skillbill.application.review.spec.runner
import skillbill.application.review.verification.citationDiagnostics
import skillbill.application.review.verification.findings
import skillbill.application.review.verification.packet
import skillbill.application.review.verification.runner
import skillbill.application.review.verification.verdicts
import skillbill.application.reviewevidence.model.ParallelReviewScope
import skillbill.ports.review.model.ReviewAccountingRecord
import skillbill.ports.review.model.ReviewNativeAgentPreflightRequest
import skillbill.review.context.model.execution.ResolvedReviewExecutionMode
import skillbill.review.parallel.ParallelReviewMerger

@Inject
class ParallelCodeReviewRunner(
  composition: ParallelCodeReviewRunnerComposition,
) {
  private val planning = composition.planning
  private val laneLaunch = composition.laneLaunch
  private val resultAssembly = composition.resultAssembly
  private val verificationStages = composition.verificationStages
  private val runtimeOwnedPersistence = composition.runtimeOwnedPersistence
  private val nativeAgentPreflight = composition.nativeAgentPreflight

  fun run(originalRequest: ParallelCodeReviewRequest): ParallelCodeReviewResult {
    earlyEmptyDelta(originalRequest)?.let { return it }
    val initial = planning.prepareInitialRun(originalRequest)
    verifyNativeWorkers(initial)
    val outcomes = laneLaunch.runLanes(initial)
    resultAssembly.recordLaneDispositions(initial, outcomes)
    val integration = resultAssembly.runIntegrationPass(initial, outcomes)
    val coverage = resultAssembly.coverageReport(initial, outcomes, integration)
    val result = resultAssembly.parallelResult(
      ParallelResultArgs(
        agent1Id = initial.agent1Id,
        outcomes = outcomes,
        integration = integration,
        coverage = coverage,
        packet = initial.compiledLaunchRequests.firstOrNull()?.packet,
        budget = initial.budget,
        stageResume = resultAssembly.stageResumeReport(initial.request.reviewRunId),
      ),
    )
    resultAssembly.persistReviewPassClaims(
      initial.request.reviewRunId,
      result.mergeResult.findings,
      persistEmpty = true,
    )
    resultAssembly.recordReviewStageBoundary(
      initial.request.reviewRunId,
      integration,
      result.mergeResult.findings,
    )
    resultAssembly.recordMergedFindingLanes(initial.request.reviewRunId)
    val verificationOutcome = verificationStages.runClaimVerification(initial, result)
    val adjudicationOutcome = verificationStages.runSpecAdjudication(initial, result)
    val recordedVerdicts = verificationStages.recordedFindingVerdicts(
      initial.request.reviewRunId,
      verificationOutcome.verdicts + adjudicationOutcome.verdicts,
    )
    resultAssembly.emitReviewStageDegradations(
      initial.request.reviewRunId,
      outcomes,
      verificationOutcome.nonSuccess,
    )
    val prose = result.output
    val assembled = ParallelReviewMerger.withRecordedVerdicts(result.mergeResult, recordedVerdicts)
      .copy(formattedOutput = prose)
    persistAccounting(result)
    return result.copy(
      mergeResult = assembled,
      stageResume = resultAssembly.stageResumeReport(initial.request.reviewRunId),
      citationDiagnostics = result.citationDiagnostics +
        verificationOutcome.citationDiagnostics +
        adjudicationOutcome.citationDiagnostics,
    )
  }

  private fun persistAccounting(result: ParallelCodeReviewResult) {
    val summary = result.accountingSummary ?: return
    runtimeOwnedPersistence.requiredWrite(
      seam = "ParallelCodeReviewRunner.saveAccounting",
      expected = "runtime-owned review accounting",
    ) { unitOfWork ->
      unitOfWork.reviews.saveAccounting(
        ReviewAccountingRecord(summary.reviewId, summary.packetDigest, summary),
      )
    }
  }

  private fun earlyEmptyDelta(originalRequest: ParallelCodeReviewRequest): ParallelCodeReviewResult? {
    if (originalRequest.suppliedDiff != null && originalRequest.suppliedDiff.isBlank()) {
      return planning.completeEmptySuppliedDelta(originalRequest) { reviewRunId ->
        verificationStages.recordAdjudicationBoundary(reviewRunId)
      }
    }
    if (
      originalRequest.scope == ParallelReviewScope.WORKTREE_FROM_BASE &&
      !planning.hasSuppliedDiff(originalRequest)
    ) {
      val revisions = planning.resolveReviewRevisions(originalRequest)
      if (planning.resolveDiff(originalRequest, revisions).isBlank()) {
        return planning.completeEmptySuppliedDelta(originalRequest) { reviewRunId ->
          verificationStages.recordAdjudicationBoundary(reviewRunId)
        }
      }
    }
    return null
  }

  private fun verifyNativeWorkers(initial: ParallelCodeReviewInitialRun) {
    val nativeNames = initial.compiledLaunchRequests
      .filter { it.workerKind == ReviewWorkerKind.PROVIDER_NATIVE }
      .mapNotNull { it.logicalWorkerName }
    val logicalNames = buildList {
      addAll(nativeNames)
      if (initial.resolvedMode == ResolvedReviewExecutionMode.INLINE) {
        add(PARALLEL_REVIEW_INLINE_NATIVE_WORKER)
      }
    }.distinct()
    if (logicalNames.isEmpty()) return
    nativeAgentPreflight.verify(
      ReviewNativeAgentPreflightRequest(
        repoRoot = initial.request.repoRoot,
        agentIds = listOf(initial.agent1Id),
        logicalNames = logicalNames,
      ),
    )
  }
}
