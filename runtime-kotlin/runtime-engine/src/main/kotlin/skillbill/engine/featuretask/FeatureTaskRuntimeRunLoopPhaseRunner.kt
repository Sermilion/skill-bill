package skillbill.engine.featuretask
import skillbill.engine.featuretask.model.FeatureTaskRuntimePhaseStateRequest
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunRequest
import skillbill.engine.featuretask.model.GoalSubtaskReviewInputBlocked
import skillbill.engine.featuretask.model.GoalSubtaskReviewInputPreparation
import skillbill.engine.featuretask.model.GoalSubtaskReviewInputReady
import skillbill.engine.featuretask.model.GoalSubtaskReviewPassCarryForward
import skillbill.engine.featuretask.model.GoalSubtaskReviewPassInFlight
import skillbill.engine.featuretask.model.GoalSubtaskReviewPassReservation
import skillbill.engine.featuretask.model.GoalSubtaskReviewPassReserved
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.ports.workflow.gitops.buildGoalSubtaskReviewInput
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseOutputValidator
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeFailureDisposition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseRecord
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeResolvedBranch
import skillbill.workflow.taskruntime.model.requireAcceptedOutput

object FeatureTaskRuntimeRunLoopPhaseRunner {

  internal fun runDeclaredReviewDriverCycle(
    context: FeatureTaskRuntimeRunLoopContext,
    run: PhaseRun,
    state: FeatureTaskRuntimeRunState,
    observability: FeatureTaskRuntimeRunObservability,
  ): PhaseOutcome {
    val reviewContext = context.copy(state = state, observability = observability)
    val prepared = FeatureTaskRuntimeRunLoopReview.prepareRuntimeOwnedReview(
      request = context.request,
      recorder = context.recorder,
      goalContinuationRecorder = context.goalContinuationRecorder,
      phaseGates = context.phaseGates,
      clock = context.clock,
      state = state,
      run = run,
    )
    return when (prepared) {
      is RuntimeOwnedReviewBlocked -> prepared.outcome
      is RuntimeOwnedReviewReady -> {
        with(FeatureTaskRuntimeRunLoopLaunch) {
          context.prepareLaunchForCapture(prepared.run, state, null)
        }
        with(FeatureTaskRuntimeRunLoopReview) {
          reviewContext.executePreparedReviewDriver(prepared)
        }
      }
    }
  }

  internal fun preLaunchBlock(
    request: FeatureTaskRuntimeRunRequest,
    recorder: FeatureTaskRuntimePhaseRecorder,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    phaseGates: FeatureTaskRuntimePhaseGates,
    diagnostics: RuntimeDiagnostics,
    session: FeatureTaskRuntimeRunLoopSession,
    run: PhaseRun,
    state: FeatureTaskRuntimeRunState,
    observability: FeatureTaskRuntimeRunObservability,
  ): PhaseOutcome? {
    FeatureTaskRuntimeCommitPushUpstreamHeadFallback.reconcile(
      request,
      run,
      state,
      phaseGates,
      diagnostics,
    )
    FeatureTaskRuntimeCommitPushUpstreamHeadFallback.clearUpstreamPersistedBlockIfRecovered(run, state)
    val persisted = state.persistedBlockedReason(run.phaseId)?.let { persistedReason ->
      val nextIteration = state.nextIteration(run.phaseId)
      val durable = state.recordFor(run.phaseId)
      if (
        shouldRelaunchPersistedBlock(
          session = session,
          state = state,
          phaseId = run.phaseId,
          durable = durable,
          persistedReason = persistedReason,
        )
      ) {
        return@let null
      }
      val reason = persistedReason.ifBlank {
        "Phase '${run.phaseId}' is durably blocked from a prior run; " +
          "the runtime re-blocks rather than relaunching."
      }
      PreLaunchBlock(nextIteration, reason, durable)
    }
    val missing = persisted ?: missingRequiredUpstream(run, state)?.let { missingIds ->
      PreLaunchBlock(
        1,
        "Phase '${run.phaseId}' requires upstream output(s) ${missingIds.joinToString()} that are not " +
          "present; the runtime blocks rather than launching the phase blind.",
      )
    }
    return missing?.let {
      persistPreLaunchBlock(
        request,
        state,
        recorder,
        goalContinuationRecorder,
        run,
        observability,
        it,
      )
    }
  }

