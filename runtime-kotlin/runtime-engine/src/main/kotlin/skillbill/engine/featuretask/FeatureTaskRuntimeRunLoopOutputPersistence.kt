package skillbill.engine.featuretask

import skillbill.application.review.RuntimeOwnedReviewMode
import skillbill.engine.featuretask.model.FeatureTaskRuntimePhaseStateRequest
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunRequest
import skillbill.engine.featuretask.model.GoalReviewPhaseCompletionRequest
import skillbill.goalrunner.subtaskreview.GoalSubtaskReviewSummaryReducer
import skillbill.goalrunner.subtaskreview.model.UnaddressedFindingLedgerScope
import skillbill.install.model.InstallAgent
import skillbill.ports.workflow.gitops.repositoryFingerprint
import skillbill.review.context.model.CodeReviewExecutionMode
import skillbill.workflow.goal.model.ValidationDepth
import skillbill.workflow.taskruntime.FeatureTaskRuntimeHandoffContract
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.envelopeWireMap
import skillbill.workflow.taskruntime.model.AcceptedFeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeCorrectiveRepairContext
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeFailureDisposition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeHandoffAssemblyRequest
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutputRepairEvidence
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRepositoryCheckpoint
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeReviewPassSequence
import skillbill.workflow.taskruntime.model.NormalizedFeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.ReviewPassResolution

object FeatureTaskRuntimeRunLoopOutputPersistence {
  internal data class ReviewOutputPersistenceContext(
    val request: FeatureTaskRuntimeRunRequest,
    val state: FeatureTaskRuntimeRunState,
    val recorder: FeatureTaskRuntimePhaseRecorder,
    val observability: FeatureTaskRuntimeRunObservability,
    val goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
  )

  internal fun persistRejectedVerificationFindings(args: PersistRejectedVerificationFindingsArgs) {
    val state = args.state
    val recorder = args.recorder
    val goalContinuationRecorder = args.goalContinuationRecorder
    val diagnostics = args.diagnostics
    val run = args.run
    val verifyOutput = args.verifyOutput
    if (!isGoalContinuationRun(run.request)) return
    val continuation = run.request.goalContinuation ?: return
    val reviewOutput = state.outputFor(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW)
      ?.normalizedOutput?.envelopeWireMap()
      ?: return
    val reviewState = goalContinuationRecorder.reviewState(run.request.workflowId)
    val passNumber = reviewState?.completedPassCount?.takeIf { it > 0 } ?: 1
    val recordedVerdicts = recorder.recordedFindingVerdicts(reviewOutput)
    val rejectedResult = GoalSubtaskReviewSummaryReducer.rejectedVerificationFindings(
      verifyOutput = verifyOutput,
      reviewOutput = reviewOutput,
      scope = UnaddressedFindingLedgerScope(
        issueKey = continuation.parentIssueKey,
        subtaskId = continuation.subtaskId,
        workflowId = run.request.workflowId,
        reviewPassNumber = passNumber,
      ),
      recordedVerdicts = recordedVerdicts,
    )
    rejectedResult.truncationRecords.forEach { record ->
      runCatching { diagnostics.warning(record) }
    }
    if (rejectedResult.findings.isEmpty()) return
    recorder.appendRejectedVerificationFindings(
      workflowId = run.request.workflowId,
      passNumber = passNumber,
      rejected = rejectedResult.findings,
    )
  }

  internal fun ReviewOutputPersistenceContext.persistStandaloneReviewCompletion(
    args: PhaseReviewPersistenceArgs,
    outputText: String,
    acceptedOutput: AcceptedFeatureTaskRuntimePhaseOutput,
  ): PhaseOutcome? {
    val run = args.run
    val iteration = args.iteration
    val observability = args.observability
    val fileManifest = args.fileManifest
    val persisted = try {
      recordStandaloneReviewCompletion(args, outputText, acceptedOutput)
    } catch (error: RuntimeOwnedFactUnavailable) {
      return FeatureTaskRuntimeRunLoopPhaseAttempts.blockInPhase(
        request,
        state,
        recorder,
        observability,
        PhaseBlockRequest(
          run = run,
          attemptCount = iteration,
          reason = "Runtime-owned review settlement could not establish its persistence fact: " +
            error.message.orEmpty(),
          observability = observability,
          failureDisposition = FeatureTaskRuntimeFailureDisposition.PROCESS_FAILURE,
        ),
      )
    }
    return if (persisted) {
      null
    } else {
      FeatureTaskRuntimeRunLoopPhaseAttempts.blockInPhase(
        request,
        state,
        recorder,
        observability,
        PhaseBlockRequest(
          run = run,
          attemptCount = iteration,
          reason = "Runtime-owned review settlement could not be persisted.",
          observability = observability,
          failureDisposition = FeatureTaskRuntimeFailureDisposition.PROCESS_FAILURE,
        ),
      )
    }
  }

