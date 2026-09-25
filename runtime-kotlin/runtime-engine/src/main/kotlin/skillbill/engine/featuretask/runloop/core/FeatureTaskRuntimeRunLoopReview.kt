package skillbill.engine.featuretask.runloop.core

import skillbill.application.diagnostics.RejectedOutputDiagnosticService
import skillbill.application.review.model.ParallelCodeReviewRequest
import skillbill.application.review.model.ParallelCodeReviewResult
import skillbill.application.review.model.StackDetectionException
import skillbill.application.review.model.UsageValidationException
import skillbill.application.review.service.RuntimeOwnedReviewMode
import skillbill.application.reviewevidence.model.DiffResolutionException
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.engine.featuretask.lifecycle.branch.Blocked
import skillbill.engine.featuretask.lifecycle.checkpoint.FeatureTaskRuntimeCheckpointMessage
import skillbill.engine.featuretask.lifecycle.continuation.FeatureTaskRuntimeGoalContinuationRecorder
import skillbill.engine.featuretask.lifecycle.continuation.reviewState
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeRunRequest
import skillbill.engine.featuretask.persist.RuntimeOwnedFactUnavailable
import skillbill.engine.featuretask.phase.core.FeatureTaskRuntimePhaseFileManifest
import skillbill.engine.featuretask.phase.core.FeatureTaskRuntimePhaseGates
import skillbill.engine.featuretask.phase.core.FeatureTaskRuntimePhaseSafetyPolicy
import skillbill.engine.featuretask.phase.record.FeatureTaskRuntimePhaseRecorder
import skillbill.engine.featuretask.review.core.FeatureTaskRuntimeReviewCycleContext
import skillbill.engine.featuretask.review.core.FeatureTaskRuntimeReviewDriverAgents
import skillbill.engine.featuretask.review.core.FeatureTaskRuntimeReviewDriverCycle
import skillbill.engine.featuretask.review.core.FeatureTaskRuntimeReviewDriverCycleOutcome
import skillbill.engine.featuretask.review.core.FeatureTaskRuntimeReviewDriverMapper
import skillbill.engine.featuretask.review.core.FeatureTaskRuntimeReviewDriverPass
import skillbill.engine.featuretask.review.core.FeatureTaskRuntimeReviewDriverWorkspace
import skillbill.engine.featuretask.review.core.FeatureTaskRuntimeReviewEnvelope
import skillbill.engine.featuretask.runloop.checkpoint.FeatureTaskRuntimeRunLoopCheckpointRemediation
import skillbill.engine.featuretask.runloop.observability.FeatureTaskRuntimePhaseStartReentry
import skillbill.engine.featuretask.runloop.observability.FeatureTaskRuntimeRunObservability
import skillbill.engine.featuretask.runloop.output.FeatureTaskRuntimeRunLoopOutputPersistence
import skillbill.engine.featuretask.runloop.output.FeatureTaskRuntimeRunLoopReviewCompletion
import skillbill.engine.featuretask.runloop.output.ReviewOutputPersistenceContext
import skillbill.engine.featuretask.runloop.output.isGoalReviewRun
import skillbill.engine.featuretask.runloop.phase.FeatureTaskRuntimeRunLoopPhaseBlocking
import skillbill.engine.featuretask.runloop.state.FeatureTaskRuntimeRunState
import skillbill.engine.featuretask.runner.STATUS_RUNNING
import skillbill.engine.goalrunner.status.completed
import skillbill.error.shellcontent.InvalidReviewContextSchemaError
import skillbill.error.shellcontent.UnreadableSpecIntentProjectionError
import skillbill.goalrunner.subtaskreview.FeatureTaskRuntimeVerificationSignalKeys
import skillbill.goalrunner.subtaskreview.GoalSubtaskReviewSummaryReducer
import skillbill.goalrunner.subtaskreview.model.UnaddressedFindingLedgerScope
import skillbill.ports.diagnostics.model.ProducerOutputEvidence
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import skillbill.review.context.model.hunk.ReviewContextBudgetExceededException
import skillbill.workflow.model.goalreview.FeatureTaskRuntimeReviewPassSequence
import skillbill.workflow.model.goalreview.GoalSubtaskBlockerDisposition
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.handoff.task.NormalizedFeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.phase.AcceptedFeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeFailureDisposition
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimePhaseRecord
import skillbill.workflow.taskruntime.model.phase.requireAcceptedOutput
import java.time.Clock
import kotlin.coroutines.cancellation.CancellationException

