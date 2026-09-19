package skillbill.engine.featuretask.runloop.core

import skillbill.application.review.service.RuntimeOwnedReviewMode
import skillbill.engine.featuretask.lifecycle.continuation.FeatureTaskRuntimeGoalContinuationRecorder
import skillbill.engine.featuretask.lifecycle.continuation.isGoalContinuationRun
import skillbill.engine.featuretask.lifecycle.continuation.reviewState
import skillbill.engine.featuretask.lifecycle.core.FeatureTaskRuntimeAgentResolver
import skillbill.engine.featuretask.lifecycle.core.FeatureTaskRuntimeModelResolver
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeRunReport
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeRunRequest
import skillbill.engine.featuretask.model.phase.FeatureTaskRuntimePhaseStateRequest
import skillbill.engine.featuretask.phase.record.FeatureTaskRuntimePhaseRecorder
import skillbill.engine.featuretask.runloop.observability.FeatureTaskRuntimeRunObservability
import skillbill.engine.featuretask.runloop.phase.FeatureTaskRuntimeRunLoopPhaseAttempts
import skillbill.engine.featuretask.runloop.phase.FeatureTaskRuntimeRunLoopPhaseRunner
import skillbill.engine.featuretask.runloop.settlement.FeatureTaskRuntimeRunLoopValidationGate
import skillbill.engine.featuretask.runloop.state.FeatureTaskRuntimeRunState
import skillbill.engine.featuretask.runner.BRANCH_SETUP_AGENT_ID
import skillbill.engine.featuretask.runner.STATUS_BLOCKED
import skillbill.engine.featuretask.runner.phaseDeclaration
import skillbill.engine.featuretask.validation.ReadinessCommitPushSettleResult
import skillbill.workflow.decomposition.model.SpecSource
import skillbill.workflow.goal.model.GOAL_SUBTASK_REVIEW_BLOCKER_SEVERITY
import skillbill.workflow.goal.model.GoalSubtaskReviewState
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimePhaseDeclaration
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeBackwardEdge
import skillbill.workflow.taskruntime.model.phase.FeatureTaskRuntimeFailureDisposition
import skillbill.workflow.taskruntime.model.review.ReviewPassResolution
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition
object FeatureTaskRuntimeRunLoopPlanningBranch {
  internal fun blockOnCapExhaustion(args: BlockOnCapExhaustionArgs) {
    val request = args.request
    val state = args.state
    val recorder = args.recorder
    val observability = args.observability
    val session = args.session
    val goalContinuationRecorder = args.goalContinuationRecorder
    val specSource = args.specSource
    val phaseId = args.phaseId
    val transition = args.transition
    val unresolvedFindings = state.unresolvedReviewFindings(phaseId)
    val reason = capExhaustionReason(
      CapExhaustionReasonArgs(
        request = request,
        recorder = recorder,
        loopId = transition.loopId,
        edgeIteration = transition.edgeIteration,
        verdict = transition.unresolvedVerdict,
        unresolvedFindings = unresolvedFindings,
      ),
    )
    val run = capExhaustionPhaseRun(args)
    FeatureTaskRuntimeRunLoopPhaseAttempts.blockAndPersist(
      request,
      state,
      recorder,
      goalContinuationRecorder,
      BlockAndPersistArgs(
        run = run,
        attemptCount = state.nextIteration(phaseId),
        reason = reason,
        observability = observability,
        loopId = transition.loopId,
        edgeIteration = transition.edgeIteration,
        failureDisposition = FeatureTaskRuntimeFailureDisposition.NEEDS_USER_ACTION,
        payload = BlockAndPersistPayload(outputArtifact = state.outputFor(phaseId)?.payload),
      ),
    )
    blockAt(request, state, session, phaseId, reason)
  }