  private fun ReviewOutputPersistenceContext.recordStandaloneReviewCompletion(
    args: PhaseReviewPersistenceArgs,
    outputText: String,
    acceptedOutput: AcceptedFeatureTaskRuntimePhaseOutput,
  ): Boolean = recorder.recordCompletedPhase(
    phaseStateRequest(
      request,
      state,
      goalContinuationRecorder,
      PhaseStateRequestArgs(
        write = PhaseStateWriteArgs(
          run = args.run,
          iteration = args.iteration,
          status = STATUS_COMPLETED,
          finished = true,
          outputArtifact = outputText,
        ),
        extras = PhaseStateRequestAttachments(
          fileManifest = args.fileManifest,
          normalizedOutput = acceptedOutput.normalizedOutput,
          repairEvidence = acceptedOutput.repairEvidence,
          reviewRunId = state.recordFor(args.run.phaseId)?.reviewRunId,
        ),
      ),
    ),
  )

  internal fun ReviewOutputPersistenceContext.persistGoalReviewCompletion(
    args: PhaseReviewPersistenceArgs,
    normalizedOutput: NormalizedFeatureTaskRuntimePhaseOutput,
    repairEvidence: FeatureTaskRuntimePhaseOutputRepairEvidence?,
  ): PhaseOutcome? {
    val run = args.run
    val iteration = args.iteration
    val observability = args.observability
    val fileManifest = args.fileManifest
    val completion = goalReviewPhaseCompletionRequest(args, normalizedOutput, repairEvidence)
    val completed = runCatching {
      recorder.completeGoalReviewPhase(
        completion = completion,
      )
    }.getOrElse { error ->
      return FeatureTaskRuntimeRunLoopPhaseAttempts.blockAndPersistInPhase(
        request,
        state,
        recorder,
        goalContinuationRecorder,
        phaseBlockArgs(
          run,
          iteration,
          "Goal-subtask review could not atomically persist its pass and completed phase: " +
            error.message.orEmpty(),
          observability,
          payload = BlockAndPersistPayload(fileManifest = fileManifest),
        ),
      )
    }
    return if (completed) {
      null
    } else {
      FeatureTaskRuntimeRunLoopPhaseAttempts.blockInPhase(
        request,
        state,
        recorder,
        observability,
        PhaseBlockRequest(
          run = run,
          attemptCount = iteration,
          reason = "Goal-subtask review could not atomically persist its reserved pass and completed phase.",
          observability = observability,
          payload = BlockAndPersistPayload(fileManifest = fileManifest),
        ),
      )
    }
  }

  internal fun isGoalReviewRun(run: PhaseRun): Boolean =
    run.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW && isGoalContinuationRun(run.request)

  internal fun schemaInvalidAttempt(
    operatorReason: String,
    fileManifest: FeatureTaskRuntimePhaseFileManifest,
    malformedOutput: Boolean = false,
    retryReason: String = operatorReason,
    correctiveRepairContext: FeatureTaskRuntimeCorrectiveRepairContext? = null,
  ): AttemptResult = AttemptResult.schemaInvalid(
    SchemaInvalidArgs(
      operatorReason = operatorReason,
      fileManifest = fileManifest,
      rejectedOutput = null,
      malformedOutput = malformedOutput,
      retryReason = retryReason,
      correctiveRepairContext = correctiveRepairContext,
    ),
  )

