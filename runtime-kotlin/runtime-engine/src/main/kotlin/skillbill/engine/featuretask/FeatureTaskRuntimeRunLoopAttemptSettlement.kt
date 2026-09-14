package skillbill.engine.featuretask

import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunRequest
import skillbill.application.diagnostics.model.FeatureTaskRuntimeRejectedOutputWrite
import skillbill.application.diagnostics.model.RejectedOutputDiagnosticRequest
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.engine.featuretask.model.FeatureTaskRuntimeCommitPushHandoff
import skillbill.engine.featuretask.model.FeatureTaskRuntimeCommitPushHandoffInvalid
import skillbill.engine.featuretask.model.FeatureTaskRuntimeCommitPushHandoffValid
import skillbill.engine.featuretask.model.FeatureTaskRuntimeSubtaskCommitIdentity
import skillbill.engine.featuretask.model.FeatureTaskRuntimeSubtaskFinalisationBlocked
import skillbill.engine.featuretask.model.FeatureTaskRuntimeSubtaskFinalisationResult
import skillbill.engine.featuretask.model.FeatureTaskRuntimeSubtaskFinaliseRequest
import skillbill.engine.featuretask.model.FeatureTaskRuntimeSubtaskFinalised
import skillbill.error.FeatureTaskRuntimePhaseOutputFailureKind
import skillbill.error.InvalidFeatureTaskRuntimePhaseOutputSchemaError
import skillbill.error.InvalidFeatureTaskRuntimeValidationEvidenceSchemaError
import skillbill.ports.diagnostics.model.ProducerOutputEvidence
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import skillbill.ports.workflow.gitops.stagedPaths
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.model.workflowStepStatus
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.FeatureTaskRuntimeWorkflowArtifactMap
import skillbill.workflow.taskruntime.envelopeWireMap
import skillbill.workflow.taskruntime.model.CorrectiveRepairCapturedResponse
import skillbill.workflow.taskruntime.model.CorrectiveRepairDiagnosticLocator
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeCorrectiveRepairContext
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeFailureDisposition
import skillbill.workflow.taskruntime.model.NormalizedFeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.requireAcceptedOutput
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseOutputValidator
import skillbill.engine.featuretask.FeatureTaskRuntimeGoalContinuationRecorder
import skillbill.engine.featuretask.FeatureTaskRuntimePhaseGates
import skillbill.engine.featuretask.FeatureTaskPhaseSettlementService
import java.time.Clock
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeTransitionDeclaration

object FeatureTaskRuntimeRunLoopAttemptSettlement {
  internal fun rejectedOutputTargeting(args: RejectedOutputTargetingArgs): RejectedOutputTargeting =
    RejectedOutputTargeting(
      phaseId = args.phaseId,
      agentId = args.agentId,
      model = args.model,
      path = args.path,
      repairTurn = args.repairTurn,
    )

  internal fun gateOutput(state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, session: FeatureTaskRuntimeRunLoopSession, goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder, phaseSettlementService: FeatureTaskPhaseSettlementService, outputValidator: FeatureTaskRuntimePhaseOutputValidator, diagnostics: RuntimeDiagnostics, phaseGates: FeatureTaskRuntimePhaseGates, clock: Clock, transitions: FeatureTaskRuntimeTransitionDeclaration, args: GateOutputArgs): AttemptResult {
    FeatureTaskRuntimeRunLoopAttemptSettlement.gateOutputEarlyExit(args)?.let { return it }
    FeatureTaskRuntimeRunLoopAttemptSettlement.settleFromPersistedEnvelope(state, recorder, session, goalContinuationRecorder, phaseSettlementService, outputValidator, diagnostics, phaseGates, clock, transitions, args)?.let { return it }
    return try {
      val run = args.run
      val acceptedOutput = outputValidator
        .validatePhaseOutput(args.captured.text, sourceLabel = run.phaseId)
        .requireAcceptedOutput(run.phaseId)
      settleValidatedOutput(state, recorder, session, goalContinuationRecorder, outputValidator, diagnostics, phaseGates, clock, transitions, SettleValidatedOutputArgs(
          run = run,
          iteration = args.iteration,
          output = SettledOutputContext(
            normalizedOutput = acceptedOutput.normalizedOutput,
            repairEvidence = acceptedOutput.repairEvidence,
            observability = args.observability,
            fileManifest = args.fileManifest,
            captured = args.captured,
          ),
        ))
    } catch (error: InvalidFeatureTaskRuntimePhaseOutputSchemaError) {
      FeatureTaskRuntimeRunLoopAttemptSettlement.gateOutputSchemaInvalid(state, recorder, args, error)
    }
  }

