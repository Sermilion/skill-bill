package skillbill.engine.featuretask.runloop.core

import skillbill.engine.diagnostics.RuntimeDiagnosticsBestEffortWarning
import skillbill.engine.featuretask.lifecycle.checkpoint.FeatureTaskRuntimeCheckpointMessage
import skillbill.engine.featuretask.lifecycle.checkpoint.FeatureTaskRuntimeCheckpointMetadata
import skillbill.engine.featuretask.lifecycle.subtask.FeatureTaskRuntimeSubtaskFinalisation
import skillbill.engine.featuretask.lifecycle.subtask.FeatureTaskRuntimeSubtaskFinalisationHandoff
import skillbill.engine.featuretask.lifecycle.subtask.FeatureTaskRuntimeSubtaskFinaliseRequest
import skillbill.engine.featuretask.model.subtask.FeatureTaskRuntimeCommitPushHandoff
import skillbill.engine.featuretask.model.subtask.FeatureTaskRuntimeCommitPushReceipt
import skillbill.engine.featuretask.model.subtask.FeatureTaskRuntimeSubtaskCommitIdentity
import skillbill.engine.featuretask.model.subtask.FeatureTaskRuntimeSubtaskFinalisationBlocked
import skillbill.engine.featuretask.model.subtask.FeatureTaskRuntimeSubtaskFinalised
import skillbill.engine.featuretask.runloop.checkpoint.FeatureTaskRuntimeRunLoopCheckpoint
import skillbill.engine.featuretask.runloop.observability.FeatureTaskRuntimePhaseStartReentry
import skillbill.engine.featuretask.runloop.output.FeatureTaskRuntimeRunLoopOutputPersistence
import skillbill.engine.featuretask.runloop.phase.FeatureTaskRuntimeRunLoopPhaseAttempts
import skillbill.engine.featuretask.runner.STATUS_COMPLETED
import skillbill.engine.featuretask.runner.STATUS_RUNNING
import skillbill.engine.featuretask.validation.ReadinessCommitPushSettleRequest
import skillbill.engine.featuretask.validation.ReadinessCommitPushSettleResult
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.phase.AcceptedFeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeFailureDisposition
import skillbill.workflow.taskruntime.model.phase.requireAcceptedOutput

private data class FinaliseSubtaskArgs(
  val branch: String,
  val ledger: SubtaskCommitLedgerState,
  val identity: FeatureTaskRuntimeSubtaskCommitIdentity,
  val subject: String,
)

private data class BindCommittedHeadArgs(
  val run: PhaseRun,
  val iteration: Int,
  val branch: String,
  val baseBranch: String,
  val outcome: FeatureTaskRuntimeSubtaskFinalised,
)

object FeatureTaskRuntimeRunLoopCommitPush {
  internal fun FeatureTaskRuntimeRunLoopContext.runDeclaredCommitPushCycle(run: PhaseRun): PhaseOutcome {
    val iteration = state.nextIteration(run.phaseId)
    persistRunning(run, iteration)?.let { return it }
    observability.started(
      run.phaseId,
      run.resolvedAgent.resolvedAgentId,
      iteration,
      run.modelDirective,
      FeatureTaskRuntimePhaseStartReentry.FIRST_VISIT,
    )
    return settle(run, iteration)
  }

  internal fun runtimeOwnedCommitPushOutput(receipt: FeatureTaskRuntimeCommitPushReceipt): String =
    FeatureTaskRuntimeSubtaskFinalisationHandoff.runtimeOwnedOutput(receipt)

  private fun FeatureTaskRuntimeRunLoopContext.settle(
    run: PhaseRun,
    iteration: Int,
  ): PhaseOutcome {
    val branch =
      FeatureTaskRuntimeRunLoopSubtaskCommit.finalisationBranch(request, session, phaseGates)
        ?: return settleUnownedHead(run, iteration)
    val baseBranch = recorder.loadResolvedBranch(request.workflowId)?.baseBranch ?: "main"
    val readiness = commitPushReadiness(this, baseBranch)
    if (readiness is ReadinessCommitPushSettleResult.Blocked) {
      return FeatureTaskRuntimeRunLoopPhaseAttempts.blockAndPersistInPhase(
        request,
        state,
        recorder,
        goalContinuationRecorder,
        phaseBlockArgs(run, iteration, readiness.reason, observability)
          .copy(failureDisposition = readiness.failureDisposition),
      )
    }
    return finaliseAndBindCommitPush(this, run, iteration, branch, baseBranch)
  }

  private fun commitPushReadiness(
    context: FeatureTaskRuntimeRunLoopContext,
    baseBranch: String,
  ): ReadinessCommitPushSettleResult {
    val changedPaths =
      FeatureTaskRuntimeRunLoopSubtaskCommit.commitPushChangedPaths(
        context.request,
        context.phaseGates,
        baseBranch,
      )
    return context.phaseGates.readinessGateCoordinator.settleBeforeCommitPush(
      ReadinessCommitPushSettleRequest(
        workflowId = context.request.workflowId,
        repoRoot = context.request.repoRoot,
        baseBranch = baseBranch,
        changedPaths = changedPaths.paths,
        changedPathsError = changedPaths.error,
        gitOperations = context.phaseGates.gitOperations,
      ),
    )
  }