  internal fun prepareLaunch(context: FeatureTaskRuntimeRunLoopContext, args: PrepareLaunchArgs): PreparedLaunch {
    with(context) {
    val run = args.run
    val state = args.state
    val priorCorrection = args.priorCorrection
    val repositoryCheckpoint = args.repositoryCheckpoint
    val resolvedBranchRecord = recorder.loadResolvedBranch(run.request.workflowId)
    val handoff = assembleLaunchHandoff(
      request,
      recorder,
      AssembleLaunchHandoffArgs(
        run,
        state,
        repositoryCheckpoint,
        resolvedBranchRecord,
      ),
    )
    recorder.validateHandoffDeclarations(handoff.projectionDeclarations)
    val sharedEvidence = FeatureTaskRuntimeRunLoopOutputVerification.resolveSharedReviewEvidence(
      phaseGates,
      run,
      repositoryCheckpoint,
    )
    val briefing = FeatureTaskRuntimePhaseBriefingAssembler.assemble(
      handoff,
      run.request.workflowId,
      phaseGates.planningProjectionValidator,
      run.request.agentAddonSelection,
      sharedEvidence?.reference,
    )
    if (!FeatureTaskRuntimePhaseWorkflowDefinition.singleAgentSessionOnly(run.phaseId)) {
      recorder.recordPhaseBriefing(
        run.request.workflowId,
        briefing,
        sharedEvidence?.measurement,
      )
    }
    val prompt = FeatureTaskRuntimeRunLoopOutputPersistence.composeLaunchPrompt(context,
      ComposeLaunchPromptArgs(
        run,
        state,
        handoff,
        priorCorrection,

        briefing,
      ),
    )
    return PreparedLaunch(briefing, prompt)

    }}

  private fun assembleLaunchHandoff(
    request: FeatureTaskRuntimeRunRequest,
    recorder: FeatureTaskRuntimePhaseRecorder,
    args: AssembleLaunchHandoffArgs,
  ) = FeatureTaskRuntimeHandoffContract.assembleHandoff(
    FeatureTaskRuntimeHandoffAssemblyRequest(
      declaration = args.run.declaration,
      runInvariants = args.run.request.runInvariants,
      recordedOutputs = args.state.outputs(),
      drivingVerdict = args.run.reentry?.drivingVerdict,
      repairLedger = null,
      repositoryCheckpoint = args.repositoryCheckpoint,
      expectedRepositoryCheckpoint = expectedCheckpointForLaunch(args.run, args.repositoryCheckpoint)
        ?.let(::FeatureTaskRuntimeRepositoryCheckpoint),
      branchIdentity = args.resolvedBranchRecord?.branch,
      baseBranch = args.resolvedBranchRecord?.baseBranch ?: "main",
      validationDepth = args.run.request.goalContinuation?.validationDepth ?: ValidationDepth.DEFAULT,
      qualityGateSelection = FeatureTaskRuntimeRunLoopTransitions.qualityGateSelection(request),
    ),
  ).copy(
    recordedFindingVerdicts = FeatureTaskRuntimeRunLoopOutputVerification.recordedFindingVerdictsForFixHandoff(
      recorder,
      args.run,

      args.state,
    ),
  )

  private fun composeLaunchPrompt(context: FeatureTaskRuntimeRunLoopContext, args: ComposeLaunchPromptArgs): String {
    with(context) {
    return FeatureTaskRuntimePhasePromptComposer.compose(FeatureTaskRuntimeRunLoopOutputPersistence.composeLaunchPromptInputs(context, args)) +
      FeatureTaskRuntimeRunLoopLaunch.verifyFindingsSpecIntentSection(state, recorder, session, phaseGates, args.run)

    }}

  private fun composeLaunchPromptInputs(context: FeatureTaskRuntimeRunLoopContext,
    args: ComposeLaunchPromptArgs,
  ): FeatureTaskRuntimePhasePromptComposeInputs {
    with(context) {
    val run = args.run
    val state = args.state
    val handoff = args.handoff
    val priorCorrection = args.priorCorrection
    val briefing = args.briefing
    val context = this
    val resolvedBranchRecord = recorder.loadResolvedBranch(run.request.workflowId)
    val (passNumber, depthResolution, executedTier) = FeatureTaskRuntimeRunLoopOutputPersistence.resolveReviewPromptTier(context, run, state)
    return FeatureTaskRuntimePhasePromptComposeInputs(
      issueKey = run.request.issueKey,
      briefing = briefing,
      suppressDecomposition = isGoalContinuationRun(run.request),
      codeReviewMode = executedTier,
      reviewPassNumber = passNumber,
      goalSubtaskReviewInput = run.goalReviewInput,
      baselineUntrackedPaths = resolvedBranchRecord?.baselineUntrackedPaths.orEmpty(),
      resolvedReviewTier = depthResolution?.let { executedTier },
      reviewDecidingRule = depthResolution?.decidingRule,
      repairLedger = handoff.repairLedger,
      priorReviewContext = null,
      priorSchemaFailure = priorCorrection?.schemaGateReason,
      priorTerminalFailure = priorCorrection?.retryableTerminalReason,
      priorFindingCoverage = priorCorrection?.findingCoverageReason,
      correctiveRepairContext = priorCorrection?.correctiveRepairContext,
      operatorBlockRetry = session.operatorBlockRetry
        ?.takeIf { it.phaseId == run.phaseId && !session.operatorBlockRetryCompleted },
      implementationContinuation =
      FeatureTaskRuntimeRunLoopOutputVerification.implementationContinuationFor(recorder, run),
      validationGateFindings = run.validationGateFindings,
      validationGateTriagePlan = run.validationGateTriagePlan,
      validationGateRepair = run.validationGateRepair,
      validationGateTriage = run.validationGateTriage,
      agentRunValidateFallback = run.agentRunValidateFallback,
      packCollectAllCommand = FeatureTaskRuntimeRunLoopOutputPersistence.packCollectAllCommand(context, run),
      packConfirmationGateCommand = FeatureTaskRuntimeRunLoopOutputPersistence.packConfirmationGateCommand(context, run),
      packBuildCommand = FeatureTaskRuntimeRunLoopOutputPersistence.packBuildCommand(context, run),
      auditRetryFocusHint = session.auditRetryFocusHint?.takeIf {
        run.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT
      },
    )

    }}