  private fun capExhaustionPhaseRun(args: BlockOnCapExhaustionArgs): PhaseRun {
    val resolvedAgent = FeatureTaskRuntimeAgentResolver.resolve(
      phaseId = args.phaseId,
      assignment = args.request.agentAssignment,
      invokedAgentId = args.request.invokedAgentId,
    )
    return PhaseRun(
      phaseId = args.phaseId,
      declaration = phaseDeclaration(
        args.phaseId,
        args.request.runInvariants.featureSize,
        FeatureTaskRuntimeRunLoopTransitions.qualityGateSelection(args.request),
      ),
      resolvedAgent = resolvedAgent,
      modelDirective = FeatureTaskRuntimeModelResolver.resolve(
        args.phaseId,
        resolvedAgent.resolvedAgentId,
        args.request.modelAssignment,
      ),
      compaction = args.request.compactionSettings.directiveFor(args.phaseId),
      request = args.request,
      specSource = args.specSource,
    )
  }

  internal fun runPhase(context: FeatureTaskRuntimeRunLoopContext, args: RunPhaseArgs): PhaseOutcome {
    val phaseId = args.phaseId
    val declaration = phaseDeclarationForRun(args.request, phaseId)
    val run = buildPhaseRun(
      phaseId = phaseId,
      request = args.request,
      declaration = declaration,
      specSource = args.specSource,
      reentry = args.reentry,
    )
    FeatureTaskRuntimeRunLoopPhaseRunner.preLaunchBlock(
      context = context,
      run = run,
      state = args.state,
      observability = args.observability,
    )?.let { return it }
    return runPreparedPhase(context, run, args.state, args.observability)
  }

  internal fun phaseDeclarationForRun(
    request: FeatureTaskRuntimeRunRequest,
    phaseId: String,
  ): FeatureTaskRuntimePhaseDeclaration = phaseDeclaration(
    phaseId,
    request.runInvariants.featureSize,
    FeatureTaskRuntimeRunLoopTransitions.qualityGateSelection(request),
  )

  internal fun buildPhaseRun(
    phaseId: String,
    request: FeatureTaskRuntimeRunRequest,
    declaration: FeatureTaskRuntimePhaseDeclaration,
    specSource: SpecSource,
    reentry: PendingReentry?,
  ): PhaseRun {
    val resolvedAgent = FeatureTaskRuntimeAgentResolver.resolve(
      phaseId = phaseId,
      assignment = request.agentAssignment,
      invokedAgentId = request.invokedAgentId,
    )
    return PhaseRun(
      phaseId = phaseId,
      declaration = declaration,
      resolvedAgent = resolvedAgent,
      modelDirective = FeatureTaskRuntimeModelResolver.resolve(
        phaseId,
        resolvedAgent.resolvedAgentId,
        request.modelAssignment,
      ),
      compaction = request.compactionSettings.directiveFor(phaseId),
      request = request,
      specSource = specSource,
      reentry = reentry,
    )
  }

  internal fun runPreparedPhase(
    context: FeatureTaskRuntimeRunLoopContext,
    run: PhaseRun,
    state: FeatureTaskRuntimeRunState,
    observability: FeatureTaskRuntimeRunObservability,
  ): PhaseOutcome {
    val gateContext = context.copy(
      state = state,
      observability = observability,
    )
    val prepared = FeatureTaskRuntimeRunLoopPhaseRunner.prepareGoalReviewRun(
      context = goalReviewContext(context, run, state, observability),
      run = run,
      observability = observability,
    )
    return when (prepared) {
      is GoalReviewRunReady -> runPreparedPhaseReady(gateContext, prepared.run, state, observability)
      GoalReviewRunPreparation.CarryForward ->
        FeatureTaskRuntimeRunLoopPhaseRunner.settleCarriedForwardGoalReview(
          context = goalReviewContext(context, run, state, observability),
        )
      is GoalReviewRunPreparation.Blocked -> PhaseOutcome.blocked(prepared.reason)
    }
  }

  private fun runPreparedPhaseReady(
    context: FeatureTaskRuntimeRunLoopContext,
    run: PhaseRun,
    state: FeatureTaskRuntimeRunState,
    observability: FeatureTaskRuntimeRunObservability,
  ): PhaseOutcome = when (run.phaseId) {
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW ->
      FeatureTaskRuntimeRunLoopPhaseRunner.runDeclaredReviewDriverCycle(
        context = context,
        run = run,
        state = state,
        observability = observability,
      )
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE ->
      with(FeatureTaskRuntimeRunLoopValidationGate) {
        context.runPhaseAttempts(run.copy(agentRunValidateFallback = true))
      }
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_BUILD ->
      with(FeatureTaskRuntimeRunLoopValidationGate) {
        context.runDeclaredBuildGateCycle(run)
      }
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_COMMIT_PUSH ->
      with(FeatureTaskRuntimeRunLoopCommitPush) {
        context.runDeclaredCommitPushCycle(run)
      }
    FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PR -> runPrPhase(context, run)
    else ->
      with(FeatureTaskRuntimeRunLoopValidationGate) {
        context.runPhaseAttempts(run)
      }
  }