  internal fun correctiveRepairContextForRejection(
    args: CorrectiveRepairRejectionArgs,
  ): FeatureTaskRuntimeCorrectiveRepairContext {
    val run = args.run
    val iteration = args.iteration
    val captured = args.captured
    val diagnosticWrite = args.diagnosticWrite
    val rejection = args.rejection
    val utf8ByteCount = captured.byteSize.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    val capturedResponse = if (captured.truncated) {
      CorrectiveRepairCapturedResponse.AlreadyTruncated(
        utf8ByteCount = utf8ByteCount,
        digestSha256 = captured.sha256,
      )
    } else {
      CorrectiveRepairCapturedResponse.classify(
        body = captured.text,
        alreadyTruncated = false,
        knownUtf8ByteCount = utf8ByteCount,
        knownDigestSha256 = captured.sha256,
      )
    }
    val repairEvidence = rejection.structuralRepairEvidence
    val locator = (diagnosticWrite as? FeatureTaskRuntimeRejectedOutputWrite.Written)?.let {
      CorrectiveRepairDiagnosticLocator(it.identity)
    }
    val degradationClass = (diagnosticWrite as? FeatureTaskRuntimeRejectedOutputWrite.Degraded)?.failureClass
    return FeatureTaskRuntimeCorrectiveRepairContext(
      phaseId = run.phaseId,
      attempt = iteration.coerceAtLeast(1),
      repairTurn = run.validationGateRepairTurn.takeIf { it > 0 },
      rejectionRule = rejection.rule,
      rejectionPath = rejection.path,
      payloadFreeConstraint = rejection.payloadFreeConstraint,
      diagnosticLocator = locator,
      captured = capturedResponse,
      acceptedAfterStructuralRepair = rejection.acceptedAfterStructuralRepair || repairEvidence != null,
      structuralRepairEvidence = repairEvidence,
      diagnosticDegradationClass = degradationClass,
    )
  }

  internal fun recordRejectedOutput(state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, args: RecordRejectedOutputArgs): FeatureTaskRuntimeRejectedOutputWrite {
    val run = args.run
    val captured = args.captured
    val targeting = args.targeting
    return recorder.recordRejectedOutput(
      RejectedOutputDiagnosticRequest(
        workflowId = run.request.workflowId,
        phaseId = targeting.phaseId,
        attempt = args.iteration.coerceAtLeast(1),
        rule = args.rule,
        path = targeting.path,
        reason = args.reason,
        agentId = targeting.agentId,
        model = targeting.model,
        rawResponse = captured.bytes,
        observedByteSize = captured.byteSize,
        observedSha256 = captured.sha256,
        truncated = captured.truncated,
        repairTurn = targeting.repairTurn,
      ),
      state.evidenceGeneration(targeting.phaseId),
    )
  }

  internal fun persistChildProcessFailureOutput(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, diagnostics: RuntimeDiagnostics, run: PhaseRun, iteration: Int, reason: String, childOutput: FeatureTaskRuntimeChildOutput?){
    val output = childOutput ?: return
    runCatching {
      recordRejectedOutput(state, recorder, RecordRejectedOutputArgs(
          run = run,
          iteration = iteration,
          rule = FEATURE_TASK_RUNTIME_PROCESS_FAILURE_RULE,
          reason = boundedSchemaGateDetail(reason),
          captured = CapturedPhaseOutput.fromBytes(output.storedBody().encodeToByteArray()),
          targeting = rejectedOutputTargeting(defaultRejectedOutputTargetingArgs(run)),
        ))
    }.onFailure { error ->
      diagnostics.warning(
        "Feature-task-runtime could not persist the child process-failure diagnostic for issue " +
          "${request.issueKey}, workflow ${request.workflowId}, phase '${run.phaseId}'. The block " +
          "reason keeps its bounded excerpt; the full child output is lost.",
        error,
      )
    }
  }

  internal fun settleValidatedOutput(state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, session: FeatureTaskRuntimeRunLoopSession, goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder, outputValidator: FeatureTaskRuntimePhaseOutputValidator, diagnostics: RuntimeDiagnostics, phaseGates: FeatureTaskRuntimePhaseGates, clock: Clock, transitions: FeatureTaskRuntimeTransitionDeclaration, args: SettleValidatedOutputArgs): AttemptResult {
    val run = args.run
    val iteration = args.iteration
    val attested = FeatureTaskRuntimeRunLoopOutputVerification.attestAbsentGateValidationReceipt( outputValidator, run, args.output.normalizedOutput)
    val capture = ValidatedOutputCapture(
      run = run,
      iteration = iteration,
      captured = args.output.captured,
      repairEvidence = args.output.repairEvidence,
      fileManifest = args.output.fileManifest,
    )
    try {
      requireValidationEvidenceForValidateSettlement(recorder, session, goalContinuationRecorder, phaseGates, run, attested.envelopeWireMap())
      return settleValidatedOutputWithEvidence(outputValidator, phaseGates, state, recorder, args.output.observability, session, goalContinuationRecorder, diagnostics, clock, transitions, capture, attested)
    } catch (error: InvalidFeatureTaskRuntimeValidationEvidenceSchemaError) {
      return rejectValidatedOutput(state, recorder, capture, attested.envelopeWireMap(), "validation-evidence", error.message.orEmpty())
    }
  }