  private fun persistPreLaunchBlock(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    run: PhaseRun,
    observability: FeatureTaskRuntimeRunObservability,
    preLaunch: PreLaunchBlock,
  ): PhaseOutcome {
    val durable = preLaunch.durableRecord
    return FeatureTaskRuntimeRunLoopPhaseAttempts.blockAndPersist(
      request,
      state,
      recorder,
      goalContinuationRecorder,
      BlockAndPersistArgs(
        run = run,
        attemptCount = preLaunch.attemptCount,
        reason = preLaunch.reason,
        observability = observability,
        loopId = durable?.loopId,
        edgeIteration = durable?.edgeIteration,
        failureDisposition = durable?.failureDisposition
          ?: FeatureTaskRuntimeFailureDisposition.NEEDS_USER_ACTION,
        payload = BlockAndPersistPayload(
          fileManifest = durable?.let {
            FeatureTaskRuntimePhaseFileManifest(it.fileManifestBefore, it.fileManifestAfter)
          },
          outputArtifact = durable?.outputArtifact,
          rejectedOutput = durable?.rejectedOutput,
        ),
      ),
    )
  }

  internal fun missingRequiredUpstream(run: PhaseRun, state: FeatureTaskRuntimeRunState): List<String>? =
    missingUpstream(run.declaration, state.outputs())?.takeIf(List<String>::isNotEmpty)

  fun isRetryableGoalReviewPreparation(phaseId: String, reason: String): Boolean {
    if (phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW) return false
    val legacyDatabaseContention =
      reason.startsWith("Goal-subtask review state or durable raw evidence is malformed:") &&
        "[SQLITE_BUSY]" in reason
    return legacyDatabaseContention ||
      "[SQLITE_BUSY]" in reason && (
        reason.startsWith("Goal-subtask review reservation failed") ||
          reason.startsWith("Goal-subtask review input persistence failed")
        )
  }

  fun isRemovedGoalReviewSchemaGateBlock(phaseId: String, reason: String): Boolean =
    phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW &&
      reason.startsWith("Goal-subtask review output failed schema validation after its reserved pass")

  fun isRemovedImplementationContinuationBudgetBlock(phaseId: String, reason: String): Boolean =
    phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT &&
      "exhausted the bounded implementation-continuation budget" in reason

  fun isReenterableLaunchSeamRecordRejection(phaseId: String, reason: String): Boolean =
    reason.contains(LEGACY_PLANNING_PROJECTION_LAUNCH_SEAM_REJECTION) &&
      FeatureTaskRuntimePhaseWorkflowDefinition.REGENERATION_PRODUCER_BY_CONSUMER.containsKey(phaseId)

  fun isReenterableRecordRejection(state: FeatureTaskRuntimeRunState, phaseId: String, reason: String): Boolean =
    isReenterableLaunchSeamRecordRejection(phaseId, reason) ||
      state.legacyLaunchSeamRejectionConsumedBudget(phaseId, reason)

  internal fun shouldRelaunchPersistedBlock(
    session: FeatureTaskRuntimeRunLoopSession,
    state: FeatureTaskRuntimeRunState,
    phaseId: String,
    durable: FeatureTaskRuntimePhaseRecord?,
    persistedReason: String,
  ): Boolean {
    val retryReviewPreparation = isRetryableGoalReviewPreparation(phaseId, persistedReason) ||
      state.legacyReviewPreparationRetryConsumedBudget(phaseId, persistedReason)
    val reenterableRecordRejection = isReenterableRecordRejection(state, phaseId, persistedReason)
    val removedContinuationBudget = isRemovedImplementationContinuationBudgetBlock(phaseId, persistedReason)
    val restartsBudget = listOf(
      retryReviewPreparation,
      reenterableRecordRejection,
      removedContinuationBudget,
      FeatureTaskRuntimeRunLoopPhaseAttempts.operatorReopenedPhase(session, phaseId),
    ).any { it }
    if (restartsBudget) {
      state.restartAttemptBudget(phaseId)
    }
    return shouldRetryPersistedBlock(
      session,
      ShouldRetryPersistedBlockArgs(
        phaseId = phaseId,
        durable = durable,
        retryReviewPreparation = retryReviewPreparation,
        reenterableRecordRejection = reenterableRecordRejection,
        persistedReason = persistedReason,
      ),
    )
  }