  private fun packCollectAllCommand(context: FeatureTaskRuntimeRunLoopContext, run: PhaseRun): String? =
    with(context) {
      FeatureTaskRuntimeRunLoopValidationGate.packCollectAllCommand(
        phaseGates,
        recorder,
        goalContinuationRecorder,
        session,
        run,
      )
    }

  private fun packConfirmationGateCommand(context: FeatureTaskRuntimeRunLoopContext, run: PhaseRun): String? =
    with(context) {
      FeatureTaskRuntimeRunLoopValidationGate.packConfirmationGateCommand(
        phaseGates,
        recorder,
        goalContinuationRecorder,
        session,
        run,
      )
    }

  private fun packBuildCommand(context: FeatureTaskRuntimeRunLoopContext, run: PhaseRun): String? =
    with(context) {
      FeatureTaskRuntimeRunLoopValidationGate.packBuildCommand(
        phaseGates,
        recorder,
        goalContinuationRecorder,
        session,
        run,
      )
    }

  private fun resolveReviewPromptTier(context: FeatureTaskRuntimeRunLoopContext,
    run: PhaseRun,
    state: FeatureTaskRuntimeRunState,
  ): Triple<Int?, ReviewPassResolution?, CodeReviewExecutionMode> {
    with(context) {
    val passNumber = reviewPassNumber(request, goalContinuationRecorder, run, state)
    val resolution = passNumber?.let { pass ->
      FeatureTaskRuntimeReviewPassSequence.resolveForPass(run.request.runInvariants.codeReviewMode, pass)
    }
    val executedTier = RuntimeOwnedReviewMode.execute(
      resolution?.resolvedTier ?: run.request.runInvariants.codeReviewMode,
    )
    resolution?.let {
      FeatureTaskRuntimeRunLoopPlanningBranch.persistResolvedReviewTier(
        request,
        goalContinuationRecorder,
        run,
        it,
      )
    }
    return Triple(passNumber, resolution, executedTier)

    }}

  internal fun persistPhase(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    recorder: FeatureTaskRuntimePhaseRecorder,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    args: PersistPhaseArgs,
  ) {
    val write = args.write
    val phaseState =
      phaseStateRequest(
        request,
        state,
        goalContinuationRecorder,
        PhaseStateRequestArgs(
          write = write,
          extras = PhaseStateRequestAttachments(
            fileManifest = args.fileManifest,
            launched = args.launched,
            reviewRunId = args.reviewRunId,
          ),
        ),
      )
    state.reserveReviewPass(phaseState.reviewPassNumber)
    recorder.recordPhaseState(
      phaseState,
    )
  }