  private fun runPrPhase(context: FeatureTaskRuntimeRunLoopContext, run: PhaseRun): PhaseOutcome {
    val readiness = context.phaseGates.readinessGateCoordinator.verifyPrEntryIdentity(
      workflowId = context.request.workflowId,
      repoRoot = context.request.repoRoot,
      baseBranch = context.recorder.loadResolvedBranch(context.request.workflowId)?.baseBranch ?: "main",
      gitOperations = context.phaseGates.gitOperations,
    )
    return if (readiness is ReadinessCommitPushSettleResult.Blocked) {
      PhaseOutcome.blocked(readiness.reason)
    } else {
      with(FeatureTaskRuntimeRunLoopValidationGate) {
        context.runPhaseAttempts(run)
      }
    }
  }

  private fun goalReviewContext(
    context: FeatureTaskRuntimeRunLoopContext,
    run: PhaseRun,
    state: FeatureTaskRuntimeRunState,
    observability: FeatureTaskRuntimeRunObservability,
  ): FeatureTaskRuntimeRunLoopPhaseRunner.GoalReviewContext = FeatureTaskRuntimeRunLoopPhaseRunner.GoalReviewContext(
    request = context.request,
    recorder = context.recorder,
    goalContinuationRecorder = context.goalContinuationRecorder,
    phaseGates = context.phaseGates,
    outputValidator = context.outputValidator,
    session = context.session,
    state = state,
    run = run,
    observability = observability,
  )

  fun remediationCheckpointBlockedReason(branch: String, error: String): String =
    "Feature-task-runtime could not establish a remediation checkpoint on the feature branch '$branch' " +
      "before re-entering a mutating phase" + (if (error.isBlank()) "." else " ($error).") +
      " Refusing to re-enter a mutating phase on a dirty, non-reconcilable tree."

  fun auditReviewCheckpointBlockedReason(branch: String, error: String): String =
    "Feature-task-runtime could not commit the audited implementation on the feature branch '$branch' " +
      "before review" + (if (error.isBlank()) "." else " ($error).") +
      " Refusing to review an uncommitted final audit iteration."

  internal fun blockAt(
    request: FeatureTaskRuntimeRunRequest,
    state: FeatureTaskRuntimeRunState,
    session: FeatureTaskRuntimeRunLoopSession,
    phaseId: String,
    reason: String,
  ) {
    session.transitionToBlocked(
      FeatureTaskRuntimeRunReport.Blocked(
        issueKey = request.issueKey,
        workflowId = request.workflowId,
        featureSize = request.runInvariants.featureSize.name,
        lastIncompletePhase = phaseId,
        blockedReason = reason,
        completedPhaseIds = state.completedPhaseIds(),
        resolvedBranch = session.resolvedBranch,
      ),
    )
  }

  fun persistBranchSetupBlock(
    request: FeatureTaskRuntimeRunRequest,
    recorder: FeatureTaskRuntimePhaseRecorder,
    observability: FeatureTaskRuntimeRunObservability,
    phaseId: String,
    reason: String,
  ) {
    recorder.recordPhaseState(
      FeatureTaskRuntimePhaseStateRequest(
        workflowId = request.workflowId,
        phaseId = phaseId,
        status = STATUS_BLOCKED,
        attemptCount = 1,
        resolvedAgentId = BRANCH_SETUP_AGENT_ID,
        finished = false,
        outputArtifact = null,
        blockedReason = reason,
      ),
    )
    observability.branchSetupBlocked(phaseId, BRANCH_SETUP_AGENT_ID, reason)
  }

