package skillbill.engine.featuretask

import skillbill.application.diagnostics.model.RejectedOutputDiagnosticRequest
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.engine.diagnostics.RuntimeDiagnosticsBestEffortWarning
import skillbill.engine.featuretask.model.FeatureTaskRuntimeCommitPushHandoff
import skillbill.engine.featuretask.model.FeatureTaskRuntimeCommitPushHandoffInvalid
import skillbill.engine.featuretask.model.FeatureTaskRuntimeCommitPushHandoffValid
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRejectedOutputWrite
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunRequest
import skillbill.engine.featuretask.model.FeatureTaskRuntimeSubtaskCommitIdentity
import skillbill.engine.featuretask.model.FeatureTaskRuntimeSubtaskFinalisationBlocked
import skillbill.engine.featuretask.model.FeatureTaskRuntimeSubtaskFinalisationResult
import skillbill.engine.featuretask.model.FeatureTaskRuntimeSubtaskFinalised
import skillbill.error.FeatureTaskRuntimePhaseOutputFailureKind
import skillbill.error.InvalidFeatureTaskRuntimePhaseOutputSchemaError
import skillbill.error.InvalidFeatureTaskRuntimeValidationEvidenceSchemaError
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.ports.diagnostics.model.ProducerOutputEvidence
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import skillbill.ports.workflow.gitops.stagedPaths
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.model.workflowStepStatus
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseOutputValidator
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.FeatureTaskRuntimeWorkflowArtifactMap
import skillbill.workflow.taskruntime.envelopeWireMap
import skillbill.workflow.taskruntime.model.CorrectiveRepairCapturedResponse
import skillbill.workflow.taskruntime.model.CorrectiveRepairDiagnosticLocator
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeCorrectiveRepairContext
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeFailureDisposition
import skillbill.workflow.taskruntime.model.NormalizedFeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.requireAcceptedOutput
import java.time.Clock

internal data class SettleValidatedOutputCommitArgs(
  val request: FeatureTaskRuntimeRunRequest,
  val state: FeatureTaskRuntimeRunState,
  val recorder: FeatureTaskRuntimePhaseRecorder,
  val diagnostics: RuntimeDiagnostics,
  val outputValidator: FeatureTaskRuntimePhaseOutputValidator,
  val phaseGates: FeatureTaskRuntimePhaseGates,
  val goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
  val session: FeatureTaskRuntimeRunLoopSession,
  val run: PhaseRun,
  val capture: ValidatedOutputCapture,
  val attested: NormalizedFeatureTaskRuntimePhaseOutput,
  val observability: FeatureTaskRuntimeRunObservability,
)

internal data class FinaliseSubtaskCommitArgs(
  val request: FeatureTaskRuntimeRunRequest,
  val state: FeatureTaskRuntimeRunState,
  val recorder: FeatureTaskRuntimePhaseRecorder,
  val diagnostics: RuntimeDiagnostics,
  val outputValidator: FeatureTaskRuntimePhaseOutputValidator,
  val phaseGates: FeatureTaskRuntimePhaseGates,
  val session: FeatureTaskRuntimeRunLoopSession,
  val run: PhaseRun,
  val normalizedOutput: NormalizedFeatureTaskRuntimePhaseOutput,
)

object FeatureTaskRuntimeRunLoopAttemptSettlement {
  internal fun rejectedOutputTargeting(args: RejectedOutputTargetingArgs): RejectedOutputTargeting =
    RejectedOutputTargeting(
      phaseId = args.phaseId,
      agentId = args.agentId,
      model = args.model,
      path = args.path,
      repairTurn = args.repairTurn,
    )

  internal fun gateOutput(args: GateOutput): AttemptResult {
    gateOutputEarlyExit(args)?.let { return it }
    settleFromPersistedEnvelope(args)?.let { return it }
    return try {
      val run = args.run
      val acceptedOutput = args.outputValidator
        .validatePhaseOutput(args.captured.text, sourceLabel = run.phaseId)
        .requireAcceptedOutput(run.phaseId)
      settleValidatedOutput(
        SettleValidatedOutput(
          run = run,
          iteration = args.iteration,
          output = SettledOutputContext(
            normalizedOutput = acceptedOutput.normalizedOutput,
            repairEvidence = acceptedOutput.repairEvidence,
            observability = args.observability,
            fileManifest = args.fileManifest,
            captured = args.captured,
          ),
          settlementContext = args.settlementContext,
        ),
      )
    } catch (error: InvalidFeatureTaskRuntimePhaseOutputSchemaError) {
      gateOutputSchemaInvalid(args.state, args.recorder, args, error)
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

  internal fun recordRejectedOutput(
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    args: RecordRejectedOutputArgs,
  ): FeatureTaskRuntimeRejectedOutputWrite {
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
        exhaustedFixLoop = args.exhaustedFixLoop,
      ),
      state.evidenceGeneration(targeting.phaseId),
    )
  }