  private fun finaliseAndBindCommitPush(
    context: FeatureTaskRuntimeRunLoopContext,
    run: PhaseRun,
    iteration: Int,
    branch: String,
    baseBranch: String,
  ): PhaseOutcome {
    val identity = FeatureTaskRuntimeRunLoopCheckpoint.subtaskCommitIdentity(context.request)
    val ledger =
      FeatureTaskRuntimeRunLoopCheckpoint.subtaskCommitLedgerState(
        context.request,
        context.recorder,
        context.diagnostics,
        identity,
      )
    val outcome =
      finaliseSubtask(
        context,
        run,
        FinaliseSubtaskArgs(branch, ledger, identity, context.commitSubject(identity.subtaskId)),
      )
    return when (outcome) {
      is FeatureTaskRuntimeSubtaskFinalisationBlocked -> context.block(run, iteration, outcome.reason)
      is FeatureTaskRuntimeSubtaskFinalised ->
        bindCommittedHead(
          context,
          BindCommittedHeadArgs(run, iteration, branch, baseBranch, outcome),
        )
    }
  }

  private fun bindCommittedHead(
    context: FeatureTaskRuntimeRunLoopContext,
    args: BindCommittedHeadArgs,
  ): PhaseOutcome {
    val rebound =
      context.phaseGates.readinessGateCoordinator.bindCommittedHead(
        workflowId = context.request.workflowId,
        repoRoot = context.request.repoRoot,
        baseBranch = args.baseBranch,
        gitOperations = context.phaseGates.gitOperations,
        commitSha = args.outcome.commitSha,
      )
    return if (rebound is ReadinessCommitPushSettleResult.Blocked) {
      context.block(args.run, args.iteration, rebound.reason)
    } else {
      context.complete(
        args.run,
        args.iteration,
        runtimeOwnedCommitPushOutput(
          FeatureTaskRuntimeCommitPushReceipt(
            commitSha = args.outcome.commitSha,
            branch = args.branch,
            baseBranch = args.baseBranch,
            pushed = !context.request.deferRemotePublication,
          ),
        ),
      )
    }
  }

  private fun finaliseSubtask(
    context: FeatureTaskRuntimeRunLoopContext,
    run: PhaseRun,
    args: FinaliseSubtaskArgs,
  ) = FeatureTaskRuntimeSubtaskFinalisation(
    gitOperations = context.phaseGates.gitOperations,
    repoRoot = context.request.repoRoot,
    record = { record -> RuntimeDiagnosticsBestEffortWarning.record(context.diagnostics, record) },
    recordCommit = { commitSha, stagedPaths ->
      FeatureTaskRuntimeRunLoopSubtaskCommit.recordFinalisedCheckpointIdentity(
        context.request,
        context.state,
        context.recorder,
        context.diagnostics,
        RecordFinalisedCheckpointIdentityArgs(
          run.phaseId,
          args.branch,
          args.ledger,
          commitSha,
          stagedPaths,
        ),
      )
    },
  ).finalise(
    FeatureTaskRuntimeSubtaskFinaliseRequest(
      identity = args.identity,
      durableCommitSha = args.ledger.commitSha,
      sequenceNumber = args.ledger.nextSequenceNumber,
      handoff = FeatureTaskRuntimeCommitPushHandoff(outcomeMessage = args.subject, changedPaths = emptyList()),
      metadata =
        FeatureTaskRuntimeCheckpointMetadata(
          phaseId = run.phaseId,
          loopId = null,
          generation = FeatureTaskRuntimeRunLoopCheckpoint.checkpointGeneration(context.state, null),
          branch = args.branch,
          intent = FeatureTaskRuntimeCheckpointMessage.INTENT_FINALISED_SUBTASK,
        ),
      deferRemotePublication = context.request.deferRemotePublication,
    ),
  )

  private fun FeatureTaskRuntimeRunLoopContext.commitSubject(subtaskId: String): String {
    val subtaskName = request.goalContinuation?.subtaskName?.trim()?.takeIf(String::isNotBlank)
    if (subtaskName == null && request.goalContinuation != null) {
      RuntimeDiagnosticsBestEffortWarning.record(
        diagnostics,
        FeatureTaskRuntimeCheckpointMessage.missingSubtaskNameRecord(request.issueKey, subtaskId),
      )
    }
    return FeatureTaskRuntimeCheckpointMessage.subject(request.issueKey, subtaskName, subtaskId)
  }