  private fun settleValidatedOutputWithEvidence(outputValidator: FeatureTaskRuntimePhaseOutputValidator, phaseGates: FeatureTaskRuntimePhaseGates, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, observability: FeatureTaskRuntimeRunObservability, session: FeatureTaskRuntimeRunLoopSession, goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder, diagnostics: RuntimeDiagnostics, clock: Clock, transitions: FeatureTaskRuntimeTransitionDeclaration, capture: ValidatedOutputCapture, attested: NormalizedFeatureTaskRuntimePhaseOutput): AttemptResult {
    val outputMap = attested.envelopeWireMap()
    fun reject(rule: String, detail: String): AttemptResult =
      FeatureTaskRuntimeRunLoopAttemptSettlement.rejectValidatedOutput(state, recorder, capture, outputMap, rule, detail)
    FeatureTaskRuntimeRunLoopAttemptSettlement.settleValidatedOutputBoundary(state, recorder, phaseGates, capture, outputMap, ::reject)?.let { return it }
    FeatureTaskRuntimeRunLoopOutputVerification.firstValidatedOutputRejection(capture.run.phaseId, outputMap)?.let { (
      rule,
      reason,
    ),
      ->
      return reject(rule, reason)
    }
    val fingerprintResolution = FeatureTaskRuntimeRunLoopAttemptSettlement.resolveRepositoryFingerprint(capture.run.request, state, recorder, phaseGates, capture.run, capture.iteration, observability, capture.fileManifest)
    fingerprintResolution.blocked?.let { return it }
    return FeatureTaskRuntimeRunLoopAttemptSettlement.settleValidatedOutputAfterFingerprint(
      capture.run.request, state, recorder, observability, session,
      phaseGates,
      goalContinuationRecorder,
      outputValidator,
      diagnostics,
      clock,
      transitions,
      SettleValidatedOutputAfterFingerprintArgs(
        capture = capture,
        attested = attested,
        repairEvidence = capture.repairEvidence,
        observability = observability,
        repositoryFingerprint = fingerprintResolution.fingerprint,
        reject = ::reject,
      ))
  }

  internal fun settleFromPersistedEnvelope(state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, session: FeatureTaskRuntimeRunLoopSession, goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder, phaseSettlementService: FeatureTaskPhaseSettlementService, outputValidator: FeatureTaskRuntimePhaseOutputValidator, diagnostics: RuntimeDiagnostics, phaseGates: FeatureTaskRuntimePhaseGates, clock: Clock, transitions: FeatureTaskRuntimeTransitionDeclaration, args: GateOutputArgs): AttemptResult? {
    val settlementEnvelope = loadPersistedSettlementEnvelope(state, recorder, phaseSettlementService, args) ?: return null
    return settlePersistedEnvelope(state, recorder, session, phaseSettlementService, outputValidator, phaseGates, goalContinuationRecorder, diagnostics, clock, transitions, args, settlementEnvelope)
  }

  private fun loadPersistedSettlementEnvelope(state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, phaseSettlementService: FeatureTaskPhaseSettlementService, args: GateOutputArgs): FeatureTaskRuntimeWorkflowArtifactMap? {
    val run = args.run
    return try {
      phaseSettlementService.findEnvelope(
        workflowId = run.request.workflowId,
        phaseId = run.phaseId,
        attempt = args.iteration,
      )?.envelope
    } catch (error: InvalidFeatureTaskRuntimeValidationEvidenceSchemaError) {
      clearAndRecordPersistedEvidenceFailure(state, recorder, phaseSettlementService, args, error)
      null
    }
  }