  internal fun persistChildProcessFailureOutput(
    context: FeatureTaskRuntimeRunLoopContext,
    run: PhaseRun,
    iteration: Int,
    reason: String,
    childOutput: FeatureTaskRuntimeChildOutput?,
  ) {
    val output = childOutput ?: return
    runCatching {
      recordRejectedOutput(
        context.state,
        context.recorder,
        RecordRejectedOutputArgs(
          run = run,
          iteration = iteration,
          rule = FEATURE_TASK_RUNTIME_PROCESS_FAILURE_RULE,
          reason = boundedSchemaGateDetail(reason),
          captured = CapturedPhaseOutput.fromBytes(output.storedBody().encodeToByteArray()),
          targeting = rejectedOutputTargeting(defaultRejectedOutputTargetingArgs(run)),
        ),
      )
    }.onFailure { error ->
      RuntimeDiagnosticsBestEffortWarning.record(
        context.diagnostics,
        "Feature-task-runtime could not persist the child process-failure diagnostic for issue " +
          "${context.request.issueKey}, workflow ${context.request.workflowId}, phase '${run.phaseId}'. The block " +
          "reason keeps its bounded excerpt; the full child output is lost.",
        error,
      )
    }
  }

  internal fun settleValidatedOutput(args: SettleValidatedOutput): AttemptResult {
    val run = args.run
    val iteration = args.iteration
    val attested = FeatureTaskRuntimeRunLoopOutputVerification.attestAbsentGateValidationReceipt(
      args.outputValidator,
      run,
      args.output.normalizedOutput,
    )
    val capture = ValidatedOutputCapture(
      run = run,
      iteration = iteration,
      captured = args.output.captured,
      repairEvidence = args.output.repairEvidence,
      fileManifest = args.output.fileManifest,
    )
    try {
      if (!shouldSkipValidationEvidenceRequirement(run)) {
        requireValidationEvidenceForValidateSettlement(
          args.recorder,
          args.phaseGates,
          run,
          attested.envelopeWireMap(),
        )
      }
      return settleValidatedOutputWithEvidence(
        args,
        capture,
        attested,
      )
    } catch (error: InvalidFeatureTaskRuntimeValidationEvidenceSchemaError) {
      return rejectValidatedOutput(
        args,
        capture,
        attested.envelopeWireMap(),
        "validation-evidence",
        error.message.orEmpty(),
      )
    }
  }

  private fun settleValidatedOutputWithEvidence(
    args: SettleValidatedOutput,
    capture: ValidatedOutputCapture,
    attested: NormalizedFeatureTaskRuntimePhaseOutput,
  ): AttemptResult {
    val state = args.state
    val recorder = args.recorder
    val phaseGates = args.phaseGates
    val observability = args.observability
    val outputMap = attested.envelopeWireMap()
    fun reject(rule: String, detail: String): AttemptResult = rejectValidatedOutput(
      args,
      capture,
      outputMap,
      rule,
      detail,
    )
    FeatureTaskRuntimeRunLoopAttemptSettlement.settleValidatedOutputBoundary(
      ValidatedOutputBoundaryContext(state, recorder, phaseGates),
      capture,
      outputMap,
      ::reject,
    )?.let { return it }
    FeatureTaskRuntimeRunLoopOutputVerification.firstValidatedOutputRejection(capture.run.phaseId, outputMap)?.let { (
      rule,
      reason,
    ),
      ->
      return reject(rule, reason)
    }
    val fingerprintResolution = FeatureTaskRuntimeRunLoopAttemptSettlement.resolveRepositoryFingerprint(
      PhaseAttemptContext(
        run = capture.run,
        state = state,
        iteration = capture.iteration,
        observability = observability,
      ),
      recorder,
      phaseGates,
      capture.fileManifest,
    )
    fingerprintResolution.blocked?.let { return it }
    return with(args.settlementContext) {
      settleValidatedOutputAfterFingerprint(
        SettleValidatedOutputAfterFingerprintArgs(
          capture = capture,
          attested = attested,
          repairEvidence = capture.repairEvidence,
          observability = observability,
          repositoryFingerprint = fingerprintResolution.fingerprint,
          reject = ::reject,
        ),
      )
    }
  }