  internal fun shouldRetryPersistedBlock(
    session: FeatureTaskRuntimeRunLoopSession,
    args: ShouldRetryPersistedBlockArgs,
  ): Boolean {
    val phaseId = args.phaseId
    val durable = args.durable
    val retryReviewPreparation = args.retryReviewPreparation
    val reenterableRecordRejection = args.reenterableRecordRejection
    val persistedReason = args.persistedReason
    val disposition = durable?.failureDisposition
    return when {
      FeatureTaskRuntimeRunLoopPhaseAttempts.operatorReopenedPhase(session, phaseId) -> true
      retryReviewPreparation -> true
      reenterableRecordRejection -> true
      isRemovedGoalReviewSchemaGateBlock(phaseId, persistedReason) -> true
      isRemovedImplementationContinuationBudgetBlock(phaseId, persistedReason) -> true
      disposition != null -> disposition.retryOnResume
      else -> FeatureTaskRuntimePhaseWorkflowDefinition.retriesOnInvalidOutput(phaseId)
    }
  }

  internal fun prepareGoalReviewRun(
    request: FeatureTaskRuntimeRunRequest,
    recorder: FeatureTaskRuntimePhaseRecorder,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    phaseGates: FeatureTaskRuntimePhaseGates,
    outputValidator: FeatureTaskRuntimePhaseOutputValidator,
    session: FeatureTaskRuntimeRunLoopSession,
    state: FeatureTaskRuntimeRunState,
    run: PhaseRun,
    observability: FeatureTaskRuntimeRunObservability,
  ): GoalReviewRunPreparation = when {
    run.phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW -> GoalReviewRunReady(run)
    FeatureTaskRuntimeRunLoopOutputPersistence.isGoalReviewRun(run) ->
      reserveGoalReviewRun(request, recorder, goalContinuationRecorder, phaseGates, state, run, observability)
    else -> prepareStandaloneReviewRun(request, recorder, phaseGates, state, run, observability)
  }

  internal fun prepareStandaloneReviewRun(
    request: FeatureTaskRuntimeRunRequest,
    recorder: FeatureTaskRuntimePhaseRecorder,
    phaseGates: FeatureTaskRuntimePhaseGates,
    state: FeatureTaskRuntimeRunState,
    run: PhaseRun,
    observability: FeatureTaskRuntimeRunObservability,
  ): GoalReviewRunPreparation {
    val resolved = recorder.loadResolvedBranch(run.request.workflowId)
      ?: return blockedGoalReviewRun(
        request,
        recorder,
        goalContinuationRecorder = null,
        run,
        state,
        observability,
        "Standalone review is missing its durable resolved branch.",
      )
    val reviewBaseSha = resolved.reviewBaseSha
      ?: return blockedGoalReviewRun(
        request,
        recorder,
        goalContinuationRecorder = null,
        run,
        state,
        observability,
        "Standalone review is missing the immutable review base captured before implementation.",
      )
    val result = phaseGates.gitOperations.buildGoalSubtaskReviewInput(
      run.request.repoRoot,
      FeatureTaskRuntimeScopedReviewBaseline.of(
        phaseGates.gitOperations,
        run.request.repoRoot,
        resolved,
        reviewBaseSha,
      ),
      resolved.branch,
    )
    val input = result.input
      ?: return blockedGoalReviewRun(
        request,
        recorder,
        goalContinuationRecorder = null,
        run,
        state,
        observability,
        result.error.ifBlank { "Standalone review input failed." },
      )
    return GoalReviewRunReady(run.copy(goalReviewInput = input))
  }

  fun scopedReviewUntrackedExclusions(
    phaseGates: FeatureTaskRuntimePhaseGates,
    request: FeatureTaskRuntimeRunRequest,
    resolved: FeatureTaskRuntimeResolvedBranch,
  ): List<String> = FeatureTaskRuntimeScopedReviewBaseline.untrackedExclusions(
    phaseGates.gitOperations,
    request.repoRoot,
    resolved,
  )