  fun clearRecoveredBranchSetupBlock(state: FeatureTaskRuntimeRunState, phaseId: String) {
    if (!state.hasBranchSetupBlock(phaseId)) {
      return
    }
    state.clearBranchSetupBlock(phaseId)
  }

  internal fun pauseAt(args: PauseAtArgs) {
    val request = args.request
    val state = args.state
    val session = args.session
    val phaseId = args.phaseId
    val reason = args.reason
    val resumableStep = args.resumableStep
    session.transitionToPaused(
      FeatureTaskRuntimeRunReport.Paused(
        issueKey = request.issueKey,
        workflowId = request.workflowId,
        featureSize = request.runInvariants.featureSize.name,
        pausedPhase = phaseId,
        pauseReason = reason,
        resumableStep = resumableStep,
        completedPhaseIds = state.completedPhaseIds(),
        resolvedBranch = session.resolvedBranch,
      ),
    )
  }

  fun goalReviewStateOrNull(
    request: FeatureTaskRuntimeRunRequest,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
  ): GoalSubtaskReviewState? = if (!isGoalContinuationRun(request)) {
    null
  } else {
    goalContinuationRecorder.reviewState(request.workflowId)
  }

  fun priorBlockerFindingIds(
    request: FeatureTaskRuntimeRunRequest,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
  ): List<String> {
    val priorPass = goalReviewStateOrNull(request, goalContinuationRecorder)?.passResults?.lastOrNull()
      ?: return emptyList()
    return priorPass.findings
      .filter { it.severity == GOAL_SUBTASK_REVIEW_BLOCKER_SEVERITY }
      .mapIndexed { index, finding -> finding.findingId ?: "pass${priorPass.passNumber}-blocker-${index + 1}" }
  }

  internal fun persistResolvedReviewTier(
    request: FeatureTaskRuntimeRunRequest,
    goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
    run: PhaseRun,
    resolution: ReviewPassResolution,
  ) {
    if (run.phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW || !isGoalContinuationRun(request)) {
      return
    }
    goalContinuationRecorder.updateReviewState(request.workflowId) { state ->
      state.copy(
        resolvedTier = RuntimeOwnedReviewMode.execute(resolution.resolvedTier),
        decidingRule = resolution.decidingRule,
      )
    }
  }

  fun effectiveEdgeIterationCount(state: FeatureTaskRuntimeRunState, edge: FeatureTaskRuntimeBackwardEdge): Int =
    state.edgeIterationCount(edge.loopId)

  internal fun capExhaustionReason(args: CapExhaustionReasonArgs): String {
    val request = args.request
    val recorder = args.recorder
    val loopId = args.loopId
    val edgeIteration = args.edgeIteration
    val verdict = args.verdict
    val unresolvedFindings = args.unresolvedFindings
    if (FeatureTaskRuntimePhaseWorkflowDefinition.isRegenerationLoopId(loopId)) {
      val producer = FeatureTaskRuntimePhaseWorkflowDefinition.REGENERATION_LOOP_ID_BY_PRODUCER.entries
        .firstOrNull { it.value == loopId }?.key
      val latest = producer?.let { producing ->
        recorder.loadQuarantinedRecords(request.workflowId)
          .orEmpty()
          .lastOrNull { it.producingPhaseId == producing }
      }
      val recordId = latest?.recordIdentifier() ?: producer?.let { "$it#<unknown-iteration>" } ?: "<unknown>"
      return "Quarantine-and-regenerate loop '$loopId' exhausted its regeneration cap after $edgeIteration " +
        "attempt(s): the quarantined record '$recordId' produced by phase '${producer ?: "<unknown>"}' still " +
        "fails projection validation. The run blocks durably rather than regenerating past the cap; recover the " +
        "record out of band by deleting or migrating the offending row."
    }
    val findingsSuffix = if (unresolvedFindings.isEmpty()) {
      ""
    } else {
      " Unresolved findings: " +
        unresolvedFindings.joinToString("; ") { "[${it.severity.wireValue}] ${it.message}" } + "."
    }
    return "Backward-edge loop '$loopId' exhausted its per-edge cap after $edgeIteration iteration(s) with the " +
      "verdict '${verdict.wireValue}' still unresolved; the run blocks rather than re-entering past the cap." +
      findingsSuffix
  }
}