  internal fun settleFromPersistedEnvelope(args: GateOutput): AttemptResult? {
    val settlementEnvelope = loadPersistedSettlementEnvelope(
      args.state,
      args.recorder,
      args.phaseSettlementService,
      args,
    ) ?: return null
    return settlePersistedEnvelope(args, settlementEnvelope)
  }

  private fun loadPersistedSettlementEnvelope(
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    phaseSettlementService: FeatureTaskPhaseSettlementService,
    args: GateOutput,
  ): FeatureTaskRuntimeWorkflowArtifactMap? {
    val run = args.run
    return try {
      phaseSettlementService.findEnvelope(
        workflowId = run.request.workflowId,
        phaseId = run.phaseId,
        attempt = args.iteration,
      )?.envelope
    } catch (error: InvalidFeatureTaskRuntimeValidationEvidenceSchemaError) {
      clearAndRecordPersistedEvidenceFailure(args.state, args.recorder, args.phaseSettlementService, args, error)
      null
    }
  }

  private fun settlePersistedEnvelope(
    args: GateOutput,
    settlementEnvelope: FeatureTaskRuntimeWorkflowArtifactMap,
  ): AttemptResult? {
    val run = args.run
    return try {
      val acceptedOutput = args.outputValidator
        .validatePhaseOutput(JsonCodec.mapToJsonString(settlementEnvelope), sourceLabel = run.phaseId)
        .requireAcceptedOutput(run.phaseId)
      validatePersistedValidationEvidence(
        args.recorder,
        args.phaseGates,
        run,
        acceptedOutput.normalizedOutput.envelopeWireMap(),
      )
      settleValidatedOutput(
        SettleValidatedOutput(
          run = run,
          iteration = args.iteration,
          output = SettledOutputContext(
            normalizedOutput = acceptedOutput.normalizedOutput,
            repairEvidence = acceptedOutput.repairEvidence,
            observability = args.observability,
            fileManifest = args.fileManifest,
            captured = args.captured,
          ),
          settlementContext = args.settlementContext,
        ),
      )
    } catch (error: InvalidFeatureTaskRuntimePhaseOutputSchemaError) {
      rejectPersistedEnvelopeSchema(
        PersistedEnvelopeSchemaRejection(
          state = args.state,
          recorder = args.recorder,
          phaseSettlementService = args.phaseSettlementService,
          args = args,
          error = error,
        ),
      )
      null
    } catch (error: InvalidFeatureTaskRuntimeValidationEvidenceSchemaError) {
      clearAndRecordPersistedEvidenceFailure(args.state, args.recorder, args.phaseSettlementService, args, error)
      null
    }
  }

  private fun validatePersistedValidationEvidence(
    recorder: FeatureTaskRuntimePhaseRecorder,
    phaseGates: FeatureTaskRuntimePhaseGates,
    run: PhaseRun,
    envelope: FeatureTaskRuntimeWorkflowArtifactMap,
  ) {
    if (shouldSkipValidationEvidenceRequirement(run)) return
    requireValidationEvidenceForValidateSettlement(
      recorder,
      phaseGates,
      run,
      envelope,
    )
  }

  private fun clearAndRecordPersistedEvidenceFailure(
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    phaseSettlementService: FeatureTaskPhaseSettlementService,
    args: GateOutput,
    error: InvalidFeatureTaskRuntimeValidationEvidenceSchemaError,
  ) {
    val run = args.run
    phaseSettlementService.clear(
      workflowId = run.request.workflowId,
      phaseId = run.phaseId,
      attempt = args.iteration,
    )
    FeatureTaskRuntimeRunLoopAttemptSettlement.recordRejectedOutput(
      state,
      recorder,
      RecordRejectedOutputArgs(
        run = run,
        iteration = args.iteration,
        rule = "phase-settlement-validation-evidence",
        reason = error.message.orEmpty(),
        captured = args.captured,
        targeting = FeatureTaskRuntimeRunLoopAttemptSettlement.rejectedOutputTargeting(
          defaultRejectedOutputTargetingArgs(run),
        ),
        exhaustedFixLoop = args.rejectionExhaustsFixLoop,
      ),
    )
  }