internal data class RuntimeOwnedReviewPreparationArgs(
  val request: FeatureTaskRuntimeRunRequest,
  val recorder: FeatureTaskRuntimePhaseRecorder,
  val goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
  val phaseGates: FeatureTaskRuntimePhaseGates,
  val clock: Clock,
  val state: FeatureTaskRuntimeRunState,
  val run: PhaseRun,
)

private data class ReviewStartArgs(
  val request: FeatureTaskRuntimeRunRequest,
  val state: FeatureTaskRuntimeRunState,
  val recorder: FeatureTaskRuntimePhaseRecorder,
  val goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
  val run: PhaseRun,
  val iteration: Int,
  val reviewRunId: String,
)

object FeatureTaskRuntimeRunLoopReview {
  internal fun prepareRuntimeOwnedReview(args: RuntimeOwnedReviewPreparationArgs): RuntimeOwnedReviewPrep {
    val request = args.request
    val recorder = args.recorder
    val goalContinuationRecorder = args.goalContinuationRecorder
    val phaseGates = args.phaseGates
    val clock = args.clock
    val state = args.state
    val run = args.run
    val input =
      run.goalReviewInput
        ?: return RuntimeOwnedReviewBlocked(
          PhaseOutcome.blocked("Runtime-owned review is missing the child-owned review input."),
        )
    val iteration = state.nextIteration(run.phaseId)
    val passNumber =
      FeatureTaskRuntimeRunLoopPhaseBlocking.reviewPassNumber(
        request,
        goalContinuationRecorder,
        run,
        state,
      ) ?: 1
    val pinnedMode = run.request.runInvariants.codeReviewMode
    val resolution = FeatureTaskRuntimeReviewPassSequence.resolveForPass(pinnedMode, passNumber)
    val reviewRunId = resolveReviewRunId(clock, state.recordFor(run.phaseId), passNumber)
    persistReviewStart(ReviewStartArgs(request, state, recorder, goalContinuationRecorder, run, iteration, reviewRunId))
    val checkpoint =
      phaseGates.gitOperations.repositoryFingerprint(run.request.repoRoot).value
        .takeIf(String::isNotBlank)
        ?: return RuntimeOwnedReviewBlocked(
          PhaseOutcome.blocked(
            "Runtime-owned review could not resolve a repository checkpoint fingerprint.",
          ),
        )
    return RuntimeOwnedReviewReady(
      run = run,
      launch =
        RuntimeOwnedReviewLaunch(
          iteration = iteration,
          passNumber = passNumber,
          resolvedTier = RuntimeOwnedReviewMode.execute(resolution.resolvedTier),
          reviewRunId = reviewRunId,
          checkpoint = checkpoint,
        ),
      driverRequest =
        runtimeOwnedReviewDriverRequest(
          recorder,
          goalContinuationRecorder,
          RuntimeOwnedReviewDriverRequestArgs(
            run,
            input,
            passNumber,
            pinnedMode,
            reviewRunId,
          ),
        ),
    )
  }

  private fun persistReviewStart(args: ReviewStartArgs) {
    FeatureTaskRuntimeRunLoopOutputPersistence.persistPhase(
      args.request,
      args.state,
      args.recorder,
      args.goalContinuationRecorder,
      PersistPhaseArgs(
        write =
          PhaseStateWriteArgs(
            run = args.run,
            iteration = args.iteration,
            status = STATUS_RUNNING,
            finished = false,
            outputArtifact = null,
          ),
        reviewRunId = args.reviewRunId,
      ),
    )
  }

  private fun resolveReviewRunId(
    clock: Clock,
    durableRecord: FeatureTaskRuntimePhaseRecord?,
    passNumber: Int,
  ): String =
    durableRecord
      ?.takeIf { (it.reviewPassNumber ?: 1) == passNumber }
      ?.reviewRunId
      ?.takeIf(String::isNotBlank)
      ?: FeatureTaskRuntimeReviewEnvelope.mintReviewRunId(clock)

