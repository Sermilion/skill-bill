package skillbill.engine.featuretask

import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunRequest

import skillbill.application.diagnostics.RejectedOutputDiagnosticService
import skillbill.application.review.RuntimeOwnedReviewMode
import skillbill.application.review.model.ParallelCodeReviewRequest
import skillbill.application.review.model.ParallelCodeReviewResult
import skillbill.application.review.model.StackDetectionException
import skillbill.application.review.model.UsageValidationException
import skillbill.application.reviewevidence.model.DiffResolutionException
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.error.InvalidReviewContextSchemaError
import skillbill.error.UnreadableSpecIntentProjectionError
import skillbill.goalrunner.subtaskreview.FeatureTaskRuntimeVerificationSignalKeys
import skillbill.goalrunner.subtaskreview.GoalSubtaskReviewSummaryReducer
import skillbill.goalrunner.subtaskreview.model.UnaddressedFindingLedgerScope
import skillbill.ports.diagnostics.model.ProducerOutputEvidence
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import skillbill.ports.workflow.gitops.repositoryFingerprint
import skillbill.review.context.model.ReviewContextBudgetExceededException
import skillbill.workflow.goal.model.GoalSubtaskBlockerDisposition
import skillbill.workflow.taskruntime.model.AcceptedFeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeFailureDisposition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseRecord
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeReviewPassSequence
import skillbill.workflow.taskruntime.model.NormalizedFeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.requireAcceptedOutput
import kotlin.coroutines.cancellation.CancellationException
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseOutputValidator
import skillbill.engine.featuretask.FeatureTaskRuntimeGoalContinuationRecorder
import skillbill.engine.featuretask.FeatureTaskRuntimePhaseGates
import skillbill.ports.diagnostics.RuntimeDiagnostics
import java.time.Clock

object FeatureTaskRuntimeRunLoopReview {
  internal fun prepareRuntimeOwnedReview(request: FeatureTaskRuntimeRunRequest, recorder: FeatureTaskRuntimePhaseRecorder, goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder, phaseGates: FeatureTaskRuntimePhaseGates, clock: Clock, run: PhaseRun, state: FeatureTaskRuntimeRunState): RuntimeOwnedReviewPrep {
    val input = run.goalReviewInput
      ?: return RuntimeOwnedReviewBlocked(
        PhaseOutcome.blocked("Runtime-owned review is missing the child-owned review input."),
      )
    val iteration = state.nextIteration(run.phaseId)
    val passNumber = FeatureTaskRuntimeRunLoopOutputPersistence.reviewPassNumber(request, goalContinuationRecorder, run, state) ?: 1
    val pinnedMode = run.request.runInvariants.codeReviewMode
    val resolution = FeatureTaskRuntimeReviewPassSequence.resolveForPass(pinnedMode, passNumber)
    val reviewRunId = resolveReviewRunId( clock, state.recordFor(run.phaseId), passNumber)
    FeatureTaskRuntimeRunLoopOutputPersistence.persistPhase(request, state, recorder, goalContinuationRecorder, PersistPhaseArgs(
        write = PhaseStateWriteArgs(
          run = run,
          iteration = iteration,
          status = STATUS_RUNNING,
          finished = false,
          outputArtifact = null,
        ),
        reviewRunId = reviewRunId,
      ))
    val checkpoint = phaseGates.gitOperations.repositoryFingerprint(run.request.repoRoot).value
      .takeIf(String::isNotBlank)
      ?: return RuntimeOwnedReviewBlocked(
        PhaseOutcome.blocked(
          "Runtime-owned review could not resolve a repository checkpoint fingerprint.",
        ),
      )
    return RuntimeOwnedReviewReady(
      run = run,
      launch = RuntimeOwnedReviewLaunch(
        iteration = iteration,
        passNumber = passNumber,
        resolvedTier = RuntimeOwnedReviewMode.execute(resolution.resolvedTier),
        reviewRunId = reviewRunId,
        checkpoint = checkpoint,
      ),
      driverRequest = runtimeOwnedReviewDriverRequest(recorder, goalContinuationRecorder, RuntimeOwnedReviewDriverRequestArgs(run, input, passNumber, pinnedMode, reviewRunId)),
    )
  }

