package skillbill.engine.featuretask.runloop.output

import skillbill.engine.diagnostics.RuntimeDiagnosticsBestEffortWarning
import skillbill.engine.featuretask.lifecycle.continuation.FeatureTaskRuntimeGoalContinuationRecorder
import skillbill.engine.featuretask.lifecycle.continuation.reviewState
import skillbill.engine.featuretask.lifecycle.remediation.FeatureTaskRuntimeRepairReceiptMissing
import skillbill.engine.featuretask.lifecycle.remediation.FeatureTaskRuntimeRepairReceiptRejected
import skillbill.engine.featuretask.lifecycle.remediation.FeatureTaskRuntimeRepairReceiptValid
import skillbill.engine.featuretask.lifecycle.remediation.featureTaskRuntimeParseRepairReceipt
import skillbill.engine.featuretask.lifecycle.remediation.featureTaskRuntimeRemediationRoundNumberOrNull
import skillbill.engine.featuretask.lifecycle.remediation.featureTaskRuntimeRepairReceiptSettleRejection
import skillbill.engine.featuretask.lifecycle.remediation.featureTaskRuntimeRepairReceiptShapeRejection
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeRunRequest
import skillbill.engine.featuretask.phase.record.FeatureTaskRuntimePhaseRecorder
import skillbill.engine.featuretask.runloop.checkpoint.FeatureTaskRuntimeRunLoopCheckpoint
import skillbill.engine.featuretask.runloop.checkpoint.FeatureTaskRuntimeRunLoopCheckpointRemediation
import skillbill.engine.featuretask.runloop.core.AttemptResult
import skillbill.engine.featuretask.runloop.core.BlockAndPersistPayload
import skillbill.engine.featuretask.runloop.core.CheckpointCommitMessageArgs
import skillbill.engine.featuretask.runloop.core.CommitCheckpointArgs
import skillbill.engine.featuretask.runloop.core.CompletedImplementationOutputArgs
import skillbill.engine.featuretask.runloop.core.FeatureTaskRuntimeRunLoopContext
import skillbill.engine.featuretask.runloop.core.FeatureTaskRuntimeRunLoopPlanningBranch
import skillbill.engine.featuretask.runloop.core.FeatureTaskRuntimeRunLoopSession
import skillbill.engine.featuretask.runloop.core.ImplementFixRepairReceiptArgs
import skillbill.engine.featuretask.runloop.core.PhaseBlockRequest
import skillbill.engine.featuretask.runloop.core.PhaseRun
import skillbill.engine.featuretask.runloop.core.RecordCheckpointIdentityArgs
import skillbill.engine.featuretask.runloop.core.RepairReceiptAnchor
import skillbill.engine.featuretask.runloop.core.RepairReceiptSettlement
import skillbill.engine.featuretask.runloop.phase.FeatureTaskRuntimeRunLoopPhaseAttempts
import skillbill.engine.featuretask.runloop.state.FeatureTaskRuntimeRunState
import skillbill.goalrunner.model.UNADDRESSED_FINDING_REJECTED_DISPOSITION
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.ports.workflow.gitops.captureIndexState
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import skillbill.ports.workflow.gitops.stagePaths
import skillbill.workflow.model.goalreview.FeatureTaskRuntimeRepairReceipt
import skillbill.workflow.model.goalreview.GoalSubtaskReviewState
import skillbill.workflow.model.goalreview.upsertRepairReceipt
import skillbill.workflow.taskruntime.artifact.envelopeWireMap
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeWorkflowArtifactMap
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeFailureDisposition

internal data class RepairReceiptSettlementArgs(
  val request: FeatureTaskRuntimeRunRequest,
  val state: FeatureTaskRuntimeRunState,
  val recorder: FeatureTaskRuntimePhaseRecorder,
  val goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
  val diagnostics: RuntimeDiagnostics,
  val run: PhaseRun,
  val outputMap: FeatureTaskRuntimeWorkflowArtifactMap,
)

internal data class SettledRepairReceiptArgs(
  val request: FeatureTaskRuntimeRunRequest,
  val state: FeatureTaskRuntimeRunState,
  val recorder: FeatureTaskRuntimePhaseRecorder,
  val goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
  val diagnostics: RuntimeDiagnostics,
  val receipt: FeatureTaskRuntimeRepairReceipt,
  val reviewState: GoalSubtaskReviewState,
)