  internal fun reserveGoalReviewRun(
    request: FeatureTaskRuntimeRunRequest,
    recorder: FeatureTaskRuntimePhaseRecorder,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    phaseGates: FeatureTaskRuntimePhaseGates,
    state: FeatureTaskRuntimeRunState,
    run: PhaseRun,
    observability: FeatureTaskRuntimeRunObservability,
  ): GoalReviewRunPreparation = runCatching {
    goalContinuationRecorder.reserveGoalReviewPass(run.request.workflowId)
  }.fold(
    onSuccess = { reservation ->
      when (reservation) {
        GoalSubtaskReviewPassReservation.MissingState -> blockedGoalReviewRun(
          request,
          recorder,
          goalContinuationRecorder,
          run,
          state,
          observability,
          "Goal-subtask review state is missing; review_base_sha must be captured before implementation " +
            "and cannot be substituted.",
        )
        is GoalSubtaskReviewPassCarryForward -> GoalReviewRunPreparation.CarryForward
        is GoalSubtaskReviewPassInFlight,
        is GoalSubtaskReviewPassReserved,
        -> buildGoalReviewRun(request, recorder, goalContinuationRecorder, phaseGates, state, run, observability)
      }
    },
    onFailure = { error ->
      blockedGoalReviewRun(
        request,
        recorder,
        goalContinuationRecorder,
        run,
        state,
        observability,
        goalReviewPreparationFailure("reservation", error),
        goalReviewPreparationDisposition(error),
      )
    },
  )

  internal fun buildGoalReviewRun(
    request: FeatureTaskRuntimeRunRequest,
    recorder: FeatureTaskRuntimePhaseRecorder,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    phaseGates: FeatureTaskRuntimePhaseGates,
    state: FeatureTaskRuntimeRunState,
    run: PhaseRun,
    observability: FeatureTaskRuntimeRunObservability,
  ): GoalReviewRunPreparation = runCatching {
    val resolved = recorder.loadResolvedBranch(run.request.workflowId)
    goalContinuationRecorder.buildGoalReviewInput(
      workflowId = run.request.workflowId,
      gitOperations = phaseGates.gitOperations,
      repoRoot = run.request.repoRoot,
      scope = FeatureTaskRuntimeGoalContinuationRecorder.GoalReviewInputScope(
        scopedUntrackedExclusions = resolved?.let {
          scopedReviewUntrackedExclusions(phaseGates, request, it)
        },
        ownedPathspec = resolved?.workflowOwnedPaths.orEmpty(),
      ),
    )
  }.fold(
    onSuccess = { prepared ->
      when (prepared) {
        GoalSubtaskReviewInputPreparation.MissingState -> {
          blockedGoalReviewRun(
            request,
            recorder,
            goalContinuationRecorder,
            run,
            state,
            observability,
            "Goal-subtask review state disappeared before review launch.",
          )
        }
        is GoalSubtaskReviewInputBlocked -> {
          blockedGoalReviewRun(request, recorder, goalContinuationRecorder, run, state, observability, prepared.reason)
        }
        is GoalSubtaskReviewInputReady ->
          GoalReviewRunReady(run.copy(goalReviewInput = prepared.input))
      }
    },
    onFailure = { error ->
      blockedGoalReviewRun(
        request,
        recorder,
        goalContinuationRecorder,
        run,
        state,
        observability,
        goalReviewPreparationFailure("input persistence", error),
        goalReviewPreparationDisposition(error),
      )
    },
  )

  fun goalReviewPreparationFailure(stage: String, error: Throwable): String {
    val location = error.stackTrace.firstOrNull { frame -> frame.className.startsWith("skillbill.") }
      ?.let { frame -> " at ${frame.className}.${frame.methodName}:${frame.lineNumber}" }
      .orEmpty()
    return "Goal-subtask review $stage failed$location: ${error.message.orEmpty()}"
  }

  fun goalReviewPreparationDisposition(error: Throwable): FeatureTaskRuntimeFailureDisposition =
    if ("[SQLITE_BUSY]" in error.message.orEmpty()) {
      FeatureTaskRuntimeFailureDisposition.RETRYABLE
    } else {
      FeatureTaskRuntimeFailureDisposition.NEEDS_USER_ACTION
    }

  internal fun blockedGoalReviewRun(
    request: FeatureTaskRuntimeRunRequest,
    recorder: FeatureTaskRuntimePhaseRecorder,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder?,
    run: PhaseRun,
    state: FeatureTaskRuntimeRunState,
    observability: FeatureTaskRuntimeRunObservability,
    reason: String,
    failureDisposition: FeatureTaskRuntimeFailureDisposition = FeatureTaskRuntimeFailureDisposition.NEEDS_USER_ACTION,
  ): GoalReviewRunPreparation {
    FeatureTaskRuntimeRunLoopPhaseAttempts.blockAndPersist(
      request,
      state,
      recorder,
      goalContinuationRecorder,
      BlockAndPersistArgs(
        run = run,
        attemptCount = 1,
        reason = reason,
        observability = observability,
        loopId = null,
        edgeIteration = null,
        failureDisposition = failureDisposition,
        payload = BlockAndPersistPayload(),
      ),
    )
    return GoalReviewRunPreparation.Blocked(reason, failureDisposition)
  }

