package skillbill.engine.featuretask.runloop.settlement

import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.engine.featuretask.lifecycle.checkpoint.FeatureTaskRuntimeCheckpointMessage
import skillbill.engine.featuretask.phase.core.FeatureTaskRuntimePhaseFileManifest
import skillbill.engine.featuretask.review.core.FeatureTaskRuntimeOutputVerification
import skillbill.engine.featuretask.runloop.checkpoint.FeatureTaskRuntimeRunLoopCheckpointRemediation
import skillbill.engine.featuretask.runloop.core.AttemptResult
import skillbill.engine.featuretask.runloop.core.BlockAndPersistPayload
import skillbill.engine.featuretask.runloop.core.FeatureTaskRuntimeRunLoopContext
import skillbill.engine.featuretask.runloop.core.FeatureTaskRuntimeRunLoopPlanningBranch
import skillbill.engine.featuretask.runloop.core.FeatureTaskRuntimeRunLoopSession
import skillbill.engine.featuretask.runloop.core.PhaseBlockRequest
import skillbill.engine.featuretask.runloop.core.PhaseRun
import skillbill.engine.featuretask.runloop.core.ValidatedOutputCapture
import skillbill.engine.featuretask.runloop.phase.FeatureTaskRuntimeRunLoopPhaseAttempts
import skillbill.engine.featuretask.runloop.state.FeatureTaskRuntimeRunState
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.model.validation.FeatureTaskRuntimeVerdict
import skillbill.workflow.model.workflowStepStatus
import skillbill.workflow.taskruntime.artifact.envelopeWireMap
import skillbill.workflow.taskruntime.feature.FeatureTaskRuntimeAuditRemainingAcInterpretation
import skillbill.workflow.taskruntime.model.audit.FeatureTaskRuntimeAuditRemainingAcResult
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeWorkflowArtifactMap
import skillbill.workflow.taskruntime.model.handoff.task.NormalizedFeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeFailureDisposition
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition

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

  internal fun commitCompletedAuditRound(
    context: FeatureTaskRuntimeRunLoopContext,
    precedingPhaseId: String,
    blockedReason: (
      String,
      String,
    ) -> String,
  ): String? =
    with(context) {
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
          FeatureTaskRuntimeRunLoopCheckpointRemediation.checkpointEstablished(
            context,
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

  internal fun auditRoundCommitBlockedReason(
    branch: String,
    detail: String,
  ): (String, String) -> String =
    { actualBranch, error ->
      FeatureTaskRuntimeRunLoopPlanningBranch.auditReviewCheckpointBlockedReason(
        actualBranch.ifBlank { branch },
        error.ifBlank { detail },
      )
    }

  internal fun blockAuditWhitespaceOnlyFinalResponse(
    context: FeatureTaskRuntimeRunLoopContext,
    run: PhaseRun,
    iteration: Int,
    fileManifest: FeatureTaskRuntimePhaseFileManifest?,
  ): AttemptResult =
    with(context) {
      AttemptResult.settled(
        FeatureTaskRuntimeRunLoopPhaseAttempts.blockInPhase(
          request,
          state,
          recorder,
          observability,
          PhaseBlockRequest(
            run = run,
            attemptCount = iteration,
            reason =
              "Audit completed with a whitespace-only remaining-criteria final response; the run blocks " +
                "rather than treating it as an empty list or launching a retry.",
            observability = observability,
            payload = BlockAndPersistPayload(fileManifest = fileManifest),
            failureDisposition = FeatureTaskRuntimeFailureDisposition.INVALID_OUTPUT,
          ),
        ),
      )
    }

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

  internal fun settleCompletedAuditRound(
    context: FeatureTaskRuntimeRunLoopContext,
    capture: ValidatedOutputCapture,
    outputMap: FeatureTaskRuntimeWorkflowArtifactMap,
  ): AttemptResult? =
    with(context) {
      val run = capture.run
      if (run.phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT) return null
      if ((outputMap[SharedPayloadKeys.STATUS] as? String).workflowStepStatus() != WorkflowStepStatus.COMPLETED) {
        return null
      }
      val finalResponse = FeatureTaskRuntimeOutputVerification.auditProseValue(outputMap)
      return when (val interpretation = interpretCompletedAuditValue(finalResponse)) {
        FeatureTaskRuntimeAuditRemainingAcResult.MissingFinalResponse ->
          blockAuditWhitespaceOnlyFinalResponse(context, run, capture.iteration, capture.fileManifest)
        FeatureTaskRuntimeAuditRemainingAcResult.WhitespaceOnlyFinalResponse ->
          blockAuditWhitespaceOnlyFinalResponse(context, run, capture.iteration, capture.fileManifest)
        is FeatureTaskRuntimeAuditRemainingAcResult.RemainingCriteriaText -> {
          val branch = session.resolvedBranch
          if (branch != null) {
            val blocked =
              commitCompletedAuditRound(
                context,
                precedingPhaseId = run.phaseId,
                blockedReason =
                  auditRoundCommitBlockedReason(
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
