package skillbill.engine.featuretask

import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunRequest
import skillbill.goalrunner.model.UNADDRESSED_FINDING_REJECTED_DISPOSITION
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.ports.workflow.gitops.captureIndexState
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import skillbill.ports.workflow.gitops.stagePaths
import skillbill.workflow.goal.model.GoalSubtaskReviewState
import skillbill.workflow.taskruntime.FeatureTaskRuntimeWorkflowArtifactMap
import skillbill.workflow.taskruntime.envelopeWireMap
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeFailureDisposition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRepairReceipt
import skillbill.workflow.taskruntime.model.upsertRepairReceipt

object FeatureTaskRuntimeRunLoopRepairReceipt {
  internal fun persistImplementFixRepairReceipt(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    diagnostics: RuntimeDiagnostics,
    receipt: FeatureTaskRuntimeRepairReceipt,
  ): String? = runCatching {
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
    diagnostics.warning(
      "Feature-task-runtime could not persist the implement_fix repair receipt for issue " +
        "${request.issueKey}, workflow ${request.workflowId}.",
      error,
    )
  }

  internal fun FeatureTaskRuntimeRunLoopContext.settleAndPersistImplementFixRepairReceipt(
    args: ImplementFixRepairReceiptArgs,
  ): AttemptResult? {
    val run = args.run
    val outputMap = args.normalizedOutput.envelopeWireMap()
    val reject = args.reject
    val iteration = args.iteration
    val observability = args.observability
    val fileManifest = args.fileManifest
    val settlement = implementFixRepairReceiptSettlement(run, outputMap)
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

  internal fun FeatureTaskRuntimeRunLoopContext.implementFixRepairReceiptSettlement(
    run: PhaseRun,
    outputMap: FeatureTaskRuntimeWorkflowArtifactMap,
  ): RepairReceiptSettlement {
    val produced = FeatureTaskRuntimeRunLoopCheckpointRemediation.completedImplementFixProducedOutputs(
      run,
      outputMap,
    ) ?: return RepairReceiptSettlement.None
    val reviewState = FeatureTaskRuntimeRunLoopPlanningBranch.goalReviewStateOrNull(
      request,
      goalContinuationRecorder,
    )
      ?: return repairReceiptShapeSettlement(produced)
    val anchor = repairReceiptAnchor(request, diagnostics, reviewState) ?: return repairReceiptShapeSettlement(produced)
    return when (
      val parsed = featureTaskRuntimeParseRepairReceipt(
        produced,
        anchor.baseSha,
        anchor.roundNumber,
        recordTruncation = { record -> runCatching { diagnostics.warning(record) } },
      )
    ) {
      FeatureTaskRuntimeRepairReceiptMissing -> RepairReceiptSettlement.None
      is FeatureTaskRuntimeRepairReceiptRejected -> RepairReceiptSettlement.rejected(parsed.rejectionDetail)
      is FeatureTaskRuntimeRepairReceiptValid -> settledRepairReceipt(parsed.receipt, reviewState)
    }
  }

  internal fun FeatureTaskRuntimeRunLoopContext.settledRepairReceipt(
    receipt: FeatureTaskRuntimeRepairReceipt,
    reviewState: GoalSubtaskReviewState,
  ): RepairReceiptSettlement = featureTaskRuntimeRepairReceiptSettleRejection(
    receipt,
    reviewState,
    refutedCarriedFindingIds(request, recorder, diagnostics, reviewState),
  )
    ?.let { detail -> RepairReceiptSettlement.rejected(detail) }
    ?: persistImplementFixRepairReceipt(
      request,
      state,
      goalContinuationRecorder,
      diagnostics,
      receipt,
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
      diagnostics.warning(
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
    runCatching {
      diagnostics.warning(
        "Feature-task-runtime did not record the implement_fix repair receipt for issue " +
          "${request.issueKey}, workflow ${request.workflowId}: $reason. The remediation repair " +
          "ledger loses this round.",
      )
    }
  }

  internal fun FeatureTaskRuntimeRunLoopContext.settleCompletedImplementationOutput(
    args: CompletedImplementationOutputArgs,
  ): AttemptResult? = settleAndPersistImplementFixRepairReceipt(
    ImplementFixRepairReceiptArgs(
      run = args.run,
      normalizedOutput = args.normalizedOutput,
      reject = args.reject,
      iteration = args.iteration,
      observability = args.observability,
      fileManifest = args.fileManifest,
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

  private fun FeatureTaskRuntimeRunLoopContext.blockCheckpointAfterIndexMutation(
    args: CommitCheckpointArgs,
    error: String,
    indexSnapshot: String,
  ): Boolean = with(FeatureTaskRuntimeRunLoopCheckpoint) {
    this@blockCheckpointAfterIndexMutation.blockCheckpoint(
      args.precedingPhaseId,
      args.branch,
      FeatureTaskRuntimeRunLoopCheckpoint.withIndexRestoreOutcome(
        request,
        phaseGates,
        error,
        args.ownedPaths,
        indexSnapshot,
      ),
      args.blockedReason,
    )
  }

  internal fun FeatureTaskRuntimeRunLoopContext.commitCheckpoint(args: CommitCheckpointArgs): Boolean {
    val snapshot = phaseGates.gitOperations.captureIndexState(request.repoRoot, args.ownedPaths)
    if (snapshot !is WorkflowGitOperationResult.Ok) {
      return with(FeatureTaskRuntimeRunLoopCheckpoint) {
        this@commitCheckpoint.blockCheckpoint(
          args.precedingPhaseId,
          args.branch,
          snapshot.error,
          args.blockedReason,
        )
      }
    }
    val parentSha = phaseGates.gitOperations.headCommitSha(request.repoRoot)
      .takeIf { it is WorkflowGitOperationResult.Ok }?.value?.trim()?.takeIf(String::isNotBlank)
    val attempt = stageAndWriteCheckpoint(args)
    val commitSha = attempt.commitSha
      ?: return blockCheckpointAfterIndexMutation(args, attempt.error, snapshot.value.orEmpty())
    return with(FeatureTaskRuntimeRunLoopCheckpoint) {
      this@commitCheckpoint.recordCheckpointIdentity(
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

  private fun FeatureTaskRuntimeRunLoopContext.stageAndWriteCheckpoint(
    args: CommitCheckpointArgs,
  ): CheckpointCommitAttempt {
    val staged = phaseGates.gitOperations.stagePaths(request.repoRoot, args.ownedPaths)
    if (staged !is WorkflowGitOperationResult.Ok) {
      return CheckpointCommitAttempt(commitSha = null, error = staged.error)
    }
    val subtaskIdentity = FeatureTaskRuntimeRunLoopCheckpoint.subtaskCommitIdentity(request)
    val message = FeatureTaskRuntimeRunLoopCheckpoint.checkpointCommitMessage(
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
    val commit = with(FeatureTaskRuntimeRunLoopCheckpoint) {
      this@stageAndWriteCheckpoint.writeSubtaskCommit(args.branch, message, subtaskIdentity)
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

private data class CheckpointCommitAttempt(val commitSha: String?, val error: String)