  private fun runtimeOwnedReviewDriverRequest(
    recorder: FeatureTaskRuntimePhaseRecorder,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    args: RuntimeOwnedReviewDriverRequestArgs,
  ) = FeatureTaskRuntimeReviewDriverMapper.request(
    input = args.input,
    runInvariants = args.run.request.runInvariants,
    agents =
      FeatureTaskRuntimeReviewDriverAgents(
        agent1Id = args.run.resolvedAgent.resolvedAgentId,
      ),
    pass =
      FeatureTaskRuntimeReviewDriverPass(
        passNumber = args.passNumber,
        pinnedMode = args.pinnedMode,
        reviewRunId = args.reviewRunId,
      ),
    workspace =
      FeatureTaskRuntimeReviewDriverWorkspace(
        repoRoot = args.run.request.repoRoot,
        timeout = args.run.request.timeout,
        agentAddonSelection = args.run.request.agentAddonSelection,
        baselineUntrackedPaths = reviewBaselineUntrackedPaths(recorder, goalContinuationRecorder, args.run),
      ),
  ).copy(
    activityWorkflowId = args.run.request.workflowId,
    activityParentWorkflowId = args.run.request.goalContinuation?.parentWorkflowId,
  )

  internal fun FeatureTaskRuntimeRunLoopContext.executePreparedReviewDriver(
    prepared: RuntimeOwnedReviewReady,
  ): PhaseOutcome {
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
      return blockReviewWorktreeFailure(run, prepared.launch.iteration, "before", before.error)
    }
    return when (val attempt = invokeReviewDriver(phaseGates, prepared.driverRequest)) {
      is ReviewDriverFailed ->
        FeatureTaskRuntimeRunLoopPhaseBlocking.blockInPhase(
          request,
          state,
          recorder,
          observability,
          PhaseBlockRequest(
            run = run,
            attemptCount = prepared.launch.iteration,
            reason = attempt.reason,
            observability = observability,
            failureDisposition = attempt.disposition,
          ),
        )
      is ReviewDriverReady -> {
        val after = phaseGates.gitOperations.worktreeStatus(run.request.repoRoot)
        if (after !is WorkflowGitOperationResult.Ok) {
          return blockReviewWorktreeFailure(run, prepared.launch.iteration, "after", after.error)
        }
        FeatureTaskRuntimeRunLoopLaunch.capturePhaseContentIdentities(request, session, phaseGates, run.phaseId)
        settleReviewDriverResult(
          prepared,
          attempt.result,
          FeatureTaskRuntimePhaseFileManifest(
            before = FeatureTaskRuntimePhaseSafetyPolicy.changedPaths(before.value.orEmpty()),
            after = FeatureTaskRuntimePhaseSafetyPolicy.changedPaths(after.value.orEmpty()),
          ),
        )
      }
    }
  }

  private fun FeatureTaskRuntimeRunLoopContext.blockReviewWorktreeFailure(
    run: PhaseRun,
    iteration: Int,
    boundary: String,
    error: String,
  ): PhaseOutcome =
    FeatureTaskRuntimeRunLoopPhaseBlocking.blockInPhase(
      request,
      state,
      recorder,
      observability,
      PhaseBlockRequest(
        run = run,
        attemptCount = iteration,
        reason = "Feature-task-runtime phase 'review' could not capture its $boundary-file manifest: $error",
        observability = observability,
      ),
    )

  internal fun invokeReviewDriver(
    phaseGates: FeatureTaskRuntimePhaseGates,
    request: ParallelCodeReviewRequest,
  ): ReviewDriverAttempt {
    val outcome = runCatching { phaseGates.reviewDriver.run(request) }
    val error = outcome.exceptionOrNull()
    if (error == null) {
      return ReviewDriverReady(outcome.getOrThrow())
    }
    val mapped: ReviewDriverAttempt? =
      when (error) {
        is CancellationException -> null
        is DiffResolutionException ->
          ReviewDriverFailed(
            "Runtime-owned review could not resolve the child-owned diff: ${error.message.orEmpty()}",
          )
        is UsageValidationException ->
          ReviewDriverFailed(
            "Runtime-owned review failed: ${error.message.orEmpty()}",
            FeatureTaskRuntimeFailureDisposition.RETRYABLE,
          )
        is StackDetectionException ->
          ReviewDriverFailed(
            "Runtime-owned review failed: ${error.message.orEmpty()}",
            FeatureTaskRuntimeFailureDisposition.RETRYABLE,
          )
        is ReviewContextBudgetExceededException ->
          ReviewDriverFailed(
            "Runtime-owned review exceeded a review-context budget: ${error.message.orEmpty()}",
          )
        is UnreadableSpecIntentProjectionError ->
          ReviewDriverFailed(
            "Runtime-owned review could not read the spec intent projection: ${error.message.orEmpty()}",
          )
        is InvalidReviewContextSchemaError ->
          ReviewDriverFailed(
            "Runtime-owned review produced an invalid review-context envelope: ${error.message.orEmpty()}",
          )
        is RuntimeOwnedFactUnavailable ->
          ReviewDriverFailed(
            "Runtime-owned review could not establish a required persistence fact: ${error.message.orEmpty()}",
            FeatureTaskRuntimeFailureDisposition.PROCESS_FAILURE,
          )
        is Exception ->
          ReviewDriverFailed(
            "Runtime-owned review failed: ${error::class.simpleName}: ${error.message.orEmpty()}",
            FeatureTaskRuntimeFailureDisposition.RETRYABLE,
          )
        else -> null
      }
    return mapped ?: throw error
  }

  internal fun FeatureTaskRuntimeRunLoopContext.settleReviewDriverResult(
    prepared: RuntimeOwnedReviewReady,
    result: ParallelCodeReviewResult,
    fileManifest: FeatureTaskRuntimePhaseFileManifest,
  ): PhaseOutcome {
    val run = prepared.run
    failedReviewLaneReason(result)?.let { reason ->
      return FeatureTaskRuntimeRunLoopPhaseBlocking.blockInPhase(
        request,
        state,
        recorder,
        observability,
        PhaseBlockRequest(
          run = run,
          attemptCount = prepared.launch.iteration,
          reason = reason,
          observability = observability,
          failureDisposition = FeatureTaskRuntimeFailureDisposition.RETRYABLE,
        ),
      )
    }
    return when (
      val settlement =
        prepareReviewSettlement(
          prepared,
          result,
          fileManifest,
        )
    ) {
      is ReviewSettlementPreparation.Blocked -> settlement.outcome
      is ReviewSettlementPreparation.Ready ->
        settleRuntimeOwnedReview(
          SettleRuntimeOwnedReviewArgs(
            run,
            prepared.launch.iteration,
            settlement.outputText,
            observability,
            fileManifest,
          ),
          settlement.acceptedOutput,
        )
    }
  }

  private fun FeatureTaskRuntimeRunLoopContext.prepareReviewSettlement(
    prepared: RuntimeOwnedReviewReady,
    result: ParallelCodeReviewResult,
    fileManifest: FeatureTaskRuntimePhaseFileManifest,
  ): ReviewSettlementPreparation {
    val run = prepared.run
    val initialCycle = assembleReviewCycle(recorder, prepared, result, prepared.launch.checkpoint)
    val acceptedOutput =
      runCatching {
        outputValidator.validatePhaseOutput(
          initialCycle.outputText,
          sourceLabel = run.phaseId,
        ).requireAcceptedOutput(run.phaseId)
      }.getOrElse { error ->
        return ReviewSettlementPreparation.Blocked(
          FeatureTaskRuntimeRunLoopPhaseBlocking.blockAndPersistInPhase(
            request,
            state,
            recorder,
            null,
            phaseBlockArgs(
              run,
              prepared.launch.iteration,
              "Runtime-owned review settlement did not validate: ${error.message.orEmpty()}",
              observability,
              payload = BlockAndPersistPayload(fileManifest = fileManifest),
            ),
          ),
        )
      }
    if (fileManifest.before == fileManifest.after) {
      return ReviewSettlementPreparation.Ready(initialCycle.outputText, acceptedOutput)
    }
    return when (
      val checkpoint =
        checkpointReviewChanges(
          run,
          prepared.launch.iteration,
        )
    ) {
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

  private fun assembleReviewCycle(
    recorder: FeatureTaskRuntimePhaseRecorder,
    prepared: RuntimeOwnedReviewReady,
    result: ParallelCodeReviewResult,
    fingerprint: String,
  ): FeatureTaskRuntimeReviewDriverCycleOutcome =
    FeatureTaskRuntimeReviewDriverCycle.assemble(
      result = result,
      request = prepared.driverRequest,
      cycle =
        FeatureTaskRuntimeReviewCycleContext(
          passNumber = prepared.launch.passNumber,
          resolvedTier = prepared.launch.resolvedTier,
          repositoryFingerprint = fingerprint,
          blockerDispositions =
            reviewBlockerDispositions(
              recorder,
              ReviewBlockerDispositionsArgs(
                run = prepared.run,
                passNumber = prepared.launch.passNumber,
                result = result,
                reviewRunId = prepared.launch.reviewRunId,
                resolvedTier = prepared.launch.resolvedTier,
              ),
            ),
        ),
    )

  private fun FeatureTaskRuntimeRunLoopContext.checkpointReviewChanges(
    run: PhaseRun,
    iteration: Int,
  ): ReviewCheckpointResult {
    val checkpointed =
      with(FeatureTaskRuntimeRunLoopCheckpointRemediation) {
        FeatureTaskRuntimeRunLoopCheckpointRemediation.checkpointEstablished(
          this@checkpointReviewChanges,
          precedingPhaseId = run.phaseId,
          loopId = null,
          intent = FeatureTaskRuntimeCheckpointMessage.INTENT_REMEDIATION,
          blockedReason = { branch, error ->
            "Feature-task-runtime could not amend review changes on the feature branch '$branch'" +
              (if (error.isBlank()) "." else " ($error).") +
              " Refusing to complete review with uncommitted review fixes."
          },
        )
      }
    if (!checkpointed) {
      return ReviewCheckpointResult.Blocked(
        FeatureTaskRuntimeRunLoopPhaseBlocking.blockInPhase(
          request,
          state,
          recorder,
          observability,
          PhaseBlockRequest(
            run = run,
            attemptCount = iteration,
            reason = "Runtime-owned review changes could not be committed before review settlement.",
            observability = observability,
            failureDisposition = FeatureTaskRuntimeFailureDisposition.PROCESS_FAILURE,
          ),
        ),
      )
    }
    val fingerprint =
      phaseGates.gitOperations.repositoryFingerprint(run.request.repoRoot).value
        .takeIf(String::isNotBlank)
        ?: return ReviewCheckpointResult.Blocked(
          FeatureTaskRuntimeRunLoopPhaseBlocking.blockInPhase(
            request,
            state,
            recorder,
            observability,
            PhaseBlockRequest(
              run = run,
              attemptCount = iteration,
              reason = "Runtime-owned review could not resolve the post-amend repository checkpoint fingerprint.",
              observability = observability,
              failureDisposition = FeatureTaskRuntimeFailureDisposition.PROCESS_FAILURE,
            ),
          ),
        )
    return ReviewCheckpointResult.Refreshed(fingerprint)
  }

  internal fun reviewBaselineUntrackedPaths(
    recorder: FeatureTaskRuntimePhaseRecorder,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    run: PhaseRun,
  ): List<String> =
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

  internal fun reviewBlockerDispositions(
    recorder: FeatureTaskRuntimePhaseRecorder,
    args: ReviewBlockerDispositionsArgs,
  ): List<GoalSubtaskBlockerDisposition> {
    val run = args.run
    val passNumber = args.passNumber
    val result = args.result
    val reviewRunId = args.reviewRunId
    val resolvedTier = args.resolvedTier
    if (passNumber < 2) return emptyList()
    val prior = recorder.fetchUnaddressedLedger(run.request.workflowId)
    if (prior.isEmpty()) return emptyList()
    val continuation = run.request.goalContinuation
    val envelope =
      FeatureTaskRuntimeReviewEnvelope.envelopeMap(
        FeatureTaskRuntimeReviewEnvelope.assemble(
          result = result,
          reviewRunId = reviewRunId,
          cycle =
            FeatureTaskRuntimeReviewCycleContext(
              passNumber = passNumber,
              resolvedTier = resolvedTier,
              repositoryFingerprint = "disposition-preview",
            ),
        ),
      )
    val verdicts = recorder.recordedFindingVerdicts(envelope)
    val current =
      GoalSubtaskReviewSummaryReducer.unaddressedFindings(
        output = envelope,
        scope =
          UnaddressedFindingLedgerScope(
            issueKey = continuation?.parentIssueKey ?: run.request.issueKey,
            subtaskId = continuation?.subtaskId ?: 0,
            workflowId = run.request.workflowId,
            reviewPassNumber = passNumber,
          ),
        recordedVerdicts = verdicts,
      )
    return GoalSubtaskReviewSummaryReducer.refutedBlockerSupersedes(prior, current, verdicts)
  }

  internal fun FeatureTaskRuntimeRunLoopContext.settleRuntimeOwnedReview(
    args: SettleRuntimeOwnedReviewArgs,
    acceptedOutput: AcceptedFeatureTaskRuntimePhaseOutput,
  ): PhaseOutcome {
    val run = args.run
    val iteration = args.iteration
    val outputText = args.outputText
    val fileManifest = args.fileManifest
    with(FeatureTaskRuntimeRunLoopReviewDriverSettlement) {
      retainRuntimeOwnedReviewEvidence(
        RetainRuntimeOwnedReviewEvidenceArgs(request, recorder, clock, run, state, iteration, outputText),
      )
      persistReviewCompletionOutcome(
        PersistReviewCompletionOutcomeArgs(
          request,
          state,
          recorder,
          observability,
          goalContinuationRecorder,
          PhaseReviewCompletionOutcomeArgs(
            persistence = PhaseReviewPersistenceArgs(run, iteration, observability, fileManifest),
            normalizedOutput = acceptedOutput.normalizedOutput,
            acceptedOutput = acceptedOutput,
            outputText = outputText,
          ),
        ),
      )?.let { return it }
      return completeRuntimeOwnedReviewPhase(
        observability,
        CompleteRuntimeOwnedReviewPhaseArgs(
          run = run,
          iteration = iteration,
          observability = observability,
          normalizedOutput = acceptedOutput.normalizedOutput,
          acceptedOutput = acceptedOutput,
        ),
      )
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
  val envelope =
    JsonCodec.anyToStringAnyMap(
      FeatureTaskRuntimeReviewEnvelope.envelopeMap(outputText),
    ).orEmpty().toMutableMap()
  val produced =
    JsonCodec.anyToStringAnyMap(envelope[SharedPayloadKeys.PRODUCED_OUTPUTS])
      .orEmpty()
      .toMutableMap()
  produced[FeatureTaskRuntimeVerificationSignalKeys.REPOSITORY_CHECKPOINT] =
    mapOf(
      FeatureTaskRuntimeVerificationSignalKeys.REPOSITORY_CHECKPOINT_FINGERPRINT to fingerprint,
    )
  envelope[SharedPayloadKeys.PRODUCED_OUTPUTS] = produced
  return copy(
    normalizedOutput =
      NormalizedFeatureTaskRuntimePhaseOutput(
        canonicalJson = JsonCodec.mapToJsonString(envelope),
        envelope = envelope,
      ),
  )
}

private fun String.repositoryCheckpointFingerprint(): String? =
  FeatureTaskRuntimeReviewEnvelope.envelopeMap(this)
    .let { JsonCodec.anyToStringAnyMap(it[SharedPayloadKeys.PRODUCED_OUTPUTS]).orEmpty() }
    .let { JsonCodec.anyToStringAnyMap(it[FeatureTaskRuntimeVerificationSignalKeys.REPOSITORY_CHECKPOINT]) }
    ?.get(FeatureTaskRuntimeVerificationSignalKeys.REPOSITORY_CHECKPOINT_FINGERPRINT)
    ?.toString()
    ?.takeIf(String::isNotBlank)

object FeatureTaskRuntimeRunLoopReviewDriverSettlement {
  internal fun retainRuntimeOwnedReviewEvidence(
    args: RetainRuntimeOwnedReviewEvidenceArgs,
  ) {
    val request = args.request
    val recorder = args.recorder
    val clock = args.clock
    val run = args.run
    val state = args.state
    val iteration = args.iteration
    val outputText = args.outputText
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

  internal fun persistReviewCompletionOutcome(
    args: PersistReviewCompletionOutcomeArgs,
  ): PhaseOutcome? {
    val request = args.request
    val state = args.state
    val recorder = args.recorder
    val observability = args.observability
    val goalContinuationRecorder = args.goalContinuationRecorder
    val outcome = args.outcome
    val persistenceContext =
      ReviewOutputPersistenceContext(
        request = request,
        state = state,
        recorder = recorder,
        observability = observability,
        goalContinuationRecorder = goalContinuationRecorder,
      )
    return with(FeatureTaskRuntimeRunLoopReviewCompletion) {
      if (isGoalReviewRun(outcome.persistence.run)) {
        persistenceContext.persistGoalReviewCompletion(
          outcome.persistence,
          outcome.normalizedOutput,
          outcome.acceptedOutput.repairEvidence,
        )
      } else {
        persistenceContext.persistStandaloneReviewCompletion(
          outcome.persistence,
          outcome.outputText,
          outcome.acceptedOutput,
        )
      }
    }
  }

  internal fun completeRuntimeOwnedReviewPhase(
    observability: FeatureTaskRuntimeRunObservability,
    args: CompleteRuntimeOwnedReviewPhaseArgs,
  ): PhaseOutcome {
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