  private fun resolveReviewRunId( clock: Clock, durableRecord: FeatureTaskRuntimePhaseRecord?, passNumber: Int): String = durableRecord
    ?.takeIf { (it.reviewPassNumber ?: 1) == passNumber }
    ?.reviewRunId
    ?.takeIf(String::isNotBlank)
    ?: FeatureTaskRuntimeReviewEnvelope.mintReviewRunId(clock)

  private fun runtimeOwnedReviewDriverRequest(recorder: FeatureTaskRuntimePhaseRecorder, goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder, args: RuntimeOwnedReviewDriverRequestArgs)= FeatureTaskRuntimeReviewDriverMapper.request(
    input = args.input,
    runInvariants = args.run.request.runInvariants,
    agents = FeatureTaskRuntimeReviewDriverAgents(
      agent1Id = args.run.resolvedAgent.resolvedAgentId,
    ),
    pass = FeatureTaskRuntimeReviewDriverPass(
      passNumber = args.passNumber,
      pinnedMode = args.pinnedMode,
      reviewRunId = args.reviewRunId,
    ),
    workspace = FeatureTaskRuntimeReviewDriverWorkspace(
      repoRoot = args.run.request.repoRoot,
      timeout = args.run.request.timeout,
      agentAddonSelection = args.run.request.agentAddonSelection,
      baselineUntrackedPaths = reviewBaselineUntrackedPaths(recorder, goalContinuationRecorder, args.run),
    ),
  ).copy(
    activityWorkflowId = args.run.request.workflowId,
    activityParentWorkflowId = args.run.request.goalContinuation?.parentWorkflowId,
  )

  internal fun executePreparedReviewDriver(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, session: FeatureTaskRuntimeRunLoopSession, goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder, outputValidator: FeatureTaskRuntimePhaseOutputValidator, diagnostics: RuntimeDiagnostics, phaseGates: FeatureTaskRuntimePhaseGates, clock: Clock, prepared: RuntimeOwnedReviewReady, observability: FeatureTaskRuntimeRunObservability): PhaseOutcome {
    val run = prepared.run
    observability.started(
      run.phaseId,
      run.resolvedAgent.resolvedAgentId,
      prepared.launch.iteration,
      run.modelDirective,
      FeatureTaskRuntimePhaseStartReentry.FIRST_VISIT,
    )
    val before = phaseGates.gitOperations.worktreeStatus(run.request.repoRoot)
    if (before !is WorkflowGitOperationResult.Ok) {
      return FeatureTaskRuntimeRunLoopPhaseAttempts.blockInPhase(request, state, recorder, observability, PhaseBlockRequest(
          run = run,
          attemptCount = prepared.launch.iteration,
          reason = "Feature-task-runtime phase 'review' could not capture its before-file manifest: ${before.error}",
          observability = observability,
        ))
    }
    return when (val attempt = invokeReviewDriver( phaseGates, prepared.driverRequest)) {
      is ReviewDriverFailed -> FeatureTaskRuntimeRunLoopPhaseAttempts.blockInPhase(request, state, recorder, observability, PhaseBlockRequest(
          run = run,
          attemptCount = prepared.launch.iteration,
          reason = attempt.reason,
          observability = observability,
          failureDisposition = attempt.disposition,
        ))
      is ReviewDriverReady -> {
        val after = phaseGates.gitOperations.worktreeStatus(run.request.repoRoot)
        if (after !is WorkflowGitOperationResult.Ok) {
          return FeatureTaskRuntimeRunLoopPhaseAttempts.blockInPhase(request, state, recorder, observability, PhaseBlockRequest(
              run = run,
              attemptCount = prepared.launch.iteration,
              reason = "Feature-task-runtime phase 'review' could not capture its after-file manifest: ${after.error}",
              observability = observability,
            ))
        }
        FeatureTaskRuntimeRunLoopLaunch.capturePhaseContentIdentities(request, session, phaseGates, run.phaseId)
        settleReviewDriverResult(request, state, recorder, session, goalContinuationRecorder, outputValidator, diagnostics, phaseGates, clock, prepared, attempt.result, observability, FeatureTaskRuntimePhaseFileManifest(
            before = FeatureTaskRuntimePhaseSafetyPolicy.changedPaths(before.value.orEmpty()),
            after = FeatureTaskRuntimePhaseSafetyPolicy.changedPaths(after.value.orEmpty()),
          ))
      }
    }
  }