internal data class CompletedImplementationSettlementArgs(
  val request: FeatureTaskRuntimeRunRequest,
  val state: FeatureTaskRuntimeRunState,
  val recorder: FeatureTaskRuntimePhaseRecorder,
  val goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
  val diagnostics: RuntimeDiagnostics,
  val output: CompletedImplementationOutputArgs,
)

object FeatureTaskRuntimeRunLoopRepairReceipt {
  internal fun persistImplementFixRepairReceipt(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    diagnostics: RuntimeDiagnostics,
    receipt: FeatureTaskRuntimeRepairReceipt,
  ): String? =
    runCatching {
      goalContinuationRecorder.updateReviewState(
        request.workflowId,
      ) { state ->
        state.upsertRepairReceipt(receipt)
      }
    }.fold(
      onSuccess = { recorded ->
        if (recorded != null) null else "the review persistence.state could not be updated with the repair receipt."
      },
      onFailure = { error ->
        recordRepairReceiptWriteFailure(request, diagnostics, error)
        "the review persistence.state could not be updated with the repair receipt."
      },
    )

  internal fun recordRepairReceiptWriteFailure(
    request: FeatureTaskRuntimeRunRequest,
    diagnostics: RuntimeDiagnostics,
    error: Throwable,
  ) {
    RuntimeDiagnosticsBestEffortWarning.record(
      diagnostics,
      "Feature-task-runtime could not persist the implement_fix repair receipt for issue " +
        "${request.issueKey}, workflow ${request.workflowId}.",
      error,
    )
  }

  internal fun settleAndPersistImplementFixRepairReceipt(args: ImplementFixRepairReceiptArgs): AttemptResult? {
    val request = args.request
    val state = args.state
    val recorder = args.recorder
    val goalContinuationRecorder = args.goalContinuationRecorder
    val diagnostics = args.diagnostics
    val run = args.run
    val outputMap = args.normalizedOutput.envelopeWireMap()
    val reject = args.reject
    val iteration = args.iteration
    val observability = args.observability
    val fileManifest = args.fileManifest
    val settlement =
      implementFixRepairReceiptSettlement(
        RepairReceiptSettlementArgs(
          request,
          state,
          recorder,
          goalContinuationRecorder,
          diagnostics,
          run,
          outputMap,
        ),
      )
    settlement.rejectionDetail?.let { detail -> return reject("repair-receipt", detail) }
    val writeFailure = settlement.writeFailureReason ?: return null
    return AttemptResult.settled(
      FeatureTaskRuntimeRunLoopPhaseAttempts.blockInPhase(
        request,
        state,
        recorder,
        observability,
        PhaseBlockRequest(
          run = run,
          attemptCount = iteration,
          reason = writeFailure,
          observability = observability,
          payload = BlockAndPersistPayload(fileManifest = fileManifest),
          failureDisposition = FeatureTaskRuntimeFailureDisposition.PROCESS_FAILURE,
        ),
      ),
    )
  }

  internal fun implementFixRepairReceiptSettlement(args: RepairReceiptSettlementArgs): RepairReceiptSettlement {
    val request = args.request
    val state = args.state
    val recorder = args.recorder
    val goalContinuationRecorder = args.goalContinuationRecorder
    val diagnostics = args.diagnostics
    val run = args.run
    val outputMap = args.outputMap
    val produced =
      FeatureTaskRuntimeRunLoopCheckpointRemediation.completedImplementFixProducedOutputs(
        run,
        outputMap,
      ) ?: return RepairReceiptSettlement.None
    val reviewState =
      FeatureTaskRuntimeRunLoopPlanningBranch.goalReviewStateOrNull(
        request,
        goalContinuationRecorder,
      )
        ?: return repairReceiptShapeSettlement(produced)
    val anchor = repairReceiptAnchor(request, diagnostics, reviewState) ?: return repairReceiptShapeSettlement(produced)
    return when (
      val parsed =
        featureTaskRuntimeParseRepairReceipt(
          produced,
          anchor.baseSha,
          anchor.roundNumber,
          recordTruncation = { record -> RuntimeDiagnosticsBestEffortWarning.record(diagnostics, record) },
        )
    ) {
      FeatureTaskRuntimeRepairReceiptMissing -> RepairReceiptSettlement.None
      is FeatureTaskRuntimeRepairReceiptRejected -> RepairReceiptSettlement.rejected(parsed.rejectionDetail)
      is FeatureTaskRuntimeRepairReceiptValid ->
        settledRepairReceipt(
          SettledRepairReceiptArgs(
            request,
            state,
            recorder,
            goalContinuationRecorder,
            diagnostics,
            parsed.receipt,
            reviewState,
          ),
        )
    }
  }