  private fun rejectPersistedEnvelopeSchema(args: PersistedEnvelopeSchemaRejection) {
    val state = args.state
    val recorder = args.recorder
    val phaseSettlementService = args.phaseSettlementService
    val output = args.args
    val run = output.run
    val error = args.error
    phaseSettlementService.clear(
      workflowId = run.request.workflowId,
      phaseId = run.phaseId,
      attempt = output.iteration,
    )
    FeatureTaskRuntimeRunLoopOutputVerification.persistVerifyFindingsCheckpointIfPresent(
      recorder,
      run,
      output.captured.text,
    )
    FeatureTaskRuntimeRunLoopAttemptSettlement.recordRejectedOutput(
      state,
      recorder,
      RecordRejectedOutputArgs(
        run = run,
        iteration = output.iteration,
        rule = "phase-settlement-schema",
        reason = error.reason,
        captured = output.captured,
        targeting = FeatureTaskRuntimeRunLoopAttemptSettlement.rejectedOutputTargeting(
          defaultRejectedOutputTargetingArgs(
            run,
            RejectedOutputTargetingOverrides(
              path = FeatureTaskRuntimeRunLoopRecordRejection.rejectionPath(error.reason),
            ),
          ),
        ),
        exhaustedFixLoop = output.rejectionExhaustsFixLoop,
      ),
    )
  }

  private data class PersistedEnvelopeSchemaRejection(
    val state: FeatureTaskRuntimeRunState,
    val recorder: FeatureTaskRuntimePhaseRecorder,
    val phaseSettlementService: FeatureTaskPhaseSettlementService,
    val args: GateOutput,
    val error: InvalidFeatureTaskRuntimePhaseOutputSchemaError,
  )
  internal fun gateOutputEarlyExit(args: GateOutput): AttemptResult? {
    val run = args.run
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
    if (
      run.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE &&
      run.agentRunValidateFallback
    ) {
      return persistQualityCheckCompletion(args)
    }
    if (
      run.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE &&
      !run.agentRunValidateFallback
    ) {
      return AttemptResult.settled(
        PhaseOutcome.completed(
          FeatureTaskRuntimeRunLoopValidationGate.gateRepairSegmentOutput(run, args.iteration),
        ),
      )
    }
    if (runtimeOwnedGateAgentTurn(run)) {
      val outputMap = FeatureTaskRuntimeRunLoopValidationGate.looseOutputEnvelope(args.captured.text)
      val operatorTerminalQualityGate = outputMap?.let { envelope ->
        val disposition = FeatureTaskRuntimePhaseSafetyPolicy.dispositionForTerminalOutput(run.phaseId, envelope)
        !disposition.retryOnResume &&
          run.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_BUILD
      } == true
      if (!operatorTerminalQualityGate) {
        return AttemptResult.settled(
          PhaseOutcome.completed(
            FeatureTaskRuntimeRunLoopValidationGate.gateRepairSegmentOutput(run, args.iteration),
          ),
        )
      }
    }
    return null
  }

  private fun persistQualityCheckCompletion(args: GateOutput): AttemptResult {
    val completion = FeatureTaskRuntimeRunLoopValidationGate.qualityCheckCompletionOutput(
      args.run,
      args.iteration,
    )
    val accepted = try {
      args.outputValidator
        .validatePhaseOutput(completion.payload, sourceLabel = args.run.phaseId)
        .requireAcceptedOutput(args.run.phaseId)
    } catch (error: InvalidFeatureTaskRuntimePhaseOutputSchemaError) {
      return gateOutputSchemaInvalid(
        args.state,
        args.recorder,
        args,
        error,
      )
    }
    return FeatureTaskRuntimeRunLoopOutputVerification.persistAcceptedOutput(
      args.settlementContext,
      PersistAcceptedOutputArgs(
        run = args.run,
        iteration = args.iteration,
        normalizedOutput = accepted.normalizedOutput,
        repairEvidence = accepted.repairEvidence,
        observability = args.observability,
        fileManifest = args.fileManifest,
        repositoryFingerprint = null,
      ),
    )
  }