  internal fun invokeReviewDriver( phaseGates: FeatureTaskRuntimePhaseGates, request: ParallelCodeReviewRequest): ReviewDriverAttempt {
    val outcome = runCatching { phaseGates.reviewDriver.run(request) }
    val error = outcome.exceptionOrNull()
    if (error == null) {
      return ReviewDriverReady(outcome.getOrThrow())
    }
    val mapped: ReviewDriverAttempt? = when (error) {
      is CancellationException -> null
      is DiffResolutionException -> ReviewDriverFailed(
        "Runtime-owned review could not resolve the child-owned diff: ${error.message.orEmpty()}",
      )
      is UsageValidationException -> ReviewDriverFailed(
        "Runtime-owned review failed: ${error.message.orEmpty()}",
        FeatureTaskRuntimeFailureDisposition.RETRYABLE,
      )
      is StackDetectionException -> ReviewDriverFailed(
        "Runtime-owned review failed: ${error.message.orEmpty()}",
        FeatureTaskRuntimeFailureDisposition.RETRYABLE,
      )
      is ReviewContextBudgetExceededException -> ReviewDriverFailed(
        "Runtime-owned review exceeded a review-context budget: ${error.message.orEmpty()}",
      )
      is UnreadableSpecIntentProjectionError -> ReviewDriverFailed(
        "Runtime-owned review could not read the spec intent projection: ${error.message.orEmpty()}",
      )
      is InvalidReviewContextSchemaError -> ReviewDriverFailed(
        "Runtime-owned review produced an invalid review-context envelope: ${error.message.orEmpty()}",
      )
      is RuntimeOwnedFactUnavailable -> ReviewDriverFailed(
        "Runtime-owned review could not establish a required persistence fact: ${error.message.orEmpty()}",
        FeatureTaskRuntimeFailureDisposition.PROCESS_FAILURE,
      )
      is Exception -> ReviewDriverFailed(
        "Runtime-owned review failed: ${error::class.simpleName}: ${error.message.orEmpty()}",
        FeatureTaskRuntimeFailureDisposition.RETRYABLE,
      )
      else -> null
    }
    return mapped ?: throw error
  }

  internal fun settleReviewDriverResult(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, session: FeatureTaskRuntimeRunLoopSession, goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder, outputValidator: FeatureTaskRuntimePhaseOutputValidator, diagnostics: RuntimeDiagnostics, phaseGates: FeatureTaskRuntimePhaseGates, clock: Clock, prepared: RuntimeOwnedReviewReady, result: ParallelCodeReviewResult, observability: FeatureTaskRuntimeRunObservability, fileManifest: FeatureTaskRuntimePhaseFileManifest): PhaseOutcome {
    val run = prepared.run
    failedReviewLaneReason(result)?.let { reason ->
      return FeatureTaskRuntimeRunLoopPhaseAttempts.blockInPhase(request, state, recorder, observability, PhaseBlockRequest(
          run = run,
          attemptCount = prepared.launch.iteration,
          reason = reason,
          observability = observability,
          failureDisposition = FeatureTaskRuntimeFailureDisposition.RETRYABLE,
        ))
    }
    return when (
      val settlement = prepareReviewSettlement(request, state, recorder, session, outputValidator, diagnostics, phaseGates, prepared, result, observability, fileManifest)
    ) {
      is ReviewSettlementPreparation.Blocked -> settlement.outcome
      is ReviewSettlementPreparation.Ready -> settleRuntimeOwnedReview(request, state, recorder, observability, goalContinuationRecorder, clock, SettleRuntimeOwnedReviewArgs(
          run,
          prepared.launch.iteration,
          settlement.outputText,
          observability,
          fileManifest,
        ), settlement.acceptedOutput)
    }
  }

