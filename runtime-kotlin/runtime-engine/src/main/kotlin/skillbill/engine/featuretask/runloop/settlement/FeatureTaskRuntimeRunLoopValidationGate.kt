package skillbill.engine.featuretask.runloop.settlement

import skillbill.application.decomposition.baseBranch
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.workflow.featuretask.FEATURE_TASK_RUNTIME_CONTRACT_VERSION
import skillbill.engine.featuretask.lifecycle.branch.Blocked
import skillbill.engine.featuretask.lifecycle.continuation.FeatureTaskRuntimeGoalContinuationRecorder
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeRunRequest
import skillbill.engine.featuretask.phase.core.FeatureTaskRuntimePhaseGates
import skillbill.engine.featuretask.phase.record.FeatureTaskRuntimePhaseRecorder
import skillbill.engine.featuretask.runloop.core.FeatureTaskRuntimeRunLoopContext
import skillbill.engine.featuretask.runloop.core.FeatureTaskRuntimeRunLoopLaunch
import skillbill.engine.featuretask.runloop.core.FeatureTaskRuntimeRunLoopSession
import skillbill.engine.featuretask.runloop.core.PhaseAttemptAccumulatorContext
import skillbill.engine.featuretask.runloop.core.PhaseAttemptContext
import skillbill.engine.featuretask.runloop.core.PhaseBlockRequest
import skillbill.engine.featuretask.runloop.core.PhaseOutcome
import skillbill.engine.featuretask.runloop.core.PhaseRun
import skillbill.engine.featuretask.runloop.core.PhaseStateRequestArgs
import skillbill.engine.featuretask.runloop.core.PhaseStateRequestAttachments
import skillbill.engine.featuretask.runloop.core.PhaseStateWriteArgs
import skillbill.engine.featuretask.runloop.core.ValidationGateCycleRequestArgs
import skillbill.engine.featuretask.runloop.core.ValidationGateRepairArgs
import skillbill.engine.featuretask.runloop.core.ValidationGateTriageArgs
import skillbill.engine.featuretask.runloop.core.phaseAttemptAccumulatorContext
import skillbill.engine.featuretask.runloop.core.phaseBlockArgs
import skillbill.engine.featuretask.runloop.observability.FeatureTaskRuntimePhaseStartReentry
import skillbill.engine.featuretask.runloop.observability.FeatureTaskRuntimeRunObservability
import skillbill.engine.featuretask.runloop.observability.paused
import skillbill.engine.featuretask.runloop.phase.FeatureTaskRuntimeRunLoopPhaseBlocking
import skillbill.engine.featuretask.runloop.state.FeatureTaskRuntimeRunState
import skillbill.engine.featuretask.runloop.state.requirePassedValidationResult
import skillbill.engine.featuretask.runner.STATUS_COMPLETED
import skillbill.engine.featuretask.runner.STATUS_RUNNING
import skillbill.engine.featuretask.slot.attempt.PhaseAttemptLoop
import skillbill.engine.featuretask.slot.attempt.PhaseAttemptOnce
import skillbill.engine.featuretask.slot.attempt.PhaseStepCall
import skillbill.engine.featuretask.slot.attempt.recordRejectionAttemptArgs
import skillbill.engine.featuretask.validation.FeatureTaskRuntimeBuildGateCoordinator
import skillbill.engine.featuretask.validation.FeatureTaskRuntimeValidationGateCoordinator
import skillbill.engine.featuretask.validation.ReadinessPostValidateCaptureRequest
import skillbill.engine.featuretask.validation.model.ValidationGateAgentRepairLauncher
import skillbill.engine.featuretask.validation.model.ValidationGateAgentRepairResult
import skillbill.engine.featuretask.validation.model.ValidationGateAgentTriageLauncher
import skillbill.engine.featuretask.validation.model.ValidationGateCycleRequest
import skillbill.engine.featuretask.validation.model.ValidationGateCycleResult
import skillbill.engine.featuretask.validation.model.ValidationGateCycleTerminalOutcome
import skillbill.engine.featuretask.validation.model.ValidationGateTriageResult
import skillbill.engine.goalrunner.status.completed
import skillbill.ports.taskruntime.FeatureTaskRuntimePhaseOutputValidator
import skillbill.ports.taskruntime.validateBuildReceipt
import skillbill.workflow.model.ValidationDepth
import skillbill.workflow.taskruntime.artifact.envelopeWireMap
import skillbill.workflow.taskruntime.artifact.toWorkflowArtifactMap
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeWorkflowArtifactMap
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.phase.AcceptedFeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeFailureDisposition
import skillbill.workflow.taskruntime.model.phase.requireAcceptedOutput
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition

internal class FeatureTaskRuntimeRunLoopValidationSettlement(
  private val request: FeatureTaskRuntimeRunRequest,
  private val state: FeatureTaskRuntimeRunState,
  private val recorder: FeatureTaskRuntimePhaseRecorder,
  private val goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
  private val outputValidator: FeatureTaskRuntimePhaseOutputValidator,
  private val phaseGates: FeatureTaskRuntimePhaseGates,
  private val session: FeatureTaskRuntimeRunLoopSession,
) {
  private fun captureReadinessFragmentAfterValidate(run: PhaseRun) {
    val changedPaths =
      FeatureTaskRuntimeRunLoopValidationScope.validationChangedPaths(
        phaseGates,
        recorder,
        goalContinuationRecorder,
        session,
        run,
      ).orEmpty()
    phaseGates.readinessGateCoordinator.capturePostValidateFragment(
      ReadinessPostValidateCaptureRequest(
        workflowId = request.workflowId,
        repoRoot = request.repoRoot,
        baseBranch = recorder.loadResolvedBranch(request.workflowId)?.baseBranch ?: "main",
        changedPaths = changedPaths,
        gitOperations = phaseGates.gitOperations,
      ),
    )
  }

  internal fun settle(
    run: PhaseRun,
    iteration: Int,
    outputText: String,
    observability: FeatureTaskRuntimeRunObservability,
  ): PhaseOutcome {
    val acceptedOutput =
      validate(run, outputText).getOrElse { error ->
        return FeatureTaskRuntimeRunLoopPhaseBlocking.blockAndPersistInPhase(
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
    return finish(run, iteration, outputText, acceptedOutput, observability)
  }

  private fun validate(
    run: PhaseRun,
    outputText: String,
  ): Result<AcceptedFeatureTaskRuntimePhaseOutput> =
    runCatching {
      val accepted =
        outputValidator.validatePhaseOutput(
          outputText,
          sourceLabel = run.phaseId,
        ).requireAcceptedOutput(run.phaseId)
      requirePassedValidationResult(run, accepted.normalizedOutput.envelopeWireMap())
      accepted
    }

  private fun finish(
    run: PhaseRun,
    iteration: Int,
    outputText: String,
    acceptedOutput: AcceptedFeatureTaskRuntimePhaseOutput,
    observability: FeatureTaskRuntimeRunObservability,
  ): PhaseOutcome {
    val normalizedOutput = acceptedOutput.normalizedOutput
    if (!persistCompleted(run, iteration, outputText, acceptedOutput)) {
      return FeatureTaskRuntimeRunLoopPhaseBlocking.blockInPhase(
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
    captureReadinessFragmentAfterValidate(run)
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

  private fun persistCompleted(
    run: PhaseRun,
    iteration: Int,
    outputText: String,
    acceptedOutput: AcceptedFeatureTaskRuntimePhaseOutput,
  ): Boolean =
    recorder.recordCompletedPhase(
      FeatureTaskRuntimeRunLoopPhaseBlocking.phaseStateRequest(
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
}

internal class FeatureTaskRuntimeRunLoopValidationGateCycleSettlement(
  private val context: PhaseAttemptAccumulatorContext,
  private val request: FeatureTaskRuntimeRunRequest,
  private val recorder: FeatureTaskRuntimePhaseRecorder,
  private val goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
  private val outputValidator: FeatureTaskRuntimePhaseOutputValidator,
  private val phaseGates: FeatureTaskRuntimePhaseGates,
  private val session: FeatureTaskRuntimeRunLoopSession,
) {
  internal fun settle(cycle: ValidationGateCycleResult): PhaseOutcome {
    return when (cycle) {
      ValidationGateCycleResult.AbsentFallback -> settleAbsentFallback()
      is ValidationGateCycleResult.Terminal -> settleTerminal(cycle.outcome)
    }
  }

  private fun settleAbsentFallback(): PhaseOutcome {
    startPhase()
    return FeatureTaskRuntimeRunLoopPhaseBlocking.blockInPhase(
      request,
      context.attempt.state,
      recorder,
      context.attempt.observability,
      PhaseBlockRequest(
        run = context.attempt.run,
        attemptCount = context.attempt.iteration,
        reason = FeatureTaskRuntimeValidationGateCoordinator.ABSENT_VALIDATION_GATE_REASON,
        observability = context.attempt.observability,
        failureDisposition = FeatureTaskRuntimeFailureDisposition.NEEDS_USER_ACTION,
      ),
    )
  }

  private fun settleTerminal(outcome: ValidationGateCycleTerminalOutcome): PhaseOutcome {
    return when (outcome) {
      is ValidationGateCycleTerminalOutcome.Paused -> PhaseOutcome.paused(outcome.reason)
      is ValidationGateCycleTerminalOutcome.Completed ->
        FeatureTaskRuntimeRunLoopValidationSettlement(
          request = request,
          state = context.attempt.state,
          recorder = recorder,
          goalContinuationRecorder = goalContinuationRecorder,
          outputValidator = outputValidator,
          phaseGates = phaseGates,
          session = session,
        ).settle(
          run = context.attempt.run,
          iteration = outcome.output.iteration,
          outputText = outcome.output.payload,
          observability = context.attempt.observability,
        )
      is ValidationGateCycleTerminalOutcome.Blocked ->
        FeatureTaskRuntimeRunLoopPhaseBlocking.blockInPhase(
          request,
          context.attempt.state,
          recorder,
          context.attempt.observability,
          PhaseBlockRequest(
            run = context.attempt.run,
            attemptCount =
              recorder.loadPhaseRecords(request.workflowId)
                ?.get(context.attempt.run.phaseId)?.attemptCount ?: context.attempt.iteration,
            reason = outcome.reason,
            observability = context.attempt.observability,
            failureDisposition =
              outcome.failureDisposition
                ?: FeatureTaskRuntimeFailureDisposition.NEEDS_USER_ACTION,
          ),
        )
    }
  }

  private fun startPhase() {
    context.attempt.observability.started(
      context.attempt.run.phaseId,
      context.attempt.run.resolvedAgent.resolvedAgentId,
      context.attempt.iteration,
      context.attempt.run.modelDirective,
      FeatureTaskRuntimePhaseStartReentry.FIRST_VISIT,
    )
  }
}

internal class FeatureTaskRuntimeRunLoopBuildSettlement(
  private val request: FeatureTaskRuntimeRunRequest,
  private val state: FeatureTaskRuntimeRunState,
  private val recorder: FeatureTaskRuntimePhaseRecorder,
  private val goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
  private val outputValidator: FeatureTaskRuntimePhaseOutputValidator,
  private val phaseGates: FeatureTaskRuntimePhaseGates,
) {
  internal fun settle(
    run: PhaseRun,
    iteration: Int,
    outputText: String,
    observability: FeatureTaskRuntimeRunObservability,
  ): PhaseOutcome {
    val acceptedOutput =
      accept(run, outputText).getOrElse { error ->
        return FeatureTaskRuntimeRunLoopPhaseBlocking.blockAndPersistInPhase(
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
    val normalizedOutput = acceptedOutput.normalizedOutput
    if (!persistCompleted(run, iteration, outputText, acceptedOutput)) {
      return FeatureTaskRuntimeRunLoopPhaseBlocking.blockInPhase(
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

  private fun persistCompleted(
    run: PhaseRun,
    iteration: Int,
    outputText: String,
    acceptedOutput: AcceptedFeatureTaskRuntimePhaseOutput,
  ): Boolean =
    recorder.recordCompletedPhase(
      FeatureTaskRuntimeRunLoopPhaseBlocking.phaseStateRequest(
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

  private fun accept(
    run: PhaseRun,
    outputText: String,
  ): Result<AcceptedFeatureTaskRuntimePhaseOutput> =
    runCatching {
      val accepted =
        outputValidator.validatePhaseOutput(outputText, sourceLabel = run.phaseId)
          .requireAcceptedOutput(run.phaseId)
      val buildReceipt =
        JsonCodec.anyToStringAnyMap(
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
}

internal class FeatureTaskRuntimeRunLoopBuildGateRunningPhase(
  private val request: FeatureTaskRuntimeRunRequest,
  private val state: FeatureTaskRuntimeRunState,
  private val recorder: FeatureTaskRuntimePhaseRecorder,
  private val goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
  private val observability: FeatureTaskRuntimeRunObservability,
) {
  internal fun persist(
    run: PhaseRun,
    iteration: Int,
  ): PhaseOutcome? {
    val runningPhaseState =
      FeatureTaskRuntimeRunLoopPhaseBlocking.phaseStateRequest(
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
      return FeatureTaskRuntimeRunLoopPhaseBlocking.blockInPhase(
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
}

internal class FeatureTaskRuntimeRunLoopBuildGateCycleSettlement(
  private val request: FeatureTaskRuntimeRunRequest,
  private val state: FeatureTaskRuntimeRunState,
  private val recorder: FeatureTaskRuntimePhaseRecorder,
  private val goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
  private val outputValidator: FeatureTaskRuntimePhaseOutputValidator,
  private val phaseGates: FeatureTaskRuntimePhaseGates,
) {
  internal fun settle(
    run: PhaseRun,
    iteration: Int,
    observability: FeatureTaskRuntimeRunObservability,
    checkpoint: String,
    cycle: ValidationGateCycleResult,
  ): PhaseOutcome {
    return when (cycle) {
      ValidationGateCycleResult.AbsentFallback ->
        runtimeOwnedBuild(
          run,
          iteration,
          observability,
          FeatureTaskRuntimeBuildGateCoordinator.runtimeOwnedBuildOutput(
            repositoryCheckpoint = checkpoint,
            measurements = emptyList(),
          ).payload,
        )
      is ValidationGateCycleResult.Terminal -> {
        when (val terminal = cycle.outcome) {
          is ValidationGateCycleTerminalOutcome.Paused -> PhaseOutcome.paused(terminal.reason)
          is ValidationGateCycleTerminalOutcome.Completed ->
            runtimeOwnedBuild(run, iteration, observability, terminal.output.payload)
          is ValidationGateCycleTerminalOutcome.Blocked ->
            FeatureTaskRuntimeRunLoopPhaseBlocking.blockInPhase(
              request,
              state,
              recorder,
              observability,
              PhaseBlockRequest(
                run = run,
                attemptCount = iteration,
                reason = terminal.reason,
                observability = observability,
                failureDisposition =
                  terminal.failureDisposition
                    ?: FeatureTaskRuntimeFailureDisposition.NEEDS_USER_ACTION,
              ),
            )
        }
      }
    }
  }

  private fun runtimeOwnedBuild(
    run: PhaseRun,
    iteration: Int,
    observability: FeatureTaskRuntimeRunObservability,
    outputText: String,
  ): PhaseOutcome =
    FeatureTaskRuntimeRunLoopBuildSettlement(
      request = request,
      state = state,
      recorder = recorder,
      goalContinuationRecorder = goalContinuationRecorder,
      outputValidator = outputValidator,
      phaseGates = phaseGates,
    ).settle(run, iteration, outputText, observability)
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
  val produced =
    looseOutputEnvelope(outputText)
      ?.let { JsonCodec.anyToStringAnyMap(it[SharedPayloadKeys.PRODUCED_OUTPUTS]) }
      ?: return emptyMap()
  return buildMap {
    produced[SharedPayloadKeys.VALUE]?.let { put(SharedPayloadKeys.VALUE, it) }
    produced["validation_repair_plan"]?.let { put("validation_repair_plan", it) }
  }
}

internal fun gateRepairSegmentOutput(
  run: PhaseRun,
  iteration: Int,
): FeatureTaskRuntimePhaseOutput =
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

object FeatureTaskRuntimeRunLoopValidationGate {
  internal fun FeatureTaskRuntimeRunLoopContext.runDeclaredBuildGateCycle(
    run: PhaseRun,
    call: PhaseStepCall,
  ): PhaseOutcome {
    val checkpoint =
      resolveValidationGateCheckpoint(phaseGates, run)
        ?: return PhaseOutcome.blocked(
          "Build gate cycle could not resolve a repository checkpoint fingerprint.",
        )
    val iteration = state.nextIteration(run.phaseId)
    FeatureTaskRuntimeRunLoopBuildGateRunningPhase(
      request,
      state,
      recorder,
      goalContinuationRecorder,
      observability,
    ).persist(run, iteration)?.let { return it }
    val context = phaseAttemptAccumulatorContext(run, state, iteration, observability)
    val cycle =
      phaseGates.buildGateCoordinator.execute(
        cycle =
          buildGateCycleRequest(
            ValidationGateCycleRequestArgs(
              context,
              checkpoint,
            ),
            call,
          ),
        onGateRunCount = { observability.validationGateProgress() },
      )
    return FeatureTaskRuntimeRunLoopBuildGateCycleSettlement(
      request = request,
      state = state,
      recorder = recorder,
      goalContinuationRecorder = goalContinuationRecorder,
      outputValidator = outputValidator,
      phaseGates = phaseGates,
    ).settle(run, iteration, observability, checkpoint, cycle)
  }

  internal fun FeatureTaskRuntimeRunLoopContext.launchValidationGateTriage(
    args: ValidationGateTriageArgs,
    call: PhaseStepCall,
  ): ValidationGateTriageResult {
    val run = args.context.attempt.run
    val state = args.context.attempt.state
    val iteration = args.context.attempt.iteration
    val observability = args.context.attempt.observability
    val findings = args.findings
    val triageRun = run.copy(validationGateFindings = findings, validationGateTriage = true)
    val attempt =
      PhaseAttemptOnce.attemptOnce(
        this@launchValidationGateTriage,
        recordRejectionAttemptArgs(
          PhaseAttemptContext(triageRun, state, iteration, observability),
          call,
        ),
      )
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
    call: PhaseStepCall,
  ): ValidationGateCycleRequest =
    validationGateCycleRequest(args, call).copy(validationDepth = ValidationDepth.DEFAULT)

  internal fun extractValidationGateTriagePlan(output: FeatureTaskRuntimePhaseOutput): ValidationGateTriageResult {
    val envelope =
      FeatureTaskRuntimeRunLoopLaunch.outputEnvelopeOf(output)
        ?: return ValidationGateTriageResult.Empty
    val produced =
      JsonCodec.anyToStringAnyMap(envelope[SharedPayloadKeys.PRODUCED_OUTPUTS])
        ?: return ValidationGateTriageResult.Empty
    FeatureTaskRuntimeRunLoopValidationGate.planFromProducedValue(produced[SharedPayloadKeys.VALUE])?.let { return it }
    val directPlan =
      FeatureTaskRuntimeRunLoopValidationGate
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
    val inner =
      JsonCodec.parseObjectOrNull(valueText)
        ?.let(JsonCodec::jsonElementToValue)
        ?.let(JsonCodec::anyToStringAnyMap)
    val planFromValue = inner?.let { extractTriagePlanProse(it["validation_repair_plan"]) }
    if (!planFromValue.isNullOrBlank()) {
      return ValidationGateTriageResult.Captured(planFromValue)
    }
    return if (inner == null) ValidationGateTriageResult.Captured(valueText) else null
  }

  internal fun extractTriagePlanProse(raw: Any?): String? =
    when (raw) {
      is String -> raw.takeIf { it.isNotBlank() }
      null -> null
      else ->
        JsonCodec.mapToJsonString(
          JsonCodec.anyToStringAnyMap(raw) ?: mapOf("validation_repair_plan" to raw),
        ).takeIf { it.isNotBlank() && it != "{}" && it != "[]" }
    }

  internal fun FeatureTaskRuntimeRunLoopContext.launchValidationGateRepair(
    args: ValidationGateRepairArgs,
    call: PhaseStepCall,
  ): ValidationGateAgentRepairResult {
    val run = args.context.attempt.run
    if (run.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE) {
      val settled =
        with(PhaseAttemptLoop) { runPhaseAttempts(run.copy(validationGateFindings = args.findings), call) }
      val completed = settled.completedOutput
      val paused = settled.pausedReason
      return when {
        completed != null -> ValidationGateAgentRepairResult.Completed(completed)
        paused != null -> ValidationGateAgentRepairResult.Paused(paused)
        else ->
          ValidationGateAgentRepairResult.Blocked(
            settled.blockedReason ?: "Validation phase did not complete.",
            failureDisposition =
              recorder.loadPhaseRecords(run.request.workflowId)
                ?.get(run.phaseId)
                ?.failureDisposition,
          )
      }
    }
    val state = args.context.attempt.state
    val iteration = args.context.attempt.iteration
    val observability = args.context.attempt.observability
    val repairTurn = args.repairTurn
    val repairRun =
      run.copy(
        validationGateFindings = args.findings.takeIf { it.findings.isNotEmpty() },
        validationGateRepairTurn = repairTurn,
        validationGateTriagePlan = args.triagePlan,
        validationGateRepair = true,
      )
    val attempt =
      PhaseAttemptOnce.attemptOnce(
        this@launchValidationGateRepair,
        recordRejectionAttemptArgs(
          PhaseAttemptContext(repairRun, state, iteration, observability),
          call,
        ),
      )
    val settled = attempt.settledOutcome
    val completed = settled?.completedOutput
    return when {
      completed != null -> ValidationGateAgentRepairResult.Completed(completed)
      settled != null ->
        ValidationGateAgentRepairResult.Blocked(
          settled.blockedReason
            ?: settled.pausedReason
            ?: "Validation repair attempt persistence.session.blocked.",
          failureDisposition =
            recorder.loadPhaseRecords(run.request.workflowId)
              ?.get(run.phaseId)
              ?.failureDisposition,
        )
      else -> ValidationGateAgentRepairResult.Completed(gateRepairSegmentOutput(run, iteration))
    }
  }

  internal fun FeatureTaskRuntimeRunLoopContext.runDeclaredValidationGateCycle(
    run: PhaseRun,
    call: PhaseStepCall,
  ): PhaseOutcome {
    val checkpoint = FeatureTaskRuntimeRunLoopValidationGate.resolveValidationGateCheckpoint(phaseGates, run).orEmpty()
    val iteration = state.nextIteration(run.phaseId)
    val context = phaseAttemptAccumulatorContext(run, state, iteration, observability)
    val cycle =
      phaseGates.validationGateCoordinator.execute(
        cycle =
          validationGateCycleRequest(
            ValidationGateCycleRequestArgs(
              context,
              checkpoint,
            ),
            call,
          ),
      )
    return FeatureTaskRuntimeRunLoopValidationGateCycleSettlement(
      context = context,
      request = request,
      recorder = recorder,
      goalContinuationRecorder = goalContinuationRecorder,
      outputValidator = outputValidator,
      phaseGates = phaseGates,
      session = session,
    ).settle(cycle)
  }

  internal fun FeatureTaskRuntimeRunLoopContext.validationGateCycleRequest(
    args: ValidationGateCycleRequestArgs,
    call: PhaseStepCall,
  ): ValidationGateCycleRequest {
    val run = args.context.attempt.run
    val validationDepth = run.request.goalContinuation?.validationDepth ?: ValidationDepth.DEFAULT
    return ValidationGateCycleRequest(
      repoRoot = run.request.repoRoot,
      request = run.request,
      validationDepth = validationDepth,
      changedPaths =
        FeatureTaskRuntimeRunLoopValidationScope.validationChangedPaths(
          phaseGates,
          recorder,
          goalContinuationRecorder,
          session,
          run,
        ).orEmpty(),
      repositoryCheckpoint = args.checkpoint,
      agentTriageLauncher =
        ValidationGateAgentTriageLauncher { findings ->
          launchValidationGateTriage(
            ValidationGateTriageArgs(
              args.context,
              findings,
            ),
            call,
          )
        },
      agentRepairLauncher =
        ValidationGateAgentRepairLauncher { findings, repairIteration, triagePlan ->
          launchValidationGateRepair(
            ValidationGateRepairArgs(
              context = args.context,
              findings = findings,
              repairTurn = repairIteration,
              triagePlan = triagePlan,
            ),
            call,
          )
        },
    )
  }

  internal fun resolveValidationGateCheckpoint(
    phaseGates: FeatureTaskRuntimePhaseGates,
    run: PhaseRun,
  ): String? = phaseGates.gitOperations.repositoryFingerprint(run.request.repoRoot).value.takeIf(String::isNotBlank)
}