  private fun FeatureTaskRuntimeRunLoopContext.settleUnownedHead(
    run: PhaseRun,
    iteration: Int,
  ): PhaseOutcome {
    val accepted =
      accept(
        run,
        runtimeOwnedCommitPushOutput(FeatureTaskRuntimeCommitPushReceipt(commitSha = null)),
      ).getOrElse { error ->
        return block(
          run,
          iteration,
          "Runtime-owned commit_push settlement did not validate: ${error.message.orEmpty()}",
        )
      }
    return when (
      val unowned =
        FeatureTaskRuntimeRunLoopSubtaskCommit.unownedWorktreeCommitSha(
          UnownedWorktreeCommitShaArgs(
            request,
            outputValidator,
            diagnostics,
            phaseGates,
            run,
            accepted.normalizedOutput,
          ),
        )
    ) {
      is CommitPushSettled -> complete(run, iteration, unowned.output.canonicalJson)
      is CommitPushBlocked -> block(run, iteration, unowned.reason)
      CommitPushNotApplicable ->
        block(run, iteration, "commit_push could not resolve a branch or measure HEAD for commit_sha.")
    }
  }

  private fun FeatureTaskRuntimeRunLoopContext.complete(
    run: PhaseRun,
    iteration: Int,
    outputText: String,
  ): PhaseOutcome {
    val accepted =
      accept(run, outputText).getOrElse { error ->
        return block(
          run,
          iteration,
          "Runtime-owned commit_push settlement did not validate: ${error.message.orEmpty()}",
        )
      }
    val normalizedOutput = accepted.normalizedOutput
    if (!persistCompleted(run, iteration, outputText, accepted)) {
      return FeatureTaskRuntimeRunLoopPhaseAttempts.blockInPhase(
        request,
        state,
        recorder,
        observability,
        PhaseBlockRequest(
          run = run,
          attemptCount = iteration,
          reason = "Runtime-owned commit_push settlement could not be persisted.",
          observability = observability,
          failureDisposition = FeatureTaskRuntimeFailureDisposition.PROCESS_FAILURE,
        ),
      )
    }
    observability.completed(run.phaseId, run.resolvedAgent.resolvedAgentId, iteration)
    return PhaseOutcome.completed(
      FeatureTaskRuntimePhaseOutput(
        run.phaseId,
        iteration,
        normalizedOutput.canonicalJson,
        normalizedOutput,
        accepted.repairEvidence,
      ),
    )
  }

  private fun FeatureTaskRuntimeRunLoopContext.persistRunning(
    run: PhaseRun,
    iteration: Int,
  ): PhaseOutcome? {
    val runningPhaseState =
      FeatureTaskRuntimeRunLoopOutputPersistence.phaseStateRequest(
        request,
        state,
        goalContinuationRecorder,
        PhaseStateRequestArgs(
          write =
            PhaseStateWriteArgs(
              run = run,
              iteration = iteration,
              status = STATUS_RUNNING,
              finished = false,
              outputArtifact = null,
            ),
        ),
      )
    state.reserveReviewPass(runningPhaseState.reviewPassNumber)
    if (!recorder.recordPhaseState(runningPhaseState)) {
      return FeatureTaskRuntimeRunLoopPhaseAttempts.blockInPhase(
        request,
        state,
        recorder,
        observability,
        PhaseBlockRequest(
          run = run,
          attemptCount = iteration,
          reason = "Commit-push cycle could not persist running phase before finalisation.",
          observability = observability,
          failureDisposition = FeatureTaskRuntimeFailureDisposition.PROCESS_FAILURE,
        ),
      )
    }
    return null
  }

  private fun FeatureTaskRuntimeRunLoopContext.persistCompleted(
    run: PhaseRun,
    iteration: Int,
    outputText: String,
    acceptedOutput: AcceptedFeatureTaskRuntimePhaseOutput,
  ): Boolean =
    recorder.recordCompletedPhase(
      FeatureTaskRuntimeRunLoopOutputPersistence.phaseStateRequest(
        request,
        state,
        goalContinuationRecorder,
        PhaseStateRequestArgs(
          write =
            PhaseStateWriteArgs(
              run = run,
              iteration = iteration,
              status = STATUS_COMPLETED,
              finished = true,
              outputArtifact = outputText,
            ),
          extras =
            PhaseStateRequestAttachments(
              normalizedOutput = acceptedOutput.normalizedOutput,
              repairEvidence = acceptedOutput.repairEvidence,
            ),
        ),
      ),
    )

  private fun FeatureTaskRuntimeRunLoopContext.accept(
    run: PhaseRun,
    outputText: String,
  ): Result<AcceptedFeatureTaskRuntimePhaseOutput> =
    runCatching {
      outputValidator.validatePhaseOutput(outputText, sourceLabel = run.phaseId).requireAcceptedOutput(run.phaseId)
    }

  private fun FeatureTaskRuntimeRunLoopContext.block(
    run: PhaseRun,
    iteration: Int,
    reason: String,
  ): PhaseOutcome =
    FeatureTaskRuntimeRunLoopPhaseAttempts.blockAndPersistInPhase(
      request,
      state,
      recorder,
      goalContinuationRecorder,
      phaseBlockArgs(run, iteration, reason, observability),
    )
}