  private fun settlePersistedEnvelope(state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, session: FeatureTaskRuntimeRunLoopSession, phaseSettlementService: FeatureTaskPhaseSettlementService, outputValidator: FeatureTaskRuntimePhaseOutputValidator, phaseGates: FeatureTaskRuntimePhaseGates, goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder, diagnostics: RuntimeDiagnostics, clock: Clock, transitions: FeatureTaskRuntimeTransitionDeclaration, args: GateOutputArgs, settlementEnvelope: FeatureTaskRuntimeWorkflowArtifactMap): AttemptResult? {
    val run = args.run
    return try {
      val acceptedOutput = outputValidator
        .validatePhaseOutput(JsonCodec.mapToJsonString(settlementEnvelope), sourceLabel = run.phaseId)
        .requireAcceptedOutput(run.phaseId)
      validatePersistedValidationEvidence(recorder, session, goalContinuationRecorder, phaseGates, run, acceptedOutput.normalizedOutput.envelopeWireMap())
      FeatureTaskRuntimeRunLoopAttemptSettlement.settleValidatedOutput(state, recorder, session, goalContinuationRecorder, outputValidator, diagnostics, phaseGates, clock, transitions, SettleValidatedOutputArgs(
          run = run,
          iteration = args.iteration,
          output = SettledOutputContext(
            normalizedOutput = acceptedOutput.normalizedOutput,
            repairEvidence = acceptedOutput.repairEvidence,
            observability = args.observability,
            fileManifest = args.fileManifest,
            captured = args.captured,
          ),
        ))
    } catch (error: InvalidFeatureTaskRuntimePhaseOutputSchemaError) {
      rejectPersistedEnvelopeSchema(state, recorder, phaseSettlementService, args, run, error)
      null
    } catch (error: InvalidFeatureTaskRuntimeValidationEvidenceSchemaError) {
      clearAndRecordPersistedEvidenceFailure(state, recorder, phaseSettlementService, args, error)
      null
    }
  }

  private fun validatePersistedValidationEvidence(recorder: FeatureTaskRuntimePhaseRecorder, session: FeatureTaskRuntimeRunLoopSession, goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder, phaseGates: FeatureTaskRuntimePhaseGates, run: PhaseRun, envelope: FeatureTaskRuntimeWorkflowArtifactMap){
    requireValidationEvidenceForValidateSettlement(recorder, session, goalContinuationRecorder, phaseGates, run, envelope)
  }

  private fun clearAndRecordPersistedEvidenceFailure(state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, phaseSettlementService: FeatureTaskPhaseSettlementService, args: GateOutputArgs, error: InvalidFeatureTaskRuntimeValidationEvidenceSchemaError){
    val run = args.run
    phaseSettlementService.clear(
      workflowId = run.request.workflowId,
      phaseId = run.phaseId,
      attempt = args.iteration,
    )
    FeatureTaskRuntimeRunLoopAttemptSettlement.recordRejectedOutput(state, recorder, RecordRejectedOutputArgs(
        run = run,
        iteration = args.iteration,
        rule = "phase-settlement-validation-evidence",
        reason = error.message.orEmpty(),
        captured = args.captured,
        targeting = FeatureTaskRuntimeRunLoopAttemptSettlement.rejectedOutputTargeting(
          defaultRejectedOutputTargetingArgs(run),
        ),
      ))
  }