  internal fun settleCarriedForwardGoalReview(
    request: FeatureTaskRuntimeRunRequest,
    recorder: FeatureTaskRuntimePhaseRecorder,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    outputValidator: FeatureTaskRuntimePhaseOutputValidator,
    state: FeatureTaskRuntimeRunState,
    run: PhaseRun,
    observability: FeatureTaskRuntimeRunObservability,
  ): PhaseOutcome {
    val acceptedOutput = loadCarriedForwardGoalReviewOutput(goalContinuationRecorder, outputValidator, run)
      .getOrElse { error ->
        return FeatureTaskRuntimeRunLoopPhaseAttempts.blockAndPersist(
          request,
          state,
          recorder,
          goalContinuationRecorder,
          carriedForwardMissingReviewBlock(run, state, observability, error),
        )
      }
    val normalizedOutput = acceptedOutput.normalizedOutput
    val iteration = state.nextIteration(run.phaseId)
    val phaseState = FeatureTaskRuntimeRunLoopOutputPersistence.phaseStateRequest(
      request,
      state,
      goalContinuationRecorder,
      PhaseStateRequestArgs(
        write = PhaseStateWriteArgs(
          run = run,
          iteration = iteration,
          status = STATUS_COMPLETED,
          finished = true,
          outputArtifact = normalizedOutput.canonicalJson,
        ),
        extras = PhaseStateRequestAttachments(
          normalizedOutput = normalizedOutput,
          repairEvidence = acceptedOutput.repairEvidence,
        ),
      ),
    )
    state.reserveReviewPass(phaseState.reviewPassNumber)
    carriedForwardReviewPersistenceFailure(recorder, phaseState)?.let { failure ->
      return FeatureTaskRuntimeRunLoopPhaseAttempts.blockAndPersist(
        request,
        state,
        recorder,
        goalContinuationRecorder,
        BlockAndPersistArgs(
          run = run,
          attemptCount = iteration,
          reason = failure,
          observability = observability,
          loopId = null,
          edgeIteration = null,
          failureDisposition = FeatureTaskRuntimeFailureDisposition.PROCESS_FAILURE,
          payload = BlockAndPersistPayload(
            normalizedOutput = normalizedOutput,
            outputArtifact = normalizedOutput.canonicalJson,
            repairEvidence = acceptedOutput.repairEvidence,
          ),
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

  internal fun carriedForwardReviewPersistenceFailure(
    recorder: FeatureTaskRuntimePhaseRecorder,
    phaseState: FeatureTaskRuntimePhaseStateRequest,
  ): String? {
    val prefix = "Carried-forward goal review could not atomically persist its canonical result."
    return runCatching {
      recorder.recordCompletedPhase(phaseState)
    }.fold(
      onSuccess = { persisted -> if (persisted) null else prefix },
      onFailure = { error -> "$prefix ${error.message.orEmpty()}" },
    )
  }

  internal fun loadCarriedForwardGoalReviewOutput(
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    outputValidator: FeatureTaskRuntimePhaseOutputValidator,
    run: PhaseRun,
  ) = runCatching {
    val output = goalContinuationRecorder.lastGoalReviewResult(run.request.workflowId)
      ?: throw MissingCarriedForwardGoalReviewResultException()
    outputValidator.validatePhaseOutput(output, sourceLabel = run.phaseId).requireAcceptedOutput(run.phaseId)
  }

  internal fun carriedForwardMissingReviewBlock(
    run: PhaseRun,
    state: FeatureTaskRuntimeRunState,
    observability: FeatureTaskRuntimeRunObservability,
    error: Throwable,
  ): BlockAndPersistArgs {
    val detail = if (error is MissingCarriedForwardGoalReviewResultException) {
      "missing."
    } else {
      "malformed: ${error.message.orEmpty()}"
    }
    return BlockAndPersistArgs(
      run = run,
      attemptCount = state.nextIteration(run.phaseId),
      reason = "Goal-subtask review pass budget is exhausted but its durable raw " +
        "review result is $detail",
      observability = observability,
      loopId = null,
      edgeIteration = null,
      failureDisposition = FeatureTaskRuntimeFailureDisposition.NEEDS_USER_ACTION,
      payload = BlockAndPersistPayload(),
    )
  }
}