  private fun runtimeOwnedGateAgentTurn(run: PhaseRun): Boolean {
    if (run.agentRunValidateFallback) return false
    if (run.phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE &&
      run.phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_BUILD
    ) {
      return false
    }
    return run.validationGateRepair || run.validationGateRepairTurn > 0 ||
      (run.validationGateFindings != null && run.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_BUILD)
  }

  private fun shouldSkipValidationEvidenceRequirement(run: PhaseRun): Boolean {
    if (run.agentRunValidateFallback) return false
    return run.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE
  }

  internal fun gateOutputSchemaInvalid(
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    args: GateOutput,
    error: InvalidFeatureTaskRuntimePhaseOutputSchemaError,
  ): AttemptResult {
    val run = args.run
    FeatureTaskRuntimeRunLoopOutputVerification.persistVerifyFindingsCheckpointIfPresent(
      recorder,
      run,
      args.captured.text,
    )
    val path = FeatureTaskRuntimeRunLoopRecordRejection.rejectionPath(error.reason)
    val reason = FeatureTaskRuntimeRunLoopRecordRejection.payloadFreeRejectionReason("phase-output-schema", path)
    val diagnosticWrite = FeatureTaskRuntimeRunLoopAttemptSettlement.recordRejectedOutput(
      state,
      recorder,
      RecordRejectedOutputArgs(
        run = run,
        iteration = args.iteration,
        rule = "phase-output-schema",
        reason = error.reason,
        captured = args.captured,
        targeting = FeatureTaskRuntimeRunLoopAttemptSettlement.rejectedOutputTargeting(
          defaultRejectedOutputTargetingArgs(run, RejectedOutputTargetingOverrides(path = path)),
        ),
        exhaustedFixLoop = args.rejectionExhaustsFixLoop,
      ),
    )
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

  internal fun resolveRepositoryFingerprint(
    context: PhaseAttemptContext,
    recorder: FeatureTaskRuntimePhaseRecorder,
    phaseGates: FeatureTaskRuntimePhaseGates,
    fileManifest: FeatureTaskRuntimePhaseFileManifest,
  ): RepositoryFingerprintResolution {
    val request = context.run.request
    val result = FeatureTaskRuntimeRunLoopOutputVerification.completedPhaseRepositoryFingerprint(
      phaseGates,
      context.run,
    ) ?: return RepositoryFingerprintResolution(null, null)
    if (result !is WorkflowGitOperationResult.Ok) {
      val blocked = AttemptResult.settled(
        FeatureTaskRuntimeRunLoopPhaseAttempts.blockInPhase(
          request,
          context.state,
          recorder,
          context.observability,
          PhaseBlockRequest(
            run = context.run,
            attemptCount = context.iteration,
            reason =
            "Completed-phase repository fingerprinting failed for '${context.run.phaseId}': ${result.error}",
            observability = context.observability,
            payload = BlockAndPersistPayload(fileManifest = fileManifest),
            failureDisposition = FeatureTaskRuntimeFailureDisposition.PROCESS_FAILURE,
          ),
        ),
      )
      return RepositoryFingerprintResolution(fingerprint = null, blocked = blocked)
    }
    return RepositoryFingerprintResolution(result.value, null)
  }

  internal fun settleValidatedOutputBoundary(
    context: ValidatedOutputBoundaryContext,
    capture: ValidatedOutputCapture,
    outputMap: FeatureTaskRuntimeWorkflowArtifactMap,
    reject: (
      String,
      String,
    ) -> AttemptResult,
  ): AttemptResult? {
    val bodyDelivery = FeatureTaskRuntimeRunLoopOutputVerification
      .findingVerificationBoundaryBodyDeliveryDecision(
        context.state,
        context.recorder,
        context.phaseGates,
        capture.run,
        outputMap,
      )
    return when (bodyDelivery) {
      is BoundaryBodyDeliveryDecision.RejectDecision -> reject("output-verification", bodyDelivery.reason)
      is BoundaryBodyDeliveryDecision.ContinueDecision ->
        AttemptResult.boundaryBodyDelivery(bodyDelivery.reason, capture.fileManifest)
      BoundaryBodyDeliveryDecision.NotApplicable -> null
    }
  }

  internal data class ValidatedOutputBoundaryContext(
    val state: FeatureTaskRuntimeRunState,
    val recorder: FeatureTaskRuntimePhaseRecorder,
    val phaseGates: FeatureTaskRuntimePhaseGates,
  )

  internal fun settleValidatedOutputPauseOrTerminal(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    observability: FeatureTaskRuntimeRunObservability,
    args: SettleValidatedOutputPauseArgs,
  ): AttemptResult? {
    val capture = args.capture
    val outputMap = args.attested.envelopeWireMap()
    val attested = args.attested
    val repairEvidence = args.repairEvidence
    val repositoryFingerprint = args.repositoryFingerprint
    val run = capture.run
    terminalBlockedReasonFrom(run.phaseId, outputMap)?.let { reason ->
      return FeatureTaskRuntimeRunLoopOutputVerification.terminalOutputAttempt(
        request,
        state,
        recorder,
        observability,
        TerminalOutputAttemptArgs(
          run = run,
          iteration = capture.iteration,
          reason = reason,
          normalizedOutput = attested,
          repairEvidence = repairEvidence,
          observability = observability,
          fileManifest = capture.fileManifest,
        ),
      )
    }
    return null
  }

  internal fun settleValidatedOutputCommit(
    args: SettleValidatedOutputCommitArgs,
  ): Pair<NormalizedFeatureTaskRuntimePhaseOutput, AttemptResult?> = when (
    val finalisation = finaliseSubtaskCommit(
      FinaliseSubtaskCommitArgs(
        request = args.request,
        state = args.state,
        recorder = args.recorder,
        diagnostics = args.diagnostics,
        outputValidator = args.outputValidator,
        phaseGates = args.phaseGates,
        session = args.session,
        run = args.run,
        normalizedOutput = args.attested,
      ),
    )
  ) {
    is CommitPushNotApplicable -> args.attested to null
    is CommitPushSettled -> finalisation.output to null
    is CommitPushBlocked -> args.attested to AttemptResult.settled(
      FeatureTaskRuntimeRunLoopPhaseAttempts.blockAndPersistInPhase(
        args.request,
        args.state,
        args.recorder,
        args.goalContinuationRecorder,
        phaseBlockArgs(
          args.run,
          args.capture.iteration,
          finalisation.reason,
          args.observability,
          payload = BlockAndPersistPayload(fileManifest = args.capture.fileManifest),
        ).withDisposition(FeatureTaskRuntimeFailureDisposition.NEEDS_USER_ACTION),
      ),
    )
  }

  internal fun FeatureTaskRuntimeRunLoopContext.settleValidatedOutputAfterFingerprint(
    args: SettleValidatedOutputAfterFingerprintArgs,
  ): AttemptResult {
    return settleValidatedOutputPauseOrTerminal(
      request,
      state,
      recorder,
      observability,
      SettleValidatedOutputPauseArgs(
        capture = args.capture,
        attested = args.attested,
        repairEvidence = args.repairEvidence,
        observability = observability,
        repositoryFingerprint = args.repositoryFingerprint,
      ),
    ) ?: settleValidatedOutputAfterPause(args)
  }

  private fun FeatureTaskRuntimeRunLoopContext.settleValidatedOutputAfterPause(
    args: SettleValidatedOutputAfterFingerprintArgs,
  ): AttemptResult {
    return with(FeatureTaskRuntimeRunLoopOutputVerification) {
      FeatureTaskRuntimeRunLoopOutputVerification.completionProjectionRejection(
        this@settleValidatedOutputAfterPause,
        CompletionProjectionRejectionArgs(
          run = args.capture.run,
          normalizedOutput = args.attested,
          iteration = args.capture.iteration,
          repairEvidence = args.repairEvidence,
          repositoryFingerprint = args.repositoryFingerprint,
        ),
      )
    }?.let { (rule, reason) -> args.reject(rule, reason) }
      ?: with(FeatureTaskRuntimeRunLoopRepairReceipt) {
        settleCompletedImplementationOutput(
          CompletedImplementationSettlementArgs(
            request = request,
            state = state,
            recorder = recorder,
            goalContinuationRecorder = goalContinuationRecorder,
            diagnostics = diagnostics,
            output =
            CompletedImplementationOutputArgs(
              run = args.capture.run,
              normalizedOutput = args.attested,
              reject = args.reject,
              iteration = args.capture.iteration,
              observability = observability,
              fileManifest = args.capture.fileManifest,
            ),
          ),
        )
      }
      ?: with(FeatureTaskRuntimeRunLoopAuditRetry) {
        settleCompletedAuditRound(
          this@settleValidatedOutputAfterPause,
          args.capture,
          args.attested.envelopeWireMap(),
        )
      }
      ?: finalizeValidatedOutputAcceptance(
        FinalizeValidatedOutputAcceptanceArgs(
          capture = args.capture,
          attested = FeatureTaskRuntimeRunLoopAuditRetry.attestedAuditOutputForAcceptance(
            args.attested,
            args.attested.envelopeWireMap(),
          ),
          repairEvidence = args.repairEvidence,
          observability = observability,
          repositoryFingerprint = args.repositoryFingerprint,
        ),
      )
  }

  internal fun FeatureTaskRuntimeRunLoopContext.finalizeValidatedOutputAcceptance(
    args: FinalizeValidatedOutputAcceptanceArgs,
  ): AttemptResult {
    val capture = args.capture
    val attested = args.attested
    val repairEvidence = args.repairEvidence
    val repositoryFingerprint = args.repositoryFingerprint
    val run = capture.run
    val (finalised, commitBlocked) =
      settleValidatedOutputCommit(
        SettleValidatedOutputCommitArgs(
          request = request,
          state = state,
          recorder = recorder,
          diagnostics = diagnostics,
          outputValidator = outputValidator,
          phaseGates = phaseGates,
          goalContinuationRecorder = goalContinuationRecorder,
          session = session,
          run = run,
          capture = capture,
          attested = attested,
          observability = observability,
        ),
      )
    commitBlocked?.let { return it }
    FeatureTaskRuntimeRunLoopAttemptSettlement.retainSettledProducerOutput(request, state, recorder, clock, capture)
    return with(FeatureTaskRuntimeRunLoopOutputVerification) {
      FeatureTaskRuntimeRunLoopOutputVerification.persistAcceptedOutput(
        this@finalizeValidatedOutputAcceptance,
        PersistAcceptedOutputArgs(
          run = run,
          iteration = capture.iteration,
          normalizedOutput = finalised,
          repairEvidence = repairEvidence,
          observability = observability,
          fileManifest = capture.fileManifest,
          repositoryFingerprint = repositoryFingerprint,
        ),
      )
    }
  }

  internal fun rejectValidatedOutput(
    settlement: SettleValidatedOutput,
    capture: ValidatedOutputCapture,
    outputMap: FeatureTaskRuntimeWorkflowArtifactMap,
    rule: String,
    detail: String,
  ): AttemptResult {
    val state = settlement.state
    val recorder = settlement.recorder
    val diagnosticRule = rule
    val path = FeatureTaskRuntimeRunLoopRecordRejection.rejectionPath(detail)
    val reason = FeatureTaskRuntimeRunLoopRecordRejection.payloadFreeRejectionReason(rule, path)
    val retryFacingConstraint = FeatureTaskRuntimeRunLoopRecordRejection.payloadFreeSemanticGateConstraint(
      rule,
      detail,
      outputMap,
    )
    val retryReason = FeatureTaskRuntimeRunLoopRecordRejection.retryRejectionReason(reason, retryFacingConstraint)
    val diagnosticWrite = FeatureTaskRuntimeRunLoopAttemptSettlement.recordRejectedOutput(
      state,
      recorder,
      RecordRejectedOutputArgs(
        run = capture.run,
        iteration = capture.iteration,
        rule = diagnosticRule,
        reason = detail,
        captured = capture.captured,
        targeting = FeatureTaskRuntimeRunLoopAttemptSettlement.rejectedOutputTargeting(
          defaultRejectedOutputTargetingArgs(capture.run, RejectedOutputTargetingOverrides(path = path)),
        ),
      ),
    )
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

  internal fun retainSettledProducerOutput(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    clock: Clock,
    capture: ValidatedOutputCapture,
  ) {
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

  internal fun finaliseSubtaskCommit(args: FinaliseSubtaskCommitArgs): CommitPushFinalisation {
    if (
      args.run.phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_COMMIT_PUSH ||
      (args.normalizedOutput.envelopeWireMap()[SharedPayloadKeys.STATUS] as? String)
        .workflowStepStatus() != WorkflowStepStatus.COMPLETED
    ) {
      return CommitPushNotApplicable
    }
    val subtaskCommit = FeatureTaskRuntimeRunLoopSubtaskCommit
    val branch = subtaskCommit.finalisationBranch(args.request, args.session, args.phaseGates)
      ?: return subtaskCommit.unownedWorktreeCommitSha(
        UnownedWorktreeCommitShaArgs(
          args.request,
          args.outputValidator,
          args.diagnostics,
          args.phaseGates,
          args.run,
          args.normalizedOutput,
        ),
      )
    val handoff = when (
      val read = FeatureTaskRuntimeSubtaskFinalisation.readHandoff(args.normalizedOutput.envelopeWireMap())
    ) {
      is FeatureTaskRuntimeCommitPushHandoffInvalid -> return CommitPushBlocked(read.reason)
      is FeatureTaskRuntimeCommitPushHandoffValid -> read.handoff
    }
    val identity = FeatureTaskRuntimeRunLoopCheckpoint.subtaskCommitIdentity(args.request)
    val ledger =
      FeatureTaskRuntimeRunLoopCheckpoint.subtaskCommitLedgerState(
        args.request,
        args.recorder,
        args.diagnostics,
        identity,
      )
    return finaliseSubtaskCommitResult(args, branch, handoff, identity, ledger)
  }

  private fun finaliseSubtaskCommitResult(
    args: FinaliseSubtaskCommitArgs,
    branch: String,
    handoff: FeatureTaskRuntimeCommitPushHandoff,
    identity: FeatureTaskRuntimeSubtaskCommitIdentity,
    ledger: SubtaskCommitLedgerState,
  ): CommitPushFinalisation {
    val outcome = finaliseSubtaskCommitOutcome(
      FinaliseSubtaskCommitOutcomeArgs(
        request = args.request,
        state = args.state,
        recorder = args.recorder,
        diagnostics = args.diagnostics,
        phaseGates = args.phaseGates,
        phase = args.run,
        branch = branch,
        handoff = handoff,
        identity = identity,
        ledger = ledger,
      ),
    )
    return when (outcome) {
      is FeatureTaskRuntimeSubtaskFinalisationBlocked -> CommitPushBlocked(outcome.reason)
      is FeatureTaskRuntimeSubtaskFinalised -> CommitPushSettled(
        FeatureTaskRuntimeRunLoopSubtaskCommit.revalidated(
          args.outputValidator,
          args.run.phaseId,
          FeatureTaskRuntimeSubtaskFinalisation.withCommitSha(
            args.normalizedOutput.envelopeWireMap(),
            outcome.commitSha,
          ),
        ),
      )
    }
  }

  private fun finaliseSubtaskCommitOutcome(
    args: FinaliseSubtaskCommitOutcomeArgs,
  ): FeatureTaskRuntimeSubtaskFinalisationResult {
    val request = args.request
    val state = args.state
    val recorder = args.recorder
    val diagnostics = args.diagnostics
    val phaseGates = args.phaseGates
    return FeatureTaskRuntimeSubtaskFinalisation(
      gitOperations = phaseGates.gitOperations,
      repoRoot = request.repoRoot,
      record = { record -> RuntimeDiagnosticsBestEffortWarning.record(diagnostics, record) },
      recordCommit = { commitSha, stagedPaths ->
        FeatureTaskRuntimeRunLoopSubtaskCommit.recordFinalisedCheckpointIdentity(
          request,
          state,
          recorder,
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
        manifestCommitSha = null,
      ),
    )
  }
}

private data class FinaliseSubtaskCommitOutcomeArgs(
  val request: FeatureTaskRuntimeRunRequest,
  val state: FeatureTaskRuntimeRunState,
  val recorder: FeatureTaskRuntimePhaseRecorder,
  val diagnostics: RuntimeDiagnostics,
  val phaseGates: FeatureTaskRuntimePhaseGates,
  val phase: PhaseRun,
  val branch: String,
  val handoff: FeatureTaskRuntimeCommitPushHandoff,
  val identity: FeatureTaskRuntimeSubtaskCommitIdentity,
  val ledger: SubtaskCommitLedgerState,
)