  private fun rejectPersistedEnvelopeSchema(state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, phaseSettlementService: FeatureTaskPhaseSettlementService, args: GateOutputArgs, run: PhaseRun, error: InvalidFeatureTaskRuntimePhaseOutputSchemaError){
    phaseSettlementService.clear(
      workflowId = run.request.workflowId,
      phaseId = run.phaseId,
      attempt = args.iteration,
    )
    FeatureTaskRuntimeRunLoopOutputVerification.persistVerifyFindingsCheckpointIfPresent(recorder, run, args.captured.text)
    FeatureTaskRuntimeRunLoopAttemptSettlement.recordRejectedOutput(state, recorder, RecordRejectedOutputArgs(
        run = run,
        iteration = args.iteration,
        rule = "phase-settlement-schema",
        reason = error.reason,
        captured = args.captured,
        targeting = FeatureTaskRuntimeRunLoopAttemptSettlement.rejectedOutputTargeting(
          defaultRejectedOutputTargetingArgs(
            run,
            RejectedOutputTargetingOverrides(
              path = FeatureTaskRuntimeRunLoopRecordRejection.rejectionPath(error.reason),
            ),
          ),
        ),
      ))
  }
  internal fun gateOutputEarlyExit(args: GateOutputArgs): AttemptResult? {
    val run = args.run
    if (run.validationGateRepairTurn > 0) {
      val outputMap = FeatureTaskRuntimeRunLoopValidationGate.looseOutputEnvelope(args.captured.text)
      val operatorTerminalQualityGate = outputMap?.let { envelope ->
        val disposition = FeatureTaskRuntimePhaseSafetyPolicy.dispositionForTerminalOutput(run.phaseId, envelope)
        !disposition.retryOnResume &&
          (
            run.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE ||
              run.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_BUILD
            )
      } == true
      if (!operatorTerminalQualityGate) {
        val gateRepairOutput = FeatureTaskRuntimeRunLoopValidationGate.gateRepairSegmentOutput(
          run,
          args.iteration,
        )
        return AttemptResult.settled(PhaseOutcome.completed(gateRepairOutput))
      }
    }
    if (run.validationGateTriage) {
      return AttemptResult.settled(
        PhaseOutcome.completed(
          FeatureTaskRuntimeRunLoopValidationGate.gateTriageSegmentOutput(
            run,
            args.iteration,
            args.captured.text,
          ),
        ),
      )
    }
    return null
  }
  internal fun gateOutputSchemaInvalid(state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, args: GateOutputArgs, error: InvalidFeatureTaskRuntimePhaseOutputSchemaError): AttemptResult {
    val run = args.run
    FeatureTaskRuntimeRunLoopOutputVerification.persistVerifyFindingsCheckpointIfPresent(recorder, run, args.captured.text)
    val path = FeatureTaskRuntimeRunLoopRecordRejection.rejectionPath(error.reason)
    val reason = FeatureTaskRuntimeRunLoopRecordRejection.payloadFreeRejectionReason("phase-output-schema", path)
    val diagnosticWrite = FeatureTaskRuntimeRunLoopAttemptSettlement.recordRejectedOutput(state, recorder, RecordRejectedOutputArgs(
        run = run,
        iteration = args.iteration,
        rule = "phase-output-schema",
        reason = error.reason,
        captured = args.captured,
        targeting = FeatureTaskRuntimeRunLoopAttemptSettlement.rejectedOutputTargeting(
          defaultRejectedOutputTargetingArgs(run, RejectedOutputTargetingOverrides(path = path)),
        ),
      ))
    val repairEvidence = FeatureTaskRuntimeRunLoopOutputVerification
      .structuralRepairEvidenceFromSchemaError(error)
    return FeatureTaskRuntimeRunLoopOutputPersistence.schemaInvalidAttempt(
      reason,
      args.fileManifest,
      malformedOutput = error.failureKind == FeatureTaskRuntimePhaseOutputFailureKind.MALFORMED,
      retryReason = FeatureTaskRuntimeRunLoopRecordRejection.retryRejectionReason(reason, error.payloadFreeReason),
      correctiveRepairContext = FeatureTaskRuntimeRunLoopAttemptSettlement.correctiveRepairContextForRejection(
        CorrectiveRepairRejectionArgs(
          run = run,
          iteration = args.iteration,
          captured = args.captured,
          diagnosticWrite = diagnosticWrite,
          rejection = CorrectiveRepairRejectionDetail(
            rule = "phase-output-schema",
            path = path,
            payloadFreeConstraint = error.payloadFreeReason.orEmpty(),
            acceptedAfterStructuralRepair = error.acceptedAfterStructuralRepair,
            structuralRepairEvidence = repairEvidence,
          ),
        ),
      ),
    )
  }

  internal data class RepositoryFingerprintResolution(
    val fingerprint: String?,
    val blocked: AttemptResult?,
  )

  internal fun resolveRepositoryFingerprint(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, phaseGates: FeatureTaskRuntimePhaseGates, run: PhaseRun, iteration: Int, observability: FeatureTaskRuntimeRunObservability, fileManifest: FeatureTaskRuntimePhaseFileManifest): RepositoryFingerprintResolution {
    val result = FeatureTaskRuntimeRunLoopOutputVerification.completedPhaseRepositoryFingerprint( phaseGates, run) ?: return RepositoryFingerprintResolution(null, null)
    if (result !is WorkflowGitOperationResult.Ok) {
      val blocked = AttemptResult.settled(
        FeatureTaskRuntimeRunLoopPhaseAttempts.blockInPhase(request, state, recorder, observability, PhaseBlockRequest(
            run = run,
            attemptCount = iteration,
            reason = "Completed-phase repository fingerprinting failed for '${run.phaseId}': ${result.error}",
            observability = observability,
            payload = BlockAndPersistPayload(fileManifest = fileManifest),
            failureDisposition = FeatureTaskRuntimeFailureDisposition.PROCESS_FAILURE,
          )),
      )
      return RepositoryFingerprintResolution(fingerprint = null, blocked = blocked)
    }
    return RepositoryFingerprintResolution(result.value, null)
  }

  internal fun settleValidatedOutputBoundary(state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, phaseGates: FeatureTaskRuntimePhaseGates, capture: ValidatedOutputCapture, outputMap: FeatureTaskRuntimeWorkflowArtifactMap, reject: (String, String) -> AttemptResult,): AttemptResult? {
    val bodyDelivery = FeatureTaskRuntimeRunLoopOutputVerification
      .findingVerificationBoundaryBodyDeliveryDecision(state, recorder, phaseGates, capture.run, outputMap)
    return when (bodyDelivery) {
      is BoundaryBodyDeliveryDecision.RejectDecision -> reject("output-verification", bodyDelivery.reason)
      is BoundaryBodyDeliveryDecision.ContinueDecision ->
        AttemptResult.boundaryBodyDelivery(bodyDelivery.reason, capture.fileManifest)
      BoundaryBodyDeliveryDecision.NotApplicable -> null
    }
  }

