package skillbill.application.review.parallel.core.code.review.inline
import skillbill.application.review.model.ParallelCodeReviewRequest
import skillbill.application.review.packet.assignment
import skillbill.application.review.packet.launch
import skillbill.application.review.parallel.core.code.review.bundled.review
import skillbill.application.review.parallel.core.code.review.claim.prompt
import skillbill.application.review.parallel.core.code.review.end.repoRoot
import skillbill.application.review.parallel.core.code.review.integration.prompt
import skillbill.application.review.parallel.core.code.review.regression.repoRoot
import skillbill.application.review.parallel.core.code.review.runner.LaunchedBoundParentArgs
import skillbill.application.review.parallel.core.code.review.runner.PARALLEL_REVIEW_INLINE_NATIVE_WORKER
import skillbill.application.review.parallel.core.code.review.runner.agentId
import skillbill.application.review.parallel.core.code.review.runner.assignment
import skillbill.application.review.parallel.core.code.review.runner.broker
import skillbill.application.review.parallel.core.code.review.runner.budget
import skillbill.application.review.parallel.core.code.review.runner.bundleState
import skillbill.application.review.parallel.core.code.review.runner.droppedCandidateDiagnostic
import skillbill.application.review.parallel.core.code.review.runner.inline
import skillbill.application.review.parallel.core.code.review.runner.inlineParentAccounting
import skillbill.application.review.parallel.core.code.review.runner.launch
import skillbill.application.review.parallel.core.code.review.runner.modelOverride
import skillbill.application.review.parallel.core.code.review.runner.parallelCodeReviewBrokerEvidenceCompletionState
import skillbill.application.review.parallel.core.code.review.runner.prompt
import skillbill.application.review.parallel.core.code.review.runner.rejectedCandidateCount
import skillbill.application.review.parallel.core.code.review.runner.request
import skillbill.application.review.parallel.core.code.review.runner.resolvedMode
import skillbill.application.review.parallel.core.code.review.runner.unsupportedParentOutcome
import skillbill.application.review.parallel.core.code.review.spec.request
import skillbill.application.review.parallel.core.review.assignment
import skillbill.application.review.parallel.core.review.launch
import skillbill.application.review.parallel.planning.agentId
import skillbill.application.review.parallel.planning.budget
import skillbill.application.review.parallel.planning.lane
import skillbill.application.review.parallel.planning.repoRoot
import skillbill.application.review.parallel.planning.request
import skillbill.application.review.parallel.planning.resolvedMode
import skillbill.application.review.parallel.verification.ParallelCodeReviewRunnerFailureAdmission
import skillbill.application.review.parallel.verification.lane
import skillbill.application.review.parallel.verification.laneFailureReason
import skillbill.application.review.parallel.verification.parallelCodeReviewInlineTerminalStatus
import skillbill.application.review.preparation.assignment
import skillbill.application.review.preparation.facts
import skillbill.application.review.preparation.launch
import skillbill.application.review.preparation.original
import skillbill.application.review.preparation.request
import skillbill.application.review.review.broker
import skillbill.application.review.review.budget
import skillbill.application.review.review.facts
import skillbill.application.review.review.lane
import skillbill.application.review.review.prompt
import skillbill.application.review.review.repoRoot
import skillbill.application.review.review.stdout
import skillbill.application.review.service.review
import skillbill.application.review.spec.budget
import skillbill.application.review.spec.facts
import skillbill.application.review.spec.issueKey
import skillbill.application.review.spec.lane
import skillbill.application.review.spec.launch
import skillbill.application.review.spec.modelOverride
import skillbill.application.review.spec.prompt
import skillbill.application.review.spec.repoRoot
import skillbill.application.review.spec.stdout
import skillbill.application.review.spec.timeout
import skillbill.application.review.stats.lane
import skillbill.application.review.stats.segments
import skillbill.application.review.verification.facts
import skillbill.application.review.verification.inline
import skillbill.application.review.verification.launch
import skillbill.application.review.verification.prompt
import skillbill.application.review.verification.stdout
import skillbill.ports.agentrun.model.AgentRunLaunchFacts
import skillbill.ports.agentrun.model.SkillRunRequest
import skillbill.ports.agentrun.model.UnsupportedAgentRunLaunch
import skillbill.ports.goalrunner.runner.GoalRunnerSubtaskLauncher
import skillbill.ports.goalrunner.runner.model.GoalRunnerSubtaskLaunchRequest
import skillbill.ports.review.evidence.GovernedReviewEvidenceEndpointBinder
import skillbill.ports.review.evidence.GovernedReviewEvidenceEndpointHandle
import skillbill.ports.review.model.ParallelReviewLaneOutcome
import skillbill.ports.review.model.ReviewLaneAccounting
import skillbill.review.context.model.execution.ResolvedReviewExecutionMode
import skillbill.review.context.model.hunk.ReviewBudgetEvaluator
import skillbill.review.context.model.hunk.ReviewLaneIdentity
import skillbill.review.context.model.packet.ReviewLaneReviewDisposition
import skillbill.review.model.ParallelReviewRawFinding
import kotlin.time.Duration
import kotlin.time.TimeSource

