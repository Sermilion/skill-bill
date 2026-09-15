package skillbill.engine.featuretask

import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.workflow.FEATURE_TASK_RUNTIME_CONTRACT_VERSION
import skillbill.contracts.workflow.ValidationEvidencePayloadKeys
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunRequest
import skillbill.engine.featuretask.validation.FeatureTaskRuntimeBuildGateCoordinator
import skillbill.engine.featuretask.validation.FeatureTaskRuntimeValidationGateCoordinator
import skillbill.engine.featuretask.validation.model.ValidationGateAgentRepairLauncher
import skillbill.engine.featuretask.validation.model.ValidationGateAgentRepairResult
import skillbill.engine.featuretask.validation.model.ValidationGateAgentTriageLauncher
import skillbill.engine.featuretask.validation.model.ValidationGateCycleRequest
import skillbill.engine.featuretask.validation.model.ValidationGateCycleResult
import skillbill.engine.featuretask.validation.model.ValidationGateCycleTerminalOutcome
import skillbill.engine.featuretask.validation.model.ValidationGateResolution
import skillbill.engine.featuretask.validation.model.ValidationGateTriageResult
import skillbill.engine.featuretask.validation.resolveRequiredValidationCommand
import skillbill.ports.workflow.gitops.repositoryFingerprint
import skillbill.scaffold.model.ValidationGateDeclaration
import skillbill.workflow.goal.model.ValidationDepth
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseOutputValidator
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.FeatureTaskRuntimeWorkflowArtifactMap
import skillbill.workflow.taskruntime.decodeValidationEvidenceFromArtifact
import skillbill.workflow.taskruntime.envelopeWireMap
import skillbill.workflow.taskruntime.model.AcceptedFeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeFailureDisposition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeValidationEvidence
import skillbill.workflow.taskruntime.model.requireAcceptedOutput
import skillbill.workflow.taskruntime.toWorkflowArtifactMap

object FeatureTaskRuntimeRunLoopValidationGate {
  internal fun FeatureTaskRuntimeRunLoopContext.runDeclaredBuildGateCycle(run: PhaseRun): PhaseOutcome {
    val checkpoint = resolveValidationGateCheckpoint(phaseGates, run)
      ?: return PhaseOutcome.blocked(
        "Build gate cycle could not resolve a repository checkpoint fingerprint.",
      )
    val iteration = state.nextIteration(run.phaseId)
    persistBuildGateRunningPhase(
      request,
      state,
      recorder,
      goalContinuationRecorder,
      observability,
      run,
      iteration,
    )?.let { return it }
    val context = phaseAttemptAccumulatorContext(run, state, iteration, observability, phaseTokenAccumulator)
    val cycle = phaseGates.buildGateCoordinator.execute(
      cycle = buildGateCycleRequest(
        ValidationGateCycleRequestArgs(
          context,

          checkpoint,
        ),
      ),
      onGateRunCount = { observability.validationGateProgress() },
    )
    return settleBuildGateCycleResult(
      SettleBuildGateCycleResultArgs(
        request = request,
        state = state,
        recorder = recorder,
        goalContinuationRecorder = goalContinuationRecorder,
        outputValidator = outputValidator,
        phaseGates = phaseGates,
        run = run,
        iteration = iteration,
        observability = observability,
        checkpoint = checkpoint,
        cycle = cycle,
      ),
    )
  }

  internal fun settleRuntimeOwnedBuild(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    outputValidator: FeatureTaskRuntimePhaseOutputValidator,
    phaseGates: FeatureTaskRuntimePhaseGates,
    run: PhaseRun,
    iteration: Int,
    outputText: String,
    observability: FeatureTaskRuntimeRunObservability,
  ): PhaseOutcome {
    val acceptedOutput = acceptRuntimeOwnedBuild(phaseGates, outputValidator, run, outputText).getOrElse { error ->
      return FeatureTaskRuntimeRunLoopPhaseAttempts.blockAndPersistInPhase(
        request,
        state,
        recorder,
        goalContinuationRecorder,
        phaseBlockArgs(
          run,
          iteration,
          "Runtime-owned build settlement did not validate: ${error.message.orEmpty()}",
          observability,
        ),
      )
    }
    return persistRuntimeOwnedBuildCompletion(
      PersistRuntimeOwnedBuildCompletionArgs(
        request = request,
        state = state,
        recorder = recorder,
        goalContinuationRecorder = goalContinuationRecorder,
        run = run,
        iteration = iteration,
        outputText = outputText,
        observability = observability,
        acceptedOutput = acceptedOutput,
      ),
    )
  }