  internal fun settleValidatedOutputPauseOrTerminal(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, observability: FeatureTaskRuntimeRunObservability, args: SettleValidatedOutputPauseArgs): AttemptResult? {
    val capture = args.capture
    val outputMap = args.attested.envelopeWireMap()
    val attested = args.attested
    val repairEvidence = args.repairEvidence
    val repositoryFingerprint = args.repositoryFingerprint
    val run = capture.run
    terminalBlockedReasonFrom(run.phaseId, outputMap)?.let { reason ->
      return FeatureTaskRuntimeRunLoopOutputVerification.terminalOutputAttempt(request, state, recorder, observability, TerminalOutputAttemptArgs(
          run = run,
          iteration = capture.iteration,
          reason = reason,
          normalizedOutput = attested,
          repairEvidence = repairEvidence,
          observability = observability,
          fileManifest = capture.fileManifest,
        ))
    }
    return null
  }

  internal fun settleValidatedOutputCommit(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, session: FeatureTaskRuntimeRunLoopSession, goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder, outputValidator: FeatureTaskRuntimePhaseOutputValidator, diagnostics: RuntimeDiagnostics, phaseGates: FeatureTaskRuntimePhaseGates, run: PhaseRun, capture: ValidatedOutputCapture, attested: NormalizedFeatureTaskRuntimePhaseOutput, observability: FeatureTaskRuntimeRunObservability): Pair<NormalizedFeatureTaskRuntimePhaseOutput, AttemptResult?> = when (
    val finalisation = FeatureTaskRuntimeRunLoopAttemptSettlement.finaliseSubtaskCommit(request, state, recorder, session, goalContinuationRecorder, outputValidator, diagnostics, phaseGates, run, attested)
  ) {
    is CommitPushNotApplicable -> attested to null
    is CommitPushSettled -> finalisation.output to null
    is CommitPushBlocked -> attested to AttemptResult.settled(
      FeatureTaskRuntimeRunLoopPhaseAttempts.blockAndPersistInPhase(request, state, recorder, observability, null, phaseBlockArgs(
          run,
          capture.iteration,
          finalisation.reason,
          observability,
          payload = BlockAndPersistPayload(fileManifest = capture.fileManifest),
        ).withDisposition(FeatureTaskRuntimeFailureDisposition.NEEDS_USER_ACTION)),
    )
  }

  internal fun settleValidatedOutputAfterFingerprint(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, observability: FeatureTaskRuntimeRunObservability, session: FeatureTaskRuntimeRunLoopSession, phaseGates: FeatureTaskRuntimePhaseGates, goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder, outputValidator: FeatureTaskRuntimePhaseOutputValidator, diagnostics: RuntimeDiagnostics, clock: Clock, transitions: FeatureTaskRuntimeTransitionDeclaration, args: SettleValidatedOutputAfterFingerprintArgs): AttemptResult {
    return settleValidatedOutputPauseOrTerminal(request, state, recorder, observability, SettleValidatedOutputPauseArgs(
        capture = args.capture,
        attested = args.attested,
        repairEvidence = args.repairEvidence,
        observability = observability,
        repositoryFingerprint = args.repositoryFingerprint,
      )) ?: FeatureTaskRuntimeRunLoopOutputVerification.completionProjectionRejection(
        request, state, recorder, session,
        phaseGates,
        transitions,
        CompletionProjectionRejectionArgs(
        run = args.capture.run,
        iteration = args.capture.iteration,
        normalizedOutput = args.attested,
        repairEvidence = args.repairEvidence,
        repositoryFingerprint = args.repositoryFingerprint,
      ))?.let { (rule, reason) -> args.reject(rule, reason) }
      ?: FeatureTaskRuntimeRunLoopRepairReceipt.settleCompletedImplementationOutput(
        request, state, recorder, observability,
        goalContinuationRecorder,
        diagnostics,
        CompletedImplementationOutputArgs(
          run = args.capture.run,
          normalizedOutput = args.attested,
          reject = args.reject,
          iteration = args.capture.iteration,
          observability = observability,
          fileManifest = args.capture.fileManifest,
        ))
      ?: FeatureTaskRuntimeRunLoopAuditRetry.settleCompletedAuditRound(request, state, recorder, observability, session, diagnostics, phaseGates, args.capture, args.attested.envelopeWireMap())
      ?: finalizeValidatedOutputAcceptance(request, state, recorder, observability, session, goalContinuationRecorder, outputValidator, diagnostics, phaseGates, clock, FinalizeValidatedOutputAcceptanceArgs(
          capture = args.capture,
          attested = FeatureTaskRuntimeRunLoopAuditRetry.attestedAuditOutputForAcceptance(
            args.attested,
            args.attested.envelopeWireMap(),
          ),
          repairEvidence = args.repairEvidence,
          observability = observability,
          repositoryFingerprint = args.repositoryFingerprint,
        ))
  }