  private fun prepareReviewSettlement(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, session: FeatureTaskRuntimeRunLoopSession, outputValidator: FeatureTaskRuntimePhaseOutputValidator, diagnostics: RuntimeDiagnostics, phaseGates: FeatureTaskRuntimePhaseGates, prepared: RuntimeOwnedReviewReady, result: ParallelCodeReviewResult, observability: FeatureTaskRuntimeRunObservability, fileManifest: FeatureTaskRuntimePhaseFileManifest): ReviewSettlementPreparation {
    val run = prepared.run
    val initialCycle = assembleReviewCycle(recorder, prepared, result, prepared.launch.checkpoint)
    val acceptedOutput = runCatching {
      outputValidator.validatePhaseOutput(
        initialCycle.outputText,
        sourceLabel = run.phaseId,
      ).requireAcceptedOutput(run.phaseId)
    }.getOrElse { error ->
      return ReviewSettlementPreparation.Blocked(
        FeatureTaskRuntimeRunLoopPhaseAttempts.blockAndPersistInPhase(request, state, recorder, observability, null, phaseBlockArgs(
            run,
            prepared.launch.iteration,
            "Runtime-owned review settlement did not validate: ${error.message.orEmpty()}",
            observability,
            payload = BlockAndPersistPayload(fileManifest = fileManifest),
          )),
      )
    }
    if (fileManifest.before == fileManifest.after) {
      return ReviewSettlementPreparation.Ready(initialCycle.outputText, acceptedOutput)
    }
    return when (val checkpoint = checkpointReviewChanges(request, state, recorder, session, diagnostics, phaseGates, run, prepared.launch.iteration, observability)) {
      is ReviewCheckpointResult.Blocked -> ReviewSettlementPreparation.Blocked(checkpoint.outcome)
      is ReviewCheckpointResult.Refreshed -> {
        val outputText = assembleReviewCycle(recorder, prepared, result, checkpoint.fingerprint).outputText
        ReviewSettlementPreparation.Ready(
          outputText,
          acceptedOutput.withReviewRepositoryFingerprintIfNeeded(outputText),
        )
      }
    }
  }

  private fun assembleReviewCycle(recorder: FeatureTaskRuntimePhaseRecorder, prepared: RuntimeOwnedReviewReady, result: ParallelCodeReviewResult, fingerprint: String): FeatureTaskRuntimeReviewDriverCycleOutcome = FeatureTaskRuntimeReviewDriverCycle.assemble(
    result = result,
    request = prepared.driverRequest,
    cycle = FeatureTaskRuntimeReviewCycleContext(
      passNumber = prepared.launch.passNumber,
      resolvedTier = prepared.launch.resolvedTier,
      repositoryFingerprint = fingerprint,
      blockerDispositions = reviewBlockerDispositions(recorder, ReviewBlockerDispositionsArgs(
          run = prepared.run,
          passNumber = prepared.launch.passNumber,
          result = result,
          reviewRunId = prepared.launch.reviewRunId,
          resolvedTier = prepared.launch.resolvedTier,
        )),
    ),
  )

  private fun checkpointReviewChanges(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, session: FeatureTaskRuntimeRunLoopSession, diagnostics: RuntimeDiagnostics, phaseGates: FeatureTaskRuntimePhaseGates, run: PhaseRun, iteration: Int, observability: FeatureTaskRuntimeRunObservability): ReviewCheckpointResult {
    val checkpointed = FeatureTaskRuntimeRunLoopCheckpointRemediation.checkpointEstablished(
      request, state, recorder, session,
      diagnostics,
      phaseGates,
      precedingPhaseId = run.phaseId,
      loopId = null,
      intent = FeatureTaskRuntimeCheckpointMessage.INTENT_REMEDIATION,
      blockedReason = { branch, error ->
        "Feature-task-runtime could not amend review changes on the feature branch '$branch'" +
          (if (error.isBlank()) "." else " ($error).") +
          " Refusing to complete review with uncommitted review fixes."
      },
    )
    if (!checkpointed) {
      return ReviewCheckpointResult.Blocked(
        FeatureTaskRuntimeRunLoopPhaseAttempts.blockInPhase(request, state, recorder, observability, PhaseBlockRequest(
            run = run,
            attemptCount = iteration,
            reason = "Runtime-owned review changes could not be committed before review settlement.",
            observability = observability,
            failureDisposition = FeatureTaskRuntimeFailureDisposition.PROCESS_FAILURE,
          )),
      )
    }
    val fingerprint = phaseGates.gitOperations.repositoryFingerprint(run.request.repoRoot).value
      .takeIf(String::isNotBlank)
      ?: return ReviewCheckpointResult.Blocked(
        FeatureTaskRuntimeRunLoopPhaseAttempts.blockInPhase(request, state, recorder, observability, PhaseBlockRequest(
            run = run,
            attemptCount = iteration,
            reason = "Runtime-owned review could not resolve the post-amend repository checkpoint fingerprint.",
            observability = observability,
            failureDisposition = FeatureTaskRuntimeFailureDisposition.PROCESS_FAILURE,
          )),
      )
    return ReviewCheckpointResult.Refreshed(fingerprint)
  }