  private fun acceptRuntimeOwnedBuild(
    phaseGates: FeatureTaskRuntimePhaseGates,
    outputValidator: FeatureTaskRuntimePhaseOutputValidator,
    run: PhaseRun,
    outputText: String,
  ): Result<AcceptedFeatureTaskRuntimePhaseOutput> = runCatching {
    val accepted = outputValidator.validatePhaseOutput(outputText, sourceLabel = run.phaseId)
      .requireAcceptedOutput(run.phaseId)
    val buildReceipt = JsonCodec.anyToStringAnyMap(
      JsonCodec.anyToStringAnyMap(
        accepted.normalizedOutput.envelopeWireMap()[SharedPayloadKeys.PRODUCED_OUTPUTS],
      )?.get("build_receipt"),
    )
    phaseGates.buildReceiptValidator.validateBuildReceipt(
      buildReceipt ?: emptyMap<String, Any?>(),
      sourceLabel = run.phaseId,
    )
    accepted
  }

  private fun persistRuntimeOwnedBuildCompletion(
    args: PersistRuntimeOwnedBuildCompletionArgs,
  ): PhaseOutcome {
    val request = args.request
    val state = args.state
    val recorder = args.recorder
    val run = args.run
    val iteration = args.iteration
    val outputText = args.outputText
    val observability = args.observability
    val acceptedOutput = args.acceptedOutput
    val normalizedOutput = acceptedOutput.normalizedOutput
    val persisted = recorder.recordCompletedPhase(
      FeatureTaskRuntimeRunLoopOutputPersistence.phaseStateRequest(
        request,
        state,
        args.goalContinuationRecorder,
        PhaseStateRequestArgs(
          write = PhaseStateWriteArgs(
            run = run,
            iteration = iteration,
            status = STATUS_COMPLETED,
            finished = true,
            outputArtifact = outputText,
          ),
          extras = PhaseStateRequestAttachments(
            normalizedOutput = normalizedOutput,
            repairEvidence = acceptedOutput.repairEvidence,
          ),
        ),
      ),
    )
    if (!persisted) {
      return FeatureTaskRuntimeRunLoopPhaseAttempts.blockInPhase(
        request,
        state,
        recorder,
        observability,
        PhaseBlockRequest(
          run = run,
          attemptCount = iteration,
          reason = "Runtime-owned build settlement could not be persisted.",
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
        acceptedOutput.repairEvidence,
      ),
    )
  }

  internal fun FeatureTaskRuntimeRunLoopContext.launchValidationGateTriage(
    args: ValidationGateTriageArgs,
  ): ValidationGateTriageResult {
    val run = args.context.attempt.run
    val state = args.context.attempt.state
    val iteration = args.context.attempt.iteration
    val observability = args.context.attempt.observability
    val phaseTokenAccumulator = args.context.phaseTokenAccumulator
    val findings = args.findings
    val triageRun = run.copy(validationGateFindings = findings, validationGateTriage = true)
    val attempt = with(FeatureTaskRuntimeRunLoopRecordRejection) {
      this@launchValidationGateTriage.attemptOnce(
        recordRejectionAttemptArgs(
          PhaseAttemptContext(triageRun, state, iteration, observability),
          phaseTokenAccumulator = args.context.phaseTokenAccumulator,
        ),
      )
    }
    val settled = attempt.settledOutcome
    val completed = settled?.completedOutput
    return when {
      completed != null -> FeatureTaskRuntimeRunLoopValidationGate.extractValidationGateTriagePlan(completed)
      settled != null -> ValidationGateTriageResult.Empty
      else -> ValidationGateTriageResult.Empty
    }
  }

  internal fun FeatureTaskRuntimeRunLoopContext.buildGateCycleRequest(
    args: ValidationGateCycleRequestArgs,
  ): ValidationGateCycleRequest = validationGateCycleRequest(args).copy(validationDepth = ValidationDepth.DEFAULT)

  internal fun persistBuildGateRunningPhase(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    observability: FeatureTaskRuntimeRunObservability,
    run: PhaseRun,
    iteration: Int,
  ): PhaseOutcome? {
    val runningPhaseState = FeatureTaskRuntimeRunLoopOutputPersistence.phaseStateRequest(
      request,
      state,
      goalContinuationRecorder,

      PhaseStateRequestArgs(
        write = PhaseStateWriteArgs(
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
          reason = "Build gate cycle could not persist running build phase before gate execution.",
          observability = observability,
          failureDisposition = FeatureTaskRuntimeFailureDisposition.PROCESS_FAILURE,
        ),
      )
    }
    observability.started(
      run.phaseId,
      run.resolvedAgent.resolvedAgentId,
      iteration,
      run.modelDirective,
      FeatureTaskRuntimePhaseStartReentry.FIRST_VISIT,
    )
    return null
  }

  internal fun settleBuildGateCycleResult(
    args: SettleBuildGateCycleResultArgs,
  ): PhaseOutcome {
    val request = args.request
    val state = args.state
    val recorder = args.recorder
    val goalContinuationRecorder = args.goalContinuationRecorder
    val outputValidator = args.outputValidator
    val phaseGates = args.phaseGates
    val run = args.run
    val iteration = args.iteration
    val observability = args.observability
    val checkpoint = args.checkpoint
    val cycle = args.cycle
    return when (cycle) {
      ValidationGateCycleResult.AbsentFallback ->
        settleRuntimeOwnedBuild(
          request,
          state,
          recorder,
          goalContinuationRecorder,
          outputValidator,
          phaseGates,
          run,
          iteration,

          FeatureTaskRuntimeBuildGateCoordinator.runtimeOwnedBuildOutput(
            repositoryCheckpoint = checkpoint,
            measurements = emptyList(),
          ).payload,
          observability,
        )
      is ValidationGateCycleResult.Terminal ->
        when (val terminal = cycle.outcome) {
          is ValidationGateCycleTerminalOutcome.Completed ->
            settleRuntimeOwnedBuild(
              request,
              state,
              recorder,
              goalContinuationRecorder,
              outputValidator,
              phaseGates,
              run,
              iteration,
              terminal.output.payload,

              observability,
            )
          is ValidationGateCycleTerminalOutcome.Blocked ->
            FeatureTaskRuntimeRunLoopPhaseAttempts.blockInPhase(
              request,
              state,
              recorder,
              observability,
              PhaseBlockRequest(
                run = run,
                attemptCount = iteration,
                reason = terminal.reason,
                observability = observability,
                failureDisposition = terminal.failureDisposition
                  ?: FeatureTaskRuntimeFailureDisposition.NEEDS_USER_ACTION,
              ),
            )
        }
    }
  }

  internal fun looseOutputEnvelope(outputText: String): FeatureTaskRuntimeWorkflowArtifactMap? {
    val trimmed = outputText.trim()
    JsonCodec.parseObjectOrNull(trimmed)?.let {
      return JsonCodec.anyToStringAnyMap(JsonCodec.jsonElementToValue(it))?.toWorkflowArtifactMap()
    }
    val start = trimmed.indexOf('{')
    val end = trimmed.lastIndexOf('}')
    if (start !in 0..<end) return null
    return JsonCodec.parseObjectOrNull(trimmed.substring(start, end + 1))
      ?.let { JsonCodec.anyToStringAnyMap(JsonCodec.jsonElementToValue(it))?.toWorkflowArtifactMap() }
  }

  internal fun gateTriageCapturedProducedOutputs(outputText: String): Map<String, Any?> {
    val produced = looseOutputEnvelope(outputText)
      ?.let { JsonCodec.anyToStringAnyMap(it[SharedPayloadKeys.PRODUCED_OUTPUTS]) }
      ?: return emptyMap()
    return buildMap {
      produced[SharedPayloadKeys.VALUE]?.let { put(SharedPayloadKeys.VALUE, it) }
      produced["validation_repair_plan"]?.let { put("validation_repair_plan", it) }
    }
  }

  internal fun gateRepairSegmentOutput(run: PhaseRun, iteration: Int): FeatureTaskRuntimePhaseOutput =
    FeatureTaskRuntimePhaseOutput(
      phaseId = run.phaseId,
      iteration = iteration,
      payload =
      """{"${SharedPayloadKeys.CONTRACT_VERSION}":"$FEATURE_TASK_RUNTIME_CONTRACT_VERSION",""" +
        """"${SharedPayloadKeys.PHASE_ID}":"${run.phaseId}",""" +
        """"${SharedPayloadKeys.STATUS}":"completed",""" +
        """"${SharedPayloadKeys.SUMMARY}":"Gate repair segment.",""" +
        """"${SharedPayloadKeys.PRODUCED_OUTPUTS}":{}}""",
    )

  internal fun gateTriageSegmentOutput(
    run: PhaseRun,
    iteration: Int,
    outputText: String,
  ): FeatureTaskRuntimePhaseOutput {
    val captured = gateTriageCapturedProducedOutputs(outputText)
    return FeatureTaskRuntimePhaseOutput(
      phaseId = run.phaseId,
      iteration = iteration,
      payload =
      JsonCodec.mapToJsonString(
        mapOf(
          SharedPayloadKeys.CONTRACT_VERSION to FEATURE_TASK_RUNTIME_CONTRACT_VERSION,
          SharedPayloadKeys.PHASE_ID to run.phaseId,
          SharedPayloadKeys.STATUS to "completed",
          SharedPayloadKeys.SUMMARY to "Gate triage segment.",
          SharedPayloadKeys.PRODUCED_OUTPUTS to captured,
        ),
      ),
    )
  }

  internal fun extractValidationGateTriagePlan(output: FeatureTaskRuntimePhaseOutput): ValidationGateTriageResult {
    val envelope = FeatureTaskRuntimeRunLoopLaunch.outputEnvelopeOf(output)
      ?: return ValidationGateTriageResult.Empty
    val produced = JsonCodec.anyToStringAnyMap(envelope[SharedPayloadKeys.PRODUCED_OUTPUTS])
      ?: return ValidationGateTriageResult.Empty
    FeatureTaskRuntimeRunLoopValidationGate.planFromProducedValue(produced[SharedPayloadKeys.VALUE])?.let { return it }
    val directPlan = FeatureTaskRuntimeRunLoopValidationGate
      .extractTriagePlanProse(produced["validation_repair_plan"])
    return if (!directPlan.isNullOrBlank()) {
      ValidationGateTriageResult.Captured(directPlan)
    } else {
      ValidationGateTriageResult.Empty
    }
  }

  internal fun planFromProducedValue(value: Any?): ValidationGateTriageResult? {
    val valueText = value as? String ?: return null
    if (valueText.isBlank()) return null
    val inner = JsonCodec.parseObjectOrNull(valueText)
      ?.let(JsonCodec::jsonElementToValue)
      ?.let(JsonCodec::anyToStringAnyMap)
    val planFromValue = inner?.let { extractTriagePlanProse(it["validation_repair_plan"]) }
    if (!planFromValue.isNullOrBlank()) {
      return ValidationGateTriageResult.Captured(planFromValue)
    }
    return if (inner == null) ValidationGateTriageResult.Captured(valueText) else null
  }

  internal fun extractTriagePlanProse(raw: Any?): String? = when (raw) {
    is String -> raw.takeIf { it.isNotBlank() }
    null -> null
    else -> JsonCodec.mapToJsonString(
      JsonCodec.anyToStringAnyMap(raw) ?: mapOf("validation_repair_plan" to raw),
    ).takeIf { it.isNotBlank() && it != "{}" && it != "[]" }
  }

  internal fun FeatureTaskRuntimeRunLoopContext.launchValidationGateRepair(
    args: ValidationGateRepairArgs,
  ): ValidationGateAgentRepairResult {
    val run = args.context.attempt.run
    val state = args.context.attempt.state
    val iteration = args.context.attempt.iteration
    val observability = args.context.attempt.observability
    val phaseTokenAccumulator = args.context.phaseTokenAccumulator
    val repairTurn = args.repairTurn
    val repairRun = run.copy(
      validationGateFindings = null,
      validationGateRepairTurn = repairTurn,
      validationGateTriagePlan = null,
      validationGateRepair = true,
    )
    val attempt = with(FeatureTaskRuntimeRunLoopRecordRejection) {
      this@launchValidationGateRepair.attemptOnce(
        recordRejectionAttemptArgs(
          PhaseAttemptContext(repairRun, state, iteration, observability),
          phaseTokenAccumulator = phaseTokenAccumulator,
        ),
      )
    }
    val settled = attempt.settledOutcome
    val completed = settled?.completedOutput
    return when {
      completed != null -> ValidationGateAgentRepairResult.Completed(completed)
      settled != null -> ValidationGateAgentRepairResult.Blocked(
        settled.blockedReason
          ?: settled.pausedReason
          ?: "Validation repair attempt persistence.session.blocked.",
        failureDisposition = recorder.loadPhaseRecords(run.request.workflowId)
          ?.get(run.phaseId)
          ?.failureDisposition,
      )
      else -> ValidationGateAgentRepairResult.Completed(
        FeatureTaskRuntimePhaseOutput(
          phaseId = run.phaseId,
          iteration = iteration,
          payload =
          """{"${SharedPayloadKeys.CONTRACT_VERSION}":"$FEATURE_TASK_RUNTIME_CONTRACT_VERSION",""" +
            """"${SharedPayloadKeys.PHASE_ID}":"${run.phaseId}",""" +
            """"${SharedPayloadKeys.STATUS}":"completed",""" +
            """"${SharedPayloadKeys.SUMMARY}":"Gate repair segment.",""" +
            """"${SharedPayloadKeys.PRODUCED_OUTPUTS}":{}}""",
        ),
      )
    }
  }

  internal fun settleRuntimeOwnedValidation(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    outputValidator: FeatureTaskRuntimePhaseOutputValidator,
    phaseGates: FeatureTaskRuntimePhaseGates,
    session: FeatureTaskRuntimeRunLoopSession,
    run: PhaseRun,
    iteration: Int,
    outputText: String,
    observability: FeatureTaskRuntimeRunObservability,
  ): PhaseOutcome {
    val acceptedOutput = runCatching {
      val accepted = outputValidator.validatePhaseOutput(
        outputText,
        sourceLabel = run.phaseId,
      ).requireAcceptedOutput(run.phaseId)
      val produced = JsonCodec.anyToStringAnyMap(
        accepted.normalizedOutput.envelopeWireMap()[SharedPayloadKeys.PRODUCED_OUTPUTS],
      )
      val validationResult = JsonCodec.anyToStringAnyMap(
        produced?.get(ValidationEvidencePayloadKeys.VALIDATION_RESULT),
      )
      val evidence = JsonCodec.anyToStringAnyMap(
        validationResult?.get(ValidationEvidencePayloadKeys.VALIDATION_EVIDENCE),
      )?.let { raw ->
        decodeValidationEvidenceFromArtifact(raw, run.phaseId)!!
      } ?: error("Runtime-owned validation evidence is missing.")
      evidence.requireSuccessfulCommand(
        requiredValidationCommand(
          RequiredValidationCommandArgs(
            phaseGates = phaseGates,
            run = run,
            evidence = evidence,
            changedPaths = validationChangedPaths(
              phaseGates,
              recorder,
              goalContinuationRecorder,
              session,
              run,
            ),
          ),
        ),
        run.phaseId,
      )
      accepted
    }.getOrElse { error ->
      return FeatureTaskRuntimeRunLoopPhaseAttempts.blockAndPersistInPhase(
        request,
        state,
        recorder,
        goalContinuationRecorder,
        phaseBlockArgs(
          run,
          iteration,
          "Runtime-owned validation settlement did not validate: ${error.message.orEmpty()}",
          observability,
        ),
      )
    }
    return finishRuntimeOwnedValidation(
      RuntimeOwnedValidationFinishArgs(
        request,
        state,
        recorder,
        goalContinuationRecorder,
        outputValidator,
        phaseGates,
        run,
        iteration,
        outputText,
        acceptedOutput,

        observability,
      ),
    )
  }

  private fun finishRuntimeOwnedValidation(args: RuntimeOwnedValidationFinishArgs): PhaseOutcome {
    val request = args.request
    val state = args.state
    val recorder = args.recorder
    val run = args.run
    val iteration = args.iteration
    val outputText = args.outputText
    val acceptedOutput = args.acceptedOutput
    val observability = args.observability
    val normalizedOutput = acceptedOutput.normalizedOutput
    val persisted = recorder.recordCompletedPhase(
      FeatureTaskRuntimeRunLoopOutputPersistence.phaseStateRequest(
        request,
        state,
        args.goalContinuationRecorder,
        PhaseStateRequestArgs(
          write = PhaseStateWriteArgs(
            run = run,
            iteration = iteration,
            status = STATUS_COMPLETED,
            finished = true,
            outputArtifact = outputText,
          ),
          extras = PhaseStateRequestAttachments(
            normalizedOutput = normalizedOutput,
            repairEvidence = acceptedOutput.repairEvidence,
          ),
        ),
      ),
    )
    if (!persisted) {
      return FeatureTaskRuntimeRunLoopPhaseAttempts.blockInPhase(
        request,
        state,
        recorder,
        observability,
        PhaseBlockRequest(
          run = run,
          attemptCount = iteration,
          reason = "Runtime-owned validation settlement could not be persisted.",
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
        acceptedOutput.repairEvidence,
      ),
    )
  }

  internal fun validationChangedPaths(
    phaseGates: FeatureTaskRuntimePhaseGates,
    recorder: FeatureTaskRuntimePhaseRecorder,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    session: FeatureTaskRuntimeRunLoopSession,
    run: PhaseRun,
  ): List<String>? =
    with(FeatureTaskRuntimeRunLoopOutputVerification) {
      resolveRepositoryCheckpoint(
        RepositoryCheckpointResolutionArgs(
          recorder = recorder,
          goalContinuationRecorder = goalContinuationRecorder,
          phaseGates = phaseGates,
          session = session,
          run = run,
        ),
      )
        ?.workingTreeOwnedPaths
        ?.distinct()
        ?.sorted()
    }

  internal data class RequiredValidationCommandArgs(
    val phaseGates: FeatureTaskRuntimePhaseGates,
    val run: PhaseRun,
    val evidence: FeatureTaskRuntimeValidationEvidence,
    val changedPaths: List<String>?,
  )

  internal fun requiredValidationCommand(args: RequiredValidationCommandArgs): String = requireNotNull(
    resolveRequiredValidationCommand(
      resolver = args.phaseGates.validationGateResolver,
      requiredCommandForDeclaration = { declaration ->
        args.phaseGates.validationGateCoordinator.requiredValidationCommand(
          args.run.request.repoRoot,
          args.run.request.workflowId,
          declaration,
        )
      },
      changedPaths = args.changedPaths,
      evidence = args.evidence,
      sourceLabel = args.run.phaseId,
    ),
  )

  internal fun declaredValidationGateDeclaration(
    phaseGates: FeatureTaskRuntimePhaseGates,
    recorder: FeatureTaskRuntimePhaseRecorder,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    session: FeatureTaskRuntimeRunLoopSession,
    run: PhaseRun,
  ): ValidationGateDeclaration? {
    if (run.phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE) {
      return null
    }
    return when (
      val resolution = phaseGates.validationGateResolver.resolve(
        validationChangedPaths(phaseGates, recorder, goalContinuationRecorder, session, run).orEmpty(),
      )
    ) {
      is ValidationGateResolution.Declared -> resolution.declaration
      is ValidationGateResolution.Absent -> null
      is ValidationGateResolution.Incompatible -> null
    }
  }

  internal fun packCollectAllCommand(
    phaseGates: FeatureTaskRuntimePhaseGates,
    recorder: FeatureTaskRuntimePhaseRecorder,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    session: FeatureTaskRuntimeRunLoopSession,
    run: PhaseRun,
  ): String? =
    declaredValidationGateDeclaration(phaseGates, recorder, goalContinuationRecorder, session, run)
      ?.collectAllFullGateCommand
      ?.joinToString(" ")

  internal fun packConfirmationGateCommand(
    phaseGates: FeatureTaskRuntimePhaseGates,
    recorder: FeatureTaskRuntimePhaseRecorder,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    session: FeatureTaskRuntimeRunLoopSession,
    run: PhaseRun,
  ): String? =
    declaredValidationGateDeclaration(phaseGates, recorder, goalContinuationRecorder, session, run)
      ?.cacheBypassingCollectAllFullGateCommand
      ?.joinToString(" ")

  internal fun packBuildCommand(
    phaseGates: FeatureTaskRuntimePhaseGates,
    recorder: FeatureTaskRuntimePhaseRecorder,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    session: FeatureTaskRuntimeRunLoopSession,
    run: PhaseRun,
  ): String? {
    if (run.phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_BUILD) {
      return null
    }
    val validationChangedPaths = validationChangedPaths(
      phaseGates,
      recorder,
      goalContinuationRecorder,
      session,
      run,
    )
    return when (
      val resolution = phaseGates.validationGateResolver.resolve(validationChangedPaths.orEmpty())
    ) {
      is ValidationGateResolution.Declared -> resolution.declaration.buildCommand?.joinToString(" ")
      is ValidationGateResolution.Absent -> null
      is ValidationGateResolution.Incompatible -> null
    }
  }

  internal fun FeatureTaskRuntimeRunLoopContext.runPhaseAttempts(run: PhaseRun): PhaseOutcome {
    val agentId = run.resolvedAgent.resolvedAgentId
    var iteration = state.nextIteration(run.phaseId)
    val continuationSegmentCount = FeatureTaskRuntimeRunLoopPhaseAttempts
      .durableContinuationSegmentCount(recorder, run)
    val nonOutputAttempts = FeatureTaskRuntimeRunLoopPhaseAttempts.durableNonOutputAttempts(state, run)
    prepareFixLoopState(run)?.let { return it }
    val semanticIteration = (
      state.fixLoopIterationFor(run.phaseId, iteration) - continuationSegmentCount - nonOutputAttempts.size
      ).coerceAtLeast(1)
    val crashResumed = state.resumedFromPriorProcess(run.phaseId)
    state.recordPhaseLaunched(run.phaseId)
    FeatureTaskRuntimeRunLoopAuditRetry.clearRetryHintOnFreshLaunch(state, session, run.phaseId)
    observability.started(
      run.phaseId,
      agentId,
      iteration,
      run.modelDirective,
      FeatureTaskRuntimePhaseStartReentry(
        resumed = iteration > 1 || state.hasPriorRecord(run.phaseId),
        startKind = featureTaskRuntimeStartContinuationKind(
          crashResumed = crashResumed,
          verifierReentry = run.reentry?.let {
            transitions.backwardEdges
              .firstOrNull { edge -> edge.loopId == it.loopId }
              ?.destinationPhaseId == it.phaseId
          } == true,
          attemptCount = iteration,
        ),
      ),
    )
    var outcome: PhaseOutcome? = null
    val loop = PhaseAttemptLoopState(
      iteration = iteration,
      malformedAttemptCount = 0,
      outputGateFailures = 0,
      semanticIteration = semanticIteration,
      continuationSegmentCount = continuationSegmentCount,
    )
    while (outcome == null) {
      outcome = resolveFixLoopOutcome(
        FixLoopOutcomeArgs(
          context = phaseAttemptAccumulatorContext(
            run,
            state,
            loop.iteration,
            observability,
            phaseTokenAccumulator,
          ),
          loop = loop,
          agentId = agentId,
        ),
      )
    }
    return outcome
  }

  internal fun FeatureTaskRuntimeRunLoopContext.prepareFixLoopState(run: PhaseRun): PhaseOutcome? {
    if (FeatureTaskRuntimePhaseWorkflowDefinition.singleAgentSessionOnly(run.phaseId)) return null
    val nonOutputAttempts = FeatureTaskRuntimeRunLoopPhaseAttempts.durableNonOutputAttempts(state, run)
    val processFailures = nonOutputAttempts.filterNot(FeatureTaskRuntimeNonOutputAttempt::paused)
    val operatorReopened = FeatureTaskRuntimeRunLoopPhaseAttempts.operatorReopenedPhase(session, run.phaseId)
    if (operatorReopened) state.restartAttemptBudget(run.phaseId)
    if (!operatorReopened) {
      FeatureTaskRuntimeAttemptBudgets
        .processFailureBlockReason(run.phaseId, processFailures.size, processFailures.lastOrNull()?.reason)
        ?.let { reason ->
          return FeatureTaskRuntimeRunLoopPhaseAttempts.blockInPhase(
            request,
            state,
            recorder,
            observability,
            PhaseBlockRequest(
              run = run,
              attemptCount = state.nextIteration(run.phaseId),
              reason = reason,
              observability = observability,
              failureDisposition = FeatureTaskRuntimeFailureDisposition.PROCESS_FAILURE,
            ),
          )
        }
    }
    return null
  }

  internal fun FeatureTaskRuntimeRunLoopContext.resolveFixLoopOutcome(args: FixLoopOutcomeArgs): PhaseOutcome? {
    val run = args.context.attempt.run
    val state = args.context.attempt.state
    val observability = args.context.attempt.observability
    val phaseTokenAccumulator = args.context.phaseTokenAccumulator
    val loop = args.loop
    val agentId = args.agentId
    val attempt = with(FeatureTaskRuntimeRunLoopRecordRejection) {
      this@resolveFixLoopOutcome.attemptOnce(
        recordRejectionAttemptArgs(
          PhaseAttemptContext(run, state, loop.iteration, observability, loop.outputGateFailures),
          priorCorrection = loop.priorCorrection,
          phaseTokenAccumulator = phaseTokenAccumulator,
        ),
      )
    }
    val context = FixLoopBranchContext(run, attempt, loop, observability, agentId)
    val phaseAttempts = FeatureTaskRuntimeRunLoopPhaseAttempts
    return attempt.settledOutcome ?: when {
      attempt.auditRetryContinuation -> phaseAttempts.settleAuditRetry(observability, session, context)
      attempt.incompleteWorkContinuationReason != null -> phaseAttempts.settleIncompleteWork(
        request,
        state,
        recorder,
        observability,

        context,
      )
      attempt.boundaryBodyDeliveryContinuationReason != null ->
        phaseAttempts.settleBoundaryBodyDelivery(observability, context)
      attempt.malformedOutput -> phaseAttempts.settleMalformedOutput(request, state, recorder, observability, context)
      attempt.retryableTerminalRetryReason != null -> phaseAttempts.settleRetryableTerminal(
        request,
        state,
        recorder,
        observability,

        context,
      )
      attempt.findingsOwedKind != null -> phaseAttempts.settleFindingsOwed(
        request,
        state,
        recorder,
        observability,
        context,
      )
      else -> FeatureTaskRuntimeRunLoopPhaseAttempts.settleSemanticFailure(
        request,
        state,
        recorder,
        observability,
        context,
      )
    }
  }

  internal fun FeatureTaskRuntimeRunLoopContext.runDeclaredValidationGateCycle(run: PhaseRun): PhaseOutcome {
    val checkpoint = FeatureTaskRuntimeRunLoopValidationGate.resolveValidationGateCheckpoint(phaseGates, run).orEmpty()
    val iteration = state.nextIteration(run.phaseId)
    val context = phaseAttemptAccumulatorContext(run, state, iteration, observability, phaseTokenAccumulator)
    val cycle = phaseGates.validationGateCoordinator.execute(
      cycle = validationGateCycleRequest(
        ValidationGateCycleRequestArgs(
          context,

          checkpoint,
        ),
      ),
      onGateRunCount = { observability.validationGateProgress() },
    )
    return settleValidationGateCycleResult(
      SettleValidationGateCycleArgs(
        context = context,
        request = request,
        recorder = recorder,
        goalContinuationRecorder = goalContinuationRecorder,
        outputValidator = outputValidator,
        phaseGates = phaseGates,
        session = session,
        cycle = cycle,
      ),
    )
  }

  internal fun FeatureTaskRuntimeRunLoopContext.validationGateCycleRequest(
    args: ValidationGateCycleRequestArgs,
  ): ValidationGateCycleRequest {
    val run = args.context.attempt.run
    val validationDepth = run.request.goalContinuation?.validationDepth ?: ValidationDepth.DEFAULT
    return ValidationGateCycleRequest(
      repoRoot = run.request.repoRoot,
      request = run.request,
      validationDepth = validationDepth,
      changedPaths = validationChangedPaths(
        phaseGates,
        recorder,
        goalContinuationRecorder,
        session,
        run,
      ).orEmpty(),
      repositoryCheckpoint = args.checkpoint,
      agentTriageLauncher = ValidationGateAgentTriageLauncher { findings ->
        launchValidationGateTriage(
          ValidationGateTriageArgs(
            args.context,
            findings,
          ),
        )
      },
      agentRepairLauncher = ValidationGateAgentRepairLauncher { findings, repairIteration, triagePlan ->
        launchValidationGateRepair(
          ValidationGateRepairArgs(
            context = args.context,
            findings = findings,
            repairTurn = repairIteration,
            triagePlan = triagePlan,
          ),
        )
      },
    )
  }

  internal fun resolveValidationGateCheckpoint(phaseGates: FeatureTaskRuntimePhaseGates, run: PhaseRun): String? =
    phaseGates.gitOperations.repositoryFingerprint(run.request.repoRoot).value.takeIf(String::isNotBlank)

  internal fun settleValidationGateCycleResult(
    args: SettleValidationGateCycleArgs,
  ): PhaseOutcome {
    val run = args.context.attempt.run
    val state = args.context.attempt.state
    val observability = args.context.attempt.observability
    val iteration = args.context.attempt.iteration
    val request = args.request
    val recorder = args.recorder
    val goalContinuationRecorder = args.goalContinuationRecorder
    val outputValidator = args.outputValidator
    val phaseGates = args.phaseGates
    val session = args.session
    return when (args.cycle) {
      ValidationGateCycleResult.AbsentFallback -> {
        observability.started(
          run.phaseId,
          run.resolvedAgent.resolvedAgentId,
          iteration,
          run.modelDirective,
          FeatureTaskRuntimePhaseStartReentry.FIRST_VISIT,
        )
        FeatureTaskRuntimeRunLoopPhaseAttempts.blockInPhase(
          request,
          state,
          recorder,
          observability,
          PhaseBlockRequest(
            run = run,
            attemptCount = iteration,
            reason = FeatureTaskRuntimeValidationGateCoordinator.ABSENT_VALIDATION_GATE_REASON,
            observability = observability,
            failureDisposition = FeatureTaskRuntimeFailureDisposition.NEEDS_USER_ACTION,
          ),
        )
      }
      is ValidationGateCycleResult.Terminal -> {
        observability.started(
          run.phaseId,
          run.resolvedAgent.resolvedAgentId,
          iteration,
          run.modelDirective,
          FeatureTaskRuntimePhaseStartReentry.FIRST_VISIT,
        )
        when (val terminal = args.cycle.outcome) {
          is ValidationGateCycleTerminalOutcome.Completed ->
            settleRuntimeOwnedValidation(
              request,
              state,
              recorder,
              goalContinuationRecorder,
              outputValidator,
              phaseGates,
              session,
              run,
              iteration,
              terminal.output.payload,
              observability,
            )
          is ValidationGateCycleTerminalOutcome.Blocked ->
            FeatureTaskRuntimeRunLoopPhaseAttempts.blockInPhase(
              request,
              state,
              recorder,
              observability,
              PhaseBlockRequest(
                run = run,
                attemptCount = iteration,
                reason = terminal.reason,
                observability = observability,
                failureDisposition = terminal.failureDisposition
                  ?: FeatureTaskRuntimeFailureDisposition.NEEDS_USER_ACTION,
              ),
            )
        }
      }
    }
  }

}

private data class RuntimeOwnedValidationFinishArgs(
  val request: FeatureTaskRuntimeRunRequest,
  val state: FeatureTaskRuntimeRunState,
  val recorder: FeatureTaskRuntimePhaseRecorder,
  val goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
  val outputValidator: FeatureTaskRuntimePhaseOutputValidator,
  val phaseGates: FeatureTaskRuntimePhaseGates,
  val run: PhaseRun,
  val iteration: Int,
  val outputText: String,
  val acceptedOutput: AcceptedFeatureTaskRuntimePhaseOutput,
  val observability: FeatureTaskRuntimeRunObservability,
)
