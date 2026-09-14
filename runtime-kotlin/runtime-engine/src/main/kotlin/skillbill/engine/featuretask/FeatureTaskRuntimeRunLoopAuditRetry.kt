package skillbill.engine.featuretask

import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.model.workflowStepStatus
import skillbill.workflow.taskruntime.FeatureTaskRuntimeAuditRemainingAcInterpretation
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.FeatureTaskRuntimeWorkflowArtifactMap
import skillbill.workflow.taskruntime.envelopeWireMap
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeAuditRemainingAcResult
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeFailureDisposition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeVerdict
import skillbill.workflow.taskruntime.model.NormalizedFeatureTaskRuntimePhaseOutput

object FeatureTaskRuntimeRunLoopAuditRetry {
  internal fun interpretCompletedAuditValue(value: String?): FeatureTaskRuntimeAuditRemainingAcResult =
    FeatureTaskRuntimeAuditRemainingAcInterpretation.interpret(value)

  internal fun stampSatisfiedVerdict(
    normalizedOutput: NormalizedFeatureTaskRuntimePhaseOutput,
  ): NormalizedFeatureTaskRuntimePhaseOutput {
    val envelope = normalizedOutput.envelopeWireMap().toMutableMap()
    envelope[SharedPayloadKeys.VERDICT] = FeatureTaskRuntimeVerdict.SATISFIED.wireValue
    return normalizedOutput.copy(
      envelope = envelope,
      canonicalJson = JsonCodec.mapToJsonString(envelope),
    )
  }

  internal fun FeatureTaskRuntimeRunLoopContext.commitCompletedAuditRound(
    precedingPhaseId: String,
    blockedReason: (
      String,
      String,
    ) -> String,
  ): String? {
    val branch = requireNotNull(session.resolvedBranch)
    val currentBranch = phaseGates.gitOperations.currentBranch(request.repoRoot)
    if (currentBranch !is WorkflowGitOperationResult.Ok) {
      return blockedReason(branch, "current branch lookup failed: ${currentBranch.error}")
    }
    if (currentBranch.value.trim() != branch.trim()) {
      return blockedReason(branch, "current branch is '${currentBranch.value.trim()}'")
    }
    val established =
      with(FeatureTaskRuntimeRunLoopCheckpointRemediation) {
        this@commitCompletedAuditRound.checkpointEstablished(
          precedingPhaseId = precedingPhaseId,
          loopId = null,
          intent = FeatureTaskRuntimeCheckpointMessage.INTENT_AUDITED_IMPLEMENTATION,
          blockedReason = blockedReason,
        )
      }
    return if (established) {
      null
    } else {
      session.blocked?.blockedReason
        ?: blockedReason(branch, "audit round commit could not be established")
    }
  }

  internal fun auditRoundCommitBlockedReason(branch: String, detail: String): (String, String) -> String =
    { actualBranch, error ->
      FeatureTaskRuntimeRunLoopPlanningBranch.auditReviewCheckpointBlockedReason(
        actualBranch.ifBlank { branch },
        error.ifBlank { detail },
      )
    }

  internal fun FeatureTaskRuntimeRunLoopContext.blockAuditWhitespaceOnlyFinalResponse(
    run: PhaseRun,
    iteration: Int,
    fileManifest: FeatureTaskRuntimePhaseFileManifest?,
  ): AttemptResult = AttemptResult.settled(
    FeatureTaskRuntimeRunLoopPhaseAttempts.blockInPhase(
      request,
      state,
      recorder,
      observability,
      PhaseBlockRequest(
        run = run,
        attemptCount = iteration,
        reason = "Audit completed with a whitespace-only remaining-criteria final response; the run blocks " +
          "rather than treating it as an empty list or launching a retry.",
        observability = observability,
        payload = BlockAndPersistPayload(fileManifest = fileManifest),
        failureDisposition = FeatureTaskRuntimeFailureDisposition.INVALID_OUTPUT,
      ),
    ),
  )

  internal fun clearRetryHintOnFreshLaunch(
    state: FeatureTaskRuntimeRunState,
    session: FeatureTaskRuntimeRunLoopSession,
    phaseId: String,
  ) {
    if (phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT) return
    if (state.resumedFromPriorProcess(phaseId)) {
      session.transitionAuditRetryFocusHint(null)
    }
  }

  internal fun FeatureTaskRuntimeRunLoopContext.settleCompletedAuditRound(
    capture: ValidatedOutputCapture,
    outputMap: FeatureTaskRuntimeWorkflowArtifactMap,
  ): AttemptResult? {
    val run = capture.run
    if (run.phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT) return null
    if ((outputMap[SharedPayloadKeys.STATUS] as? String).workflowStepStatus() != WorkflowStepStatus.COMPLETED) {
      return null
    }
    val finalResponse = FeatureTaskRuntimeOutputVerification.auditProseValue(outputMap)
    return when (val interpretation = interpretCompletedAuditValue(finalResponse)) {
      FeatureTaskRuntimeAuditRemainingAcResult.MissingFinalResponse ->
        blockAuditWhitespaceOnlyFinalResponse(run, capture.iteration, capture.fileManifest)
      FeatureTaskRuntimeAuditRemainingAcResult.WhitespaceOnlyFinalResponse ->
        blockAuditWhitespaceOnlyFinalResponse(run, capture.iteration, capture.fileManifest)
      is FeatureTaskRuntimeAuditRemainingAcResult.RemainingCriteriaText -> {
        val branch = session.resolvedBranch
        if (branch != null) {
          val blocked = commitCompletedAuditRound(
            precedingPhaseId = run.phaseId,
            blockedReason = auditRoundCommitBlockedReason(
              branch,

              "audit retry could not commit current changes",
            ),
          )
          if (blocked != null) {
            return AttemptResult.settled(
              FeatureTaskRuntimeRunLoopPhaseAttempts.blockInPhase(
                request,
                state,
                recorder,
                observability,
                PhaseBlockRequest(
                  run = run,
                  attemptCount = capture.iteration,
                  reason = blocked,
                  observability = observability,
                  payload = BlockAndPersistPayload(fileManifest = capture.fileManifest),
                  failureDisposition = FeatureTaskRuntimeFailureDisposition.NEEDS_USER_ACTION,
                ),
              ),
            )
          }
        }
        AttemptResult.auditRetry(
          focusHint = interpretation.text,
          fileManifest = capture.fileManifest,
        )
      }
      FeatureTaskRuntimeAuditRemainingAcResult.EmptyRemainingList -> {
        null
      }
    }
  }

  internal fun attestedAuditOutputForAcceptance(
    attested: NormalizedFeatureTaskRuntimePhaseOutput,
    outputMap: FeatureTaskRuntimeWorkflowArtifactMap,
  ): NormalizedFeatureTaskRuntimePhaseOutput {
    if ((outputMap[SharedPayloadKeys.STATUS] as? String).workflowStepStatus() != WorkflowStepStatus.COMPLETED) {
      return attested
    }
    if (outputMap[SharedPayloadKeys.VERDICT] != null) return attested
    return when (interpretCompletedAuditValue(FeatureTaskRuntimeOutputVerification.auditProseValue(outputMap))) {
      FeatureTaskRuntimeAuditRemainingAcResult.EmptyRemainingList -> stampSatisfiedVerdict(attested)
      else -> attested
    }
  }
}