  internal fun reviewBaselineUntrackedPaths(recorder: FeatureTaskRuntimePhaseRecorder, goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder, run: PhaseRun): List<String> =
    recorder.loadResolvedBranch(run.request.workflowId)
      ?.baselineUntrackedPaths
      ?.takeIf { it.isNotEmpty() }
      ?: goalContinuationRecorder.reviewState(run.request.workflowId)
        ?.baselineUntrackedPaths
        .orEmpty()

  internal fun failedReviewLaneReason(result: ParallelCodeReviewResult): String? {
    val parent = result.lane1
    if (parent.agentId.isBlank() || parent.success) return null
    val detail = parent.failureReason?.takeIf(String::isNotBlank) ?: "lane failed"
    return "Feature-task-runtime phase 'review' $detail"
  }

  internal fun reviewBlockerDispositions(recorder: FeatureTaskRuntimePhaseRecorder, args: ReviewBlockerDispositionsArgs): List<GoalSubtaskBlockerDisposition> {
    val run = args.run
    val passNumber = args.passNumber
    val result = args.result
    val reviewRunId = args.reviewRunId
    val resolvedTier = args.resolvedTier
    if (passNumber < 2) return emptyList()
    val prior = recorder.fetchUnaddressedLedger(run.request.workflowId)
    if (prior.isEmpty()) return emptyList()
    val continuation = run.request.goalContinuation
    val envelope = FeatureTaskRuntimeReviewEnvelope.envelopeMap(
      FeatureTaskRuntimeReviewEnvelope.assemble(
        result = result,
        reviewRunId = reviewRunId,
        cycle = FeatureTaskRuntimeReviewCycleContext(
          passNumber = passNumber,
          resolvedTier = resolvedTier,
          repositoryFingerprint = "disposition-preview",
        ),
      ),
    )
    val verdicts = recorder.recordedFindingVerdicts(envelope)
    val current = GoalSubtaskReviewSummaryReducer.unaddressedFindings(
      output = envelope,
      scope = UnaddressedFindingLedgerScope(
        issueKey = continuation?.parentIssueKey ?: run.request.issueKey,
        subtaskId = continuation?.subtaskId ?: 0,
        workflowId = run.request.workflowId,
        reviewPassNumber = passNumber,
      ),
      recordedVerdicts = verdicts,
    )
    return GoalSubtaskReviewSummaryReducer.refutedBlockerSupersedes(prior, current, verdicts)
  }

  internal fun settleRuntimeOwnedReview(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, observability: FeatureTaskRuntimeRunObservability, goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder, clock: Clock, args: SettleRuntimeOwnedReviewArgs, acceptedOutput: AcceptedFeatureTaskRuntimePhaseOutput): PhaseOutcome {
    val run = args.run
    val iteration = args.iteration
    val outputText = args.outputText
    val fileManifest = args.fileManifest
    with(FeatureTaskRuntimeRunLoopReviewDriverSettlement) {
      retainRuntimeOwnedReviewEvidence(request, recorder, clock, run, state, iteration, outputText)
      persistReviewCompletionOutcome(request, state, recorder, observability, goalContinuationRecorder, PhaseReviewCompletionOutcomeArgs(
          persistence = PhaseReviewPersistenceArgs(run, iteration, observability, fileManifest),
          normalizedOutput = acceptedOutput.normalizedOutput,
          acceptedOutput = acceptedOutput,
          outputText = outputText,
        ))?.let { return it }
      return completeRuntimeOwnedReviewPhase(observability, CompleteRuntimeOwnedReviewPhaseArgs(
          run = run,
          iteration = iteration,
          observability = observability,
          normalizedOutput = acceptedOutput.normalizedOutput,
          acceptedOutput = acceptedOutput,
        ))
    }
  }
}

private sealed interface ReviewSettlementPreparation {
  data class Ready(
    val outputText: String,
    val acceptedOutput: AcceptedFeatureTaskRuntimePhaseOutput,
  ) : ReviewSettlementPreparation

  data class Blocked(val outcome: PhaseOutcome) : ReviewSettlementPreparation
}

private sealed interface ReviewCheckpointResult {
  data class Refreshed(val fingerprint: String) : ReviewCheckpointResult

  data class Blocked(val outcome: PhaseOutcome) : ReviewCheckpointResult
}