  internal fun finalizeValidatedOutputAcceptance(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, observability: FeatureTaskRuntimeRunObservability, session: FeatureTaskRuntimeRunLoopSession, goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder, outputValidator: FeatureTaskRuntimePhaseOutputValidator, diagnostics: RuntimeDiagnostics, phaseGates: FeatureTaskRuntimePhaseGates, clock: Clock, args: FinalizeValidatedOutputAcceptanceArgs): AttemptResult {
    val capture = args.capture
    val attested = args.attested
    val repairEvidence = args.repairEvidence
    val repositoryFingerprint = args.repositoryFingerprint
    val run = capture.run
    val (finalised, commitBlocked) = settleValidatedOutputCommit(request, state, recorder, session, goalContinuationRecorder, outputValidator, diagnostics, phaseGates, run, capture, attested, observability)
    commitBlocked?.let { return it }
    FeatureTaskRuntimeRunLoopAttemptSettlement.retainSettledProducerOutput(request, state, recorder, clock, capture)
    return FeatureTaskRuntimeRunLoopOutputVerification.persistAcceptedOutput(
      request, state, recorder, observability,
      goalContinuationRecorder,
      diagnostics,
      PersistAcceptedOutputArgs(
        run = run,
        iteration = capture.iteration,
        normalizedOutput = finalised,
        repairEvidence = repairEvidence,
        observability = observability,
        fileManifest = capture.fileManifest,
        repositoryFingerprint = repositoryFingerprint,
      ))
  }

  internal fun rejectValidatedOutput(state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, capture: ValidatedOutputCapture, outputMap: FeatureTaskRuntimeWorkflowArtifactMap, rule: String, detail: String): AttemptResult {
    val diagnosticRule = rule
    val path = FeatureTaskRuntimeRunLoopRecordRejection.rejectionPath(detail)
    val reason = FeatureTaskRuntimeRunLoopRecordRejection.payloadFreeRejectionReason(rule, path)
    val retryFacingConstraint = FeatureTaskRuntimeRunLoopRecordRejection.payloadFreeSemanticGateConstraint( rule, detail, outputMap)
    val retryReason = FeatureTaskRuntimeRunLoopRecordRejection.retryRejectionReason(reason, retryFacingConstraint)
    val diagnosticWrite = FeatureTaskRuntimeRunLoopAttemptSettlement.recordRejectedOutput(state, recorder, RecordRejectedOutputArgs(
        run = capture.run,
        iteration = capture.iteration,
        rule = diagnosticRule,
        reason = detail,
        captured = capture.captured,
        targeting = FeatureTaskRuntimeRunLoopAttemptSettlement.rejectedOutputTargeting(
          defaultRejectedOutputTargetingArgs(capture.run, RejectedOutputTargetingOverrides(path = path)),
        ),
      ))
    return FeatureTaskRuntimeRunLoopOutputPersistence.schemaInvalidAttempt(
      reason,
      capture.fileManifest,
      retryReason = retryReason,
      correctiveRepairContext = FeatureTaskRuntimeRunLoopAttemptSettlement.correctiveRepairContextForRejection(
        CorrectiveRepairRejectionArgs(
          run = capture.run,
          iteration = capture.iteration,
          captured = capture.captured,
          diagnosticWrite = diagnosticWrite,
          rejection = CorrectiveRepairRejectionDetail(
            rule = diagnosticRule,
            path = path,
            payloadFreeConstraint = retryFacingConstraint ?: reason,
            acceptedAfterStructuralRepair = capture.repairEvidence != null,
            structuralRepairEvidence = capture.repairEvidence,
          ),
        ),
      ),
    )
  }

  internal fun retainSettledProducerOutput(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, clock: Clock, capture: ValidatedOutputCapture){
    val run = capture.run
    recorder.retainProducerOutput(
      ProducerOutputEvidence(
        workflowId = request.workflowId,
        phaseId = run.phaseId,
        attempt = capture.iteration,
        agentId = run.resolvedAgent.resolvedAgentId,
        model = run.modelDirective?.model ?: "unspecified",
        recordedAt = clock.instant(),
        byteSize = capture.outputByteSize,
        sha256 = capture.outputSha256,
        payload = capture.outputBytes.takeUnless { capture.outputTruncated },
        generation = state.evidenceGeneration(run.phaseId),
        repairTurn = run.validationGateRepairTurn,
      ),
    )
  }