  internal fun phaseStateRequest(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    args: PhaseStateRequestArgs,
  ): FeatureTaskRuntimePhaseStateRequest {
    val write = args.write
    val run = write.run
    val extras = args.extras
    val fileManifest = extras.fileManifest
    return FeatureTaskRuntimePhaseStateRequest(
      workflowId = run.request.workflowId,
      phaseId = run.phaseId,
      status = write.status,
      attemptCount = write.iteration,
      resolvedAgentId = run.resolvedAgent.resolvedAgentId,
      finished = write.finished,
      outputArtifact = write.outputArtifact,
      normalizedOutput = extras.normalizedOutput,
      repairEvidence = extras.repairEvidence,
      repositoryFingerprint = extras.repositoryFingerprint,
      fileManifestBefore = fileManifest?.before.orEmpty(),
      fileManifestAfter = fileManifest?.after.orEmpty(),
      fileManifestIntroduced = fileManifest?.introduced.orEmpty(),
      loopId = run.reentry?.loopId,
      edgeIteration = run.reentry?.edgeIteration,
      reviewPassNumber = reviewPassNumber(request, goalContinuationRecorder, run, state),
      launchedModel = extras.launched?.modelOverride,
      launchedEffort = extras.launched?.persistedEffort,
      launchOutcomeKnown = extras.launched != null,
      reviewRunId = extras.reviewRunId,
    )
  }

  internal fun launchedModelDirective(run: PhaseRun): LaunchedModelDirective {
    val model = run.modelDirective?.model
    val effort = run.modelDirective?.effort
    if (run.resolvedAgent.resolvedAgentId == InstallAgent.CURSOR.id && model != null && effort != null) {
      return LaunchedModelDirective("$model[effort=$effort]", effort, persistedEffort = null)
    }
    return LaunchedModelDirective(model, effort, effort)
  }

  internal fun reviewPassNumber(
    request: FeatureTaskRuntimeRunRequest,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder?,
    run: PhaseRun,
    state: FeatureTaskRuntimeRunState,
  ): Int? {
    if (run.phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW) return null
    if (goalContinuationRecorder == null) return state.currentReviewPassNumber ?: 1
    val durable = FeatureTaskRuntimeRunLoopPlanningBranch.goalReviewStateOrNull(
      request,
      goalContinuationRecorder,
    ) ?: return 1
    return resolveReviewPassNumber(
      reservedPassNumber = durable.reservedPassNumber ?: state.currentReviewPassNumber,
      completedReviewPassCount = durable.completedPassCount,
    )
  }

  internal fun ReviewOutputPersistenceContext.goalReviewPhaseCompletionRequest(
    args: PhaseReviewPersistenceArgs,
    normalizedOutput: NormalizedFeatureTaskRuntimePhaseOutput,
    repairEvidence: FeatureTaskRuntimePhaseOutputRepairEvidence?,
  ): GoalReviewPhaseCompletionRequest {
    val outputText = normalizedOutput.canonicalJson
    val outputMap = normalizedOutput.envelopeWireMap()
    val recordedVerdicts = recorder.recordedFindingVerdicts(outputMap)
    val findings = GoalSubtaskReviewSummaryReducer.fromOutput(outputMap, recordedVerdicts)
    val outcome = GoalSubtaskReviewSummaryReducer.outcomeFor(outputMap, findings)
    return GoalReviewPhaseCompletionRequest(
      phaseState = phaseStateRequest(
        request,
        state,
        goalContinuationRecorder,
        PhaseStateRequestArgs(
          write = PhaseStateWriteArgs(
            run = args.run,
            iteration = args.iteration,
            status = STATUS_COMPLETED,
            finished = true,
            outputArtifact = outputText,
          ),
          extras = PhaseStateRequestAttachments(
            fileManifest = args.fileManifest,
            normalizedOutput = normalizedOutput,
            repairEvidence = repairEvidence,
          ),
        ),
      ),
      verdict = outcome.verdict,
      unresolvedFindingCount = outcome.unresolvedFindingCount,
      findings = findings,
      rawReviewResult = outputText,
      blockerDispositions = GoalSubtaskReviewSummaryReducer.blockerDispositions(
        outputMap,
        FeatureTaskRuntimeRunLoopPlanningBranch.priorBlockerFindingIds(request, goalContinuationRecorder),
      ),
      commitFocusedAccounting = GoalSubtaskReviewSummaryReducer.commitFocusedAccounting(outputMap),
    )
  }
}

private fun expectedCheckpointForLaunch(
  run: PhaseRun,
  repositoryCheckpoint: FeatureTaskRuntimeRepositoryCheckpoint?,
): String? = if (
  run.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW &&
  run.reentry?.loopId == FeatureTaskRuntimePhaseWorkflowDefinition.REVIEW_FIX_LOOP_ID
) {
  repositoryCheckpoint?.fingerprint
} else {
  run.reentry?.expectedRepositoryCheckpoint ?: repositoryCheckpoint?.fingerprint
}