private fun AcceptedFeatureTaskRuntimePhaseOutput.withReviewRepositoryFingerprintIfNeeded(
  outputText: String,
): AcceptedFeatureTaskRuntimePhaseOutput {
  val fingerprint = outputText.repositoryCheckpointFingerprint() ?: return this
  val envelope = JsonCodec.anyToStringAnyMap(
    FeatureTaskRuntimeReviewEnvelope.envelopeMap(outputText),
  ).orEmpty().toMutableMap()
  val produced = JsonCodec.anyToStringAnyMap(envelope[SharedPayloadKeys.PRODUCED_OUTPUTS])
    .orEmpty()
    .toMutableMap()
  produced[FeatureTaskRuntimeVerificationSignalKeys.REPOSITORY_CHECKPOINT] =
    mapOf(
      FeatureTaskRuntimeVerificationSignalKeys.REPOSITORY_CHECKPOINT_FINGERPRINT to fingerprint,
    )
  envelope[SharedPayloadKeys.PRODUCED_OUTPUTS] = produced
  return copy(
    normalizedOutput = NormalizedFeatureTaskRuntimePhaseOutput(
      canonicalJson = JsonCodec.mapToJsonString(envelope),
      envelope = envelope,
    ),
  )
}

private fun String.repositoryCheckpointFingerprint(): String? = FeatureTaskRuntimeReviewEnvelope.envelopeMap(this)
  .let { JsonCodec.anyToStringAnyMap(it[SharedPayloadKeys.PRODUCED_OUTPUTS]).orEmpty() }
  .let { JsonCodec.anyToStringAnyMap(it[FeatureTaskRuntimeVerificationSignalKeys.REPOSITORY_CHECKPOINT]) }
  ?.get(FeatureTaskRuntimeVerificationSignalKeys.REPOSITORY_CHECKPOINT_FINGERPRINT)
  ?.toString()
  ?.takeIf(String::isNotBlank)

object FeatureTaskRuntimeRunLoopReviewDriverSettlement {
  internal fun FeatureTaskRuntimeRunLoopReview.retainRuntimeOwnedReviewEvidence(request: FeatureTaskRuntimeRunRequest, recorder: FeatureTaskRuntimePhaseRecorder, clock: Clock, run: PhaseRun, state: FeatureTaskRuntimeRunState, iteration: Int, outputText: String){
    val outputBytes = outputText.encodeToByteArray()
    recorder.retainProducerOutput(
      ProducerOutputEvidence(
        workflowId = request.workflowId,
        phaseId = run.phaseId,
        attempt = iteration,
        agentId = run.resolvedAgent.resolvedAgentId,
        model = run.modelDirective?.model ?: "unspecified",
        recordedAt = clock.instant(),
        byteSize = outputBytes.size.toLong(),
        sha256 = RejectedOutputDiagnosticService.sha256(outputBytes),
        payload = outputBytes,
        generation = state.evidenceGeneration(run.phaseId),
      ),
    )
  }

  internal fun FeatureTaskRuntimeRunLoopReview.persistReviewCompletionOutcome(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, observability: FeatureTaskRuntimeRunObservability, goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder, args: PhaseReviewCompletionOutcomeArgs): PhaseOutcome? {
    return if (FeatureTaskRuntimeRunLoopOutputPersistence.isGoalReviewRun(args.persistence.run)) {
      FeatureTaskRuntimeRunLoopOutputPersistence.persistGoalReviewCompletion(request, state, recorder, observability, goalContinuationRecorder, args.persistence, args.normalizedOutput, args.acceptedOutput.repairEvidence)
    } else {
      FeatureTaskRuntimeRunLoopOutputPersistence.persistStandaloneReviewCompletion(request, state, recorder, observability, goalContinuationRecorder, args.persistence, args.outputText, args.acceptedOutput)
    }
  }

  internal fun FeatureTaskRuntimeRunLoopReview.completeRuntimeOwnedReviewPhase(observability: FeatureTaskRuntimeRunObservability, args: CompleteRuntimeOwnedReviewPhaseArgs): PhaseOutcome {
    val run = args.run
    val iteration = args.iteration
    val normalizedOutput = args.normalizedOutput
    val acceptedOutput = args.acceptedOutput
    observability.completed(run.phaseId, run.resolvedAgent.resolvedAgentId, iteration)
    return PhaseOutcome.completed(
      FeatureTaskRuntimePhaseOutput(
        run.phaseId,
        iteration,
        normalizedOutput.canonicalJson,
        normalizedOutput,
        acceptedOutput.repairEvidence,
      ),
    )
  }
}