  internal fun settledRepairReceipt(args: SettledRepairReceiptArgs): RepairReceiptSettlement =
    featureTaskRuntimeRepairReceiptSettleRejection(
      args.receipt,
      args.reviewState,
      refutedCarriedFindingIds(args.request, args.recorder, args.diagnostics, args.reviewState),
    )
      ?.let { detail -> RepairReceiptSettlement.rejected(detail) }
      ?: persistImplementFixRepairReceipt(
        args.request,
        args.state,
        args.goalContinuationRecorder,
        args.diagnostics,
        args.receipt,
      )?.let { reason -> RepairReceiptSettlement.writeFailed(reason) }
      ?: RepairReceiptSettlement.None

  internal fun refutedCarriedFindingIds(
    request: FeatureTaskRuntimeRunRequest,
    recorder: FeatureTaskRuntimePhaseRecorder,
    diagnostics: RuntimeDiagnostics,
    reviewState: GoalSubtaskReviewState,
  ): Set<String> {
    val passNumber = reviewState.passResults.lastOrNull()?.passNumber ?: return emptySet()
    return runCatching {
      recorder.fetchUnaddressedLedger(request.workflowId)
        .asSequence()
        .filter { finding -> finding.reviewPassNumber == passNumber }
        .filter { finding -> finding.verificationDisposition == UNADDRESSED_FINDING_REJECTED_DISPOSITION }
        .mapNotNull { finding -> finding.findingId?.takeIf(String::isNotBlank) }
        .toSet()
    }.getOrElse { error ->
      RuntimeDiagnosticsBestEffortWarning.record(
        diagnostics,
        "Feature-task-runtime could not read the unaddressed-findings ledger for issue " +
          "${request.issueKey}, workflow ${request.workflowId}; repair-receipt coverage waives no " +
          "refuted finding for this round.",
        error,
      )
      emptySet()
    }
  }

  internal fun repairReceiptShapeSettlement(produced: Map<String, Any?>): RepairReceiptSettlement =
    featureTaskRuntimeRepairReceiptShapeRejection(produced)
      ?.let { detail -> RepairReceiptSettlement.rejected(detail) }
      ?: RepairReceiptSettlement.None

  internal fun repairReceiptAnchor(
    request: FeatureTaskRuntimeRunRequest,
    diagnostics: RuntimeDiagnostics,
    reviewState: GoalSubtaskReviewState,
  ): RepairReceiptAnchor? {
    val baseSha = reviewState.remediationBaseSha
    val roundNumber = featureTaskRuntimeRemediationRoundNumberOrNull(reviewState)
    if (baseSha == null || roundNumber == null) {
      recordRepairReceiptDegradation(
        request,
        diagnostics,
        if (baseSha == null) {
          "no durable remediation base sha was recorded for this round"
        } else {
          "the durable remediation round number is not yet established"
        },
      )
      return null
    }
    return RepairReceiptAnchor(baseSha = baseSha, roundNumber = roundNumber)
  }

  internal fun recordRepairReceiptDegradation(
    request: FeatureTaskRuntimeRunRequest,
    diagnostics: RuntimeDiagnostics,
    reason: String,
  ) {
    RuntimeDiagnosticsBestEffortWarning.record(
      diagnostics,
      "Feature-task-runtime did not record the implement_fix repair receipt for issue " +
        "${request.issueKey}, workflow ${request.workflowId}: $reason. The remediation repair " +
        "ledger loses this round.",
    )
  }

  internal fun settleCompletedImplementationOutput(args: CompletedImplementationSettlementArgs): AttemptResult? =
    settleAndPersistImplementFixRepairReceipt(
      ImplementFixRepairReceiptArgs(
        request = args.request,
        state = args.state,
        recorder = args.recorder,
        goalContinuationRecorder = args.goalContinuationRecorder,
        diagnostics = args.diagnostics,
        run = args.output.run,
        normalizedOutput = args.output.normalizedOutput,
        reject = args.output.reject,
        iteration = args.output.iteration,
        observability = args.output.observability,
        fileManifest = args.output.fileManifest,
      ),
    )

  internal fun blockRemediationBaseSha(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    session: FeatureTaskRuntimeRunLoopSession,
    precedingPhaseId: String,
    error: String,
  ): Boolean {
    FeatureTaskRuntimeRunLoopPlanningBranch.blockAt(
      request,
      state,
      session,
      precedingPhaseId,
      "Feature-task-runtime could not record the pre-fix remediation base sha before re-entering " +
        "implement_fix" + (if (error.isBlank()) "." else " ($error).") +
        " Without it the reserved remediation pass would silently review the full base-to-current " +
        "delta instead of the remediation delta.",
    )
    return false
  }