  internal fun finaliseSubtaskCommit(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, session: FeatureTaskRuntimeRunLoopSession, goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder, outputValidator: FeatureTaskRuntimePhaseOutputValidator, diagnostics: RuntimeDiagnostics, phaseGates: FeatureTaskRuntimePhaseGates, run: PhaseRun, normalizedOutput: NormalizedFeatureTaskRuntimePhaseOutput): CommitPushFinalisation {
    if (
      run.phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_COMMIT_PUSH ||
      (normalizedOutput.envelopeWireMap()[SharedPayloadKeys.STATUS] as? String)
        .workflowStepStatus() != WorkflowStepStatus.COMPLETED
    ) {
      return CommitPushNotApplicable
    }
    val subtaskCommit = FeatureTaskRuntimeRunLoopSubtaskCommit
    val branch = subtaskCommit.finalisationBranch(request, session, phaseGates)
      ?: return subtaskCommit.unownedWorktreeCommitSha(request, outputValidator, diagnostics, phaseGates, run, normalizedOutput)
    val handoff = when (
      val read = FeatureTaskRuntimeSubtaskFinalisation.readHandoff(normalizedOutput.envelopeWireMap())
    ) {
      is FeatureTaskRuntimeCommitPushHandoffInvalid -> return CommitPushBlocked(read.reason)
      is FeatureTaskRuntimeCommitPushHandoffValid -> read.handoff
    }
    val identity = FeatureTaskRuntimeRunLoopCheckpoint.subtaskCommitIdentity(request)
    val ledger = FeatureTaskRuntimeRunLoopCheckpoint.subtaskCommitLedgerState(request, recorder, diagnostics, identity)
    val outcome = finaliseSubtaskCommitOutcome(
      request, state, recorder,
      diagnostics,
      phaseGates,
      goalContinuationRecorder,
      FinaliseSubtaskCommitOutcomeArgs(goalContinuationRecorder, outputValidator, phaseGates, run, branch, handoff, identity, ledger),
    )
    return when (outcome) {
      is FeatureTaskRuntimeSubtaskFinalisationBlocked -> CommitPushBlocked(outcome.reason)
      is FeatureTaskRuntimeSubtaskFinalised -> CommitPushSettled(
        FeatureTaskRuntimeRunLoopSubtaskCommit.revalidated( outputValidator, run.phaseId, FeatureTaskRuntimeSubtaskFinalisation.withCommitSha(normalizedOutput.envelopeWireMap(), outcome.commitSha)),
      )
    }
  }

  private fun finaliseSubtaskCommitOutcome(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, diagnostics: RuntimeDiagnostics, phaseGates: FeatureTaskRuntimePhaseGates, goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder, args: FinaliseSubtaskCommitOutcomeArgs): FeatureTaskRuntimeSubtaskFinalisationResult = FeatureTaskRuntimeSubtaskFinalisation(
    gitOperations = phaseGates.gitOperations,
    repoRoot = request.repoRoot,
    record = { record -> runCatching { diagnostics.warning(record) } },
    recordCommit = { commitSha, stagedPaths ->
      FeatureTaskRuntimeRunLoopSubtaskCommit.recordFinalisedCheckpointIdentity(
        request, state, recorder,
        diagnostics,
        RecordFinalisedCheckpointIdentityArgs(
          args.phase.phaseId,
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
      handoff = args.handoff,
      metadata = FeatureTaskRuntimeCheckpointMetadata(
        phaseId = args.phase.phaseId,
        loopId = null,
        generation = FeatureTaskRuntimeRunLoopCheckpoint.checkpointGeneration(state, null),
        branch = args.branch,
        intent = FeatureTaskRuntimeCheckpointMessage.INTENT_FINALISED_SUBTASK,
      ),
      manifestCommitSha = goalContinuationManifestCommitSha( goalContinuationRecorder),
    ),
  )
}

internal fun goalContinuationManifestCommitSha( goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder): String? = null

private data class FinaliseSubtaskCommitOutcomeArgs(
  val goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
  val outputValidator: FeatureTaskRuntimePhaseOutputValidator,
  val phaseGates: FeatureTaskRuntimePhaseGates,
  val phase: PhaseRun,
  val branch: String,
  val handoff: FeatureTaskRuntimeCommitPushHandoff,
  val identity: FeatureTaskRuntimeSubtaskCommitIdentity,
  val ledger: SubtaskCommitLedgerState,
)