private data class InlineMergedOutcomeRequest(
  val args: LaunchedBoundParentArgs,
  val outcome: ParallelReviewLaneOutcome,
  val lastFacts: AgentRunLaunchFacts,
  val stdoutChunks: List<String>,
  val findings: List<ParallelReviewRawFinding>,
  val droppedDiagnostic: String?,
  val rejectedCount: Int,
  val sliceCount: Int,
)

internal class ParallelCodeReviewInlineCoverageContinuation(
  private val parentReviewLauncher: GoalRunnerSubtaskLauncher,
  private val governedEvidenceEndpointBinder: GovernedReviewEvidenceEndpointBinder,
  private val failureAdmission: ParallelCodeReviewRunnerFailureAdmission,
  private val sliceOutcome: (LaunchedBoundParentArgs, AgentRunLaunchFacts) -> ParallelReviewLaneOutcome,
  private val evidenceReadCallback: (ParallelCodeReviewRequest) -> (() -> Unit)?,
) {
  fun run(args: LaunchedBoundParentArgs): ParallelReviewLaneOutcome {
    val bound = args.bound
    var endpoint = bound.endpoint
    val passStarted = TimeSource.Monotonic.markNow()
    var remainingTimeout = args.request.timeout
    val stdoutChunks = mutableListOf<String>()
    val findings = mutableListOf<ParallelReviewRawFinding>()
    var droppedDiagnostic: String? = null
    var rejectedCount = 0
    var sliceCount = 0
    try {
      while (true) {
        sliceCount += 1
        val deliveredBefore = bound.broker.accounting().deliveredEvidenceUnits
        when (
          val launchOutcome = parentReviewLauncher.launch(
            inlineParentLaunchRequest(args, endpoint, remainingTimeout),
          )
        ) {
          is UnsupportedAgentRunLaunch -> return unsupportedParentOutcome(args.launch, launchOutcome)
          is AgentRunLaunchFacts -> {
            val outcome = sliceOutcome(args, launchOutcome)
            if (stdoutChunks.isNotEmpty()) {
              args.bound.broker.observeLaneResultChunk("\n")
            }
            args.bound.broker.observeLaneResultChunk(launchOutcome.stdout)
            stdoutChunks += launchOutcome.stdout
            findings += outcome.findings
            droppedDiagnostic = outcome.droppedCandidateDiagnostic ?: droppedDiagnostic
            rejectedCount += outcome.rejectedCandidateCount
            val accounting = bound.broker.accounting()
            if (!shouldContinue(launchOutcome, accounting, deliveredBefore)) {
              return mergedOutcome(
                InlineMergedOutcomeRequest(
                  args = args,
                  outcome = outcome,
                  lastFacts = launchOutcome,
                  stdoutChunks = stdoutChunks,
                  findings = findings,
                  droppedDiagnostic = droppedDiagnostic,
                  rejectedCount = rejectedCount,
                  sliceCount = sliceCount,
                ),
              )
            }
            endpoint.unbindListener()
            endpoint = governedEvidenceEndpointBinder.bind(
              bound.broker.accounting().lane,
              bound.broker,
              evidenceReadCallback(args.request),
            )
            remainingTimeout = remainingPassTimeout(args.request.timeout, passStarted)
          }
        }
      }
    } finally {
      endpoint.close()
    }
  }

  private fun shouldContinue(
    facts: AgentRunLaunchFacts,
    accounting: ReviewLaneAccounting,
    deliveredBefore: Int,
  ): Boolean = failureAdmission.laneFailureReason(facts) == null &&
    accounting.terminalOutcome == null &&
    accounting.requiredEvidenceUnits > accounting.deliveredEvidenceUnits &&
    accounting.deliveredEvidenceUnits > deliveredBefore

  private fun mergedOutcome(request: InlineMergedOutcomeRequest): ParallelReviewLaneOutcome {
    val args = request.args
    val outcome = request.outcome
    val lastFacts = request.lastFacts
    val stdoutChunks = request.stdoutChunks
    val findings = request.findings
    val droppedDiagnostic = request.droppedDiagnostic
    val rejectedCount = request.rejectedCount
    val sliceCount = request.sliceCount
    val mergedStdout = stdoutChunks.joinToString("\n")
    val mergedResultBytes = mergedStdout.toByteArray().size.toLong()
    val budgetOutcome = ReviewBudgetEvaluator.laneResultOutcome(
      ReviewLaneIdentity.of(args.launch.assignment),
      args.budget,
      mergedResultBytes,
    ) ?: outcome.budgetOutcome
    val budgetFailure = budgetOutcome?.let {
      "${it.type}: ${it.budgetKind} ${it.observedValue} > ${it.configuredLimit}"
    }
    val evidenceAccounting = args.bound.broker.accounting()
    val completion = parallelCodeReviewBrokerEvidenceCompletionState(
      args.launch.bundleState,
      evidenceAccounting,
    )
    val failureReason = budgetFailure
      ?: outcome.failureReason
      ?: "Required review evidence remains undelivered.".takeIf {
        completion.disposition == ReviewLaneReviewDisposition.INCOMPLETE ||
          evidenceAccounting.terminalOutcome != null
      }
    val admittedFindings = if (budgetFailure == null) findings else emptyList()
    return outcome.copy(
      success = failureReason == null &&
        completion.disposition == ReviewLaneReviewDisposition.COMPLETE &&
        evidenceAccounting.terminalOutcome == null,
      rawOutput = mergedStdout,
      failureReason = failureReason,
      budgetOutcome = budgetOutcome,
      droppedCandidateDiagnostic = if (budgetFailure == null) droppedDiagnostic else null,
      rejectedCandidateCount = if (budgetFailure == null) rejectedCount else 0,
      findings = admittedFindings,
      accounting = inlineParentAccounting(
        args.launch,
        parallelCodeReviewInlineTerminalStatus(lastFacts, completion.disposition),
        lastFacts,
        evidenceAccounting,
        completion,
      ).copy(modelTurns = sliceCount, resultBytes = mergedResultBytes),
      reviewDisposition = completion.disposition,
      bundleCompositionDigest = completion.bundleCompositionDigest,
      segmentAccounting = completion.segments,
      unreviewedSegmentIds = completion.unreviewedSegmentIds,
      budgetDimension = completion.budgetDimension,
      unreviewedUnits = completion.unreviewedUnits,
    )
  }

  private fun inlineParentLaunchRequest(
    args: LaunchedBoundParentArgs,
    endpoint: GovernedReviewEvidenceEndpointHandle,
    timeout: Duration?,
  ): GoalRunnerSubtaskLaunchRequest = GoalRunnerSubtaskLaunchRequest(
    invokedAgentId = args.launch.agentId,
    configuredAgentOverrideId = null,
    skillRunRequest = SkillRunRequest(
      issueKey = "code-review",
      repoRoot = args.request.repoRoot,
      timeout = timeout,
      promptOverride = args.request.withSelectedAgentAddons(args.launch.prompt),
      modelOverride = args.modelOverride,
      reviewEvidenceBroker = args.bound.broker,
      reviewEvidenceEndpoint = endpoint,
      nativeReviewWorkerName = PARALLEL_REVIEW_INLINE_NATIVE_WORKER
        .takeIf { args.resolvedMode == ResolvedReviewExecutionMode.INLINE },
      reviewFanOut = args.resolvedMode == ResolvedReviewExecutionMode.DELEGATED,
    ),
  )

  private fun remainingPassTimeout(original: Duration?, passStarted: TimeSource.Monotonic.ValueTimeMark): Duration? {
    if (original == null) return null
    val remaining = original - passStarted.elapsedNow()
    return remaining.takeIf { it > Duration.ZERO } ?: Duration.ZERO
  }
}