  private fun blockCheckpointAfterIndexMutation(
    context: FeatureTaskRuntimeRunLoopContext,
    args: CommitCheckpointArgs,
    error: String,
    indexSnapshot: String,
  ): Boolean =
    with(FeatureTaskRuntimeRunLoopCheckpoint) {
      FeatureTaskRuntimeRunLoopCheckpoint.blockCheckpoint(
        context,
        args.precedingPhaseId,
        args.branch,
        FeatureTaskRuntimeRunLoopCheckpoint.withIndexRestoreOutcome(
          context.request,
          context.phaseGates,
          error,
          args.ownedPaths,
          indexSnapshot,
        ),
        args.blockedReason,
      )
    }

  internal fun commitCheckpoint(
    context: FeatureTaskRuntimeRunLoopContext,
    args: CommitCheckpointArgs,
  ): Boolean {
    with(context) {
      val snapshot = phaseGates.gitOperations.captureIndexState(request.repoRoot, args.ownedPaths)
      if (snapshot !is WorkflowGitOperationResult.Ok) {
        return with(FeatureTaskRuntimeRunLoopCheckpoint) {
          FeatureTaskRuntimeRunLoopCheckpoint.blockCheckpoint(
            context,
            args.precedingPhaseId,
            args.branch,
            snapshot.error,
            args.blockedReason,
          )
        }
      }
      val parentSha =
        phaseGates.gitOperations.headCommitSha(request.repoRoot)
          .takeIf { it is WorkflowGitOperationResult.Ok }?.value?.trim()?.takeIf(String::isNotBlank)
      val attempt = FeatureTaskRuntimeRunLoopRepairReceipt.stageAndWriteCheckpoint(context, args)
      val commitSha =
        attempt.commitSha
          ?: return FeatureTaskRuntimeRunLoopRepairReceipt.blockCheckpointAfterIndexMutation(
            context,
            args,
            attempt.error,
            snapshot.value.orEmpty(),
          )
      return with(FeatureTaskRuntimeRunLoopCheckpoint) {
        FeatureTaskRuntimeRunLoopCheckpoint.recordCheckpointIdentity(
          context,
          RecordCheckpointIdentityArgs(
            precedingPhaseId = args.precedingPhaseId,
            branch = args.branch,
            loopId = args.loopId,
            ownedPaths = args.ownedPaths,
            parentSha = parentSha,
            commitSha = commitSha,
            blockedReason = args.blockedReason,
          ),
        )
      }
    }
  }

  private fun stageAndWriteCheckpoint(
    context: FeatureTaskRuntimeRunLoopContext,
    args: CommitCheckpointArgs,
  ): CheckpointCommitAttempt {
    with(context) {
      val staged = phaseGates.gitOperations.stagePaths(request.repoRoot, args.ownedPaths)
      if (staged !is WorkflowGitOperationResult.Ok) {
        return CheckpointCommitAttempt(commitSha = null, error = staged.error)
      }
      val subtaskIdentity = FeatureTaskRuntimeRunLoopCheckpoint.subtaskCommitIdentity(request)
      val message =
        FeatureTaskRuntimeRunLoopCheckpoint.checkpointCommitMessage(
          request,
          state,
          diagnostics,
          CheckpointCommitMessageArgs(
            branch = args.branch,
            phaseId = args.precedingPhaseId,
            loopId = args.loopId,
            identity = subtaskIdentity,
            intent = args.intent,
          ),
        )
      val commit =
        with(FeatureTaskRuntimeRunLoopCheckpoint) {
          FeatureTaskRuntimeRunLoopCheckpoint.writeSubtaskCommit(context, args.branch, message, subtaskIdentity)
        }
      if (commit !is WorkflowGitOperationResult.Ok) {
        return CheckpointCommitAttempt(commitSha = null, error = commit.error)
      }
      val commitSha = commit.value.orEmpty().trim()
      return if (commitSha.isBlank()) {
        CheckpointCommitAttempt(commitSha = null, error = "the checkpoint commit returned an empty sha")
      } else {
        CheckpointCommitAttempt(commitSha = commitSha, error = "")
      }
    }
  }
}

private data class CheckpointCommitAttempt(val commitSha: String?, val error: String)
