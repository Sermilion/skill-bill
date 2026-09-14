package skillbill.engine.featuretask
import skillbill.application.review.RuntimeOwnedReviewMode
import skillbill.engine.featuretask.model.FeatureTaskRuntimePhaseStateRequest
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunReport
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunRequest
import skillbill.workflow.goal.model.GOAL_SUBTASK_REVIEW_BLOCKER_SEVERITY
import skillbill.workflow.goal.model.GoalSubtaskReviewState
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeBackwardEdge
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeFailureDisposition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeNextPhase
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseDeclaration
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeReviewFinding
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeVerdict
import skillbill.workflow.taskruntime.model.ReviewPassResolution

object FeatureTaskRuntimeRunLoopPlanningBranch {
  fun clearRecoveredBranchSetupBlock(runLoop: FeatureTaskRuntimeRunLoop, phaseId: String) {
    if (!runLoop.state.hasBranchSetupBlock(phaseId)) {
      return
    }
    runLoop.state.clearBranchSetupBlock(phaseId)
  }

  fun persistBranchSetupBlock(runLoop: FeatureTaskRuntimeRunLoop, phaseId: String, reason: String) {
    runLoop.recorder.recordPhaseState(
      FeatureTaskRuntimePhaseStateRequest(
        workflowId = runLoop.request.workflowId,
        phaseId = phaseId,
        status = STATUS_BLOCKED,
        attemptCount = 1,
        resolvedAgentId = BRANCH_SETUP_AGENT_ID,
        finished = false,
        outputArtifact = null,
        blockedReason = reason,
      ),
    )
    runLoop.observability.branchSetupBlocked(phaseId, BRANCH_SETUP_AGENT_ID, reason)
  }

  fun blockAt(runLoop: FeatureTaskRuntimeRunLoop, phaseId: String, reason: String) {
    runLoop.session.transitionToBlocked(
      FeatureTaskRuntimeRunReport.Blocked(
        issueKey = runLoop.request.issueKey,
        workflowId = runLoop.request.workflowId,
        featureSize = runLoop.request.runInvariants.featureSize.name,
        lastIncompletePhase = phaseId,
        blockedReason = reason,
        completedPhaseIds = runLoop.state.completedPhaseIds(),
        resolvedBranch = runLoop.session.resolvedBranch,
      ),
    )
  }

  fun blockOnCapExhaustion(
    runLoop: FeatureTaskRuntimeRunLoop,
    phaseId: String,
    transition: FeatureTaskRuntimeNextPhase.TerminalBlock,
  ) {
    val unresolvedFindings = runLoop.state.unresolvedReviewFindings(phaseId)
    val reason = capExhaustionReason(
      runLoop,
      transition.loopId,
      transition.edgeIteration,
      transition.unresolvedVerdict,
      unresolvedFindings,
    )
    val resolvedAgent = FeatureTaskRuntimeAgentResolver.resolve(
      phaseId = phaseId,
      assignment = runLoop.request.agentAssignment,
      invokedAgentId = runLoop.request.invokedAgentId,
    )
    val run = PhaseRun(
      phaseId = phaseId,
      declaration = phaseDeclaration(
        phaseId,
        runLoop.request.runInvariants.featureSize,
        FeatureTaskRuntimeRunLoopTransitions.qualityGateSelection(runLoop.request),
      ),
      resolvedAgent = resolvedAgent,
      modelDirective = FeatureTaskRuntimeModelResolver.resolve(
        phaseId,
        resolvedAgent.resolvedAgentId,
        runLoop.request.modelAssignment,
      ),
      compaction = runLoop.request.compactionSettings.directiveFor(phaseId),
      request = runLoop.request,
      specSource = runLoop.specSource,
    )
    with(FeatureTaskRuntimeRunLoopPhaseAttempts) {
      runLoop.context.blockAndPersist(
        BlockAndPersistArgs(
          run = run,
          attemptCount = runLoop.state.nextIteration(phaseId),
          reason = reason,
          observability = runLoop.observability,
          loopId = transition.loopId,
          edgeIteration = transition.edgeIteration,
          failureDisposition = FeatureTaskRuntimeFailureDisposition.NEEDS_USER_ACTION,
          payload = BlockAndPersistPayload(outputArtifact = runLoop.state.outputFor(phaseId)?.payload),
        ),
      )
    }
    blockAt(runLoop, phaseId, reason)
  }

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

  fun effectiveEdgeIterationCount(runLoop: FeatureTaskRuntimeRunLoop, edge: FeatureTaskRuntimeBackwardEdge): Int =
    runLoop.state.edgeIterationCount(edge.loopId)

  internal fun persistResolvedReviewTier(
    runLoop: FeatureTaskRuntimeRunLoop,
    run: PhaseRun,
    resolution: ReviewPassResolution,
  ) {
    if (run.phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW ||
      !isGoalContinuationRun(runLoop.request)
    ) {
      return
    }
    runLoop.goalContinuationRecorder.updateReviewState(
      runLoop.request.workflowId,
    ) { state ->
      state.copy(
        resolvedTier = RuntimeOwnedReviewMode.execute(resolution.resolvedTier),
        decidingRule = resolution.decidingRule,
      )
    }
  }

  fun priorBlockerFindingIds(runLoop: FeatureTaskRuntimeRunLoop): List<String> {
    val priorPass = goalReviewStateOrNull(runLoop)?.passResults?.lastOrNull() ?: return emptyList()
    return priorPass.findings
      .filter { it.severity == GOAL_SUBTASK_REVIEW_BLOCKER_SEVERITY }
      .mapIndexed { index, finding -> finding.findingId ?: "pass${priorPass.passNumber}-blocker-${index + 1}" }
  }

  fun goalReviewStateOrNull(runLoop: FeatureTaskRuntimeRunLoop): GoalSubtaskReviewState? =
    if (!isGoalContinuationRun(runLoop.request)) {
      null
    } else {
      runLoop.goalContinuationRecorder.reviewState(runLoop.request.workflowId)
    }

  fun regenerationCapExhaustionReason(runLoop: FeatureTaskRuntimeRunLoop, loopId: String, edgeIteration: Int): String {
    val producer = FeatureTaskRuntimePhaseWorkflowDefinition.REGENERATION_LOOP_ID_BY_PRODUCER.entries
      .firstOrNull { it.value == loopId }?.key
    val latest = producer?.let { producing ->
      runLoop.recorder.loadQuarantinedRecords(runLoop.request.workflowId)
        .orEmpty()
        .lastOrNull { it.producingPhaseId == producing }
    }
    val recordId = latest?.recordIdentifier() ?: producer?.let { "$it#<unknown-iteration>" } ?: "<unknown>"
    return "Quarantine-and-regenerate loop '$loopId' exhausted its regeneration cap after $edgeIteration " +
      "attempt(s): the quarantined record '$recordId' produced by phase '${producer ?: "<unknown>"}' still " +
      "fails projection validation. The run blocks durably rather than regenerating past the cap; recover the " +
      "record out of band by deleting or migrating the offending row."
  }

  internal fun runPhase(runLoop: FeatureTaskRuntimeRunLoop, args: RunPhaseArgs): PhaseOutcome {
    val phaseId = args.phaseId
    val request = args.request
    val state = args.state
    val observability = args.observability
    val specSource = args.specSource
    val reentry = args.reentry
    val phaseTokenAccumulator = args.phaseTokenAccumulator
    val declaration = phaseDeclarationForRun(runLoop, phaseId)
    val run = buildPhaseRun(
      runLoop,
      BuildPhaseRunArgs(phaseId, runLoop.request, declaration, runLoop.specSource, reentry),
    )
    FeatureTaskRuntimeRunLoopPhaseRunner.preLaunchBlock(
      runLoop,
      run,
      runLoop.state,
      runLoop.observability,
    )?.let { return it }
    return runPreparedPhase(runLoop, run, runLoop.state, runLoop.observability, runLoop.phaseTokenAccumulator)
  }

  internal fun phaseDeclarationForRun(
    runLoop: FeatureTaskRuntimeRunLoop,
    phaseId: String,
  ): FeatureTaskRuntimePhaseDeclaration {
    val declaration = phaseDeclaration(
      phaseId,
      runLoop.request.runInvariants.featureSize,
      FeatureTaskRuntimeRunLoopTransitions.qualityGateSelection(runLoop.request),
    )
    return declaration
  }

  internal fun buildPhaseRun(runLoop: FeatureTaskRuntimeRunLoop, args: BuildPhaseRunArgs): PhaseRun {
    val phaseId = args.phaseId
    val declaration = args.declaration
    val reentry = args.reentry
    val resolvedAgent = FeatureTaskRuntimeAgentResolver.resolve(
      phaseId = phaseId,
      assignment = runLoop.request.agentAssignment,
      invokedAgentId = runLoop.request.invokedAgentId,
    )
    return PhaseRun(
      phaseId = phaseId,
      declaration = declaration,
      resolvedAgent = resolvedAgent,
      modelDirective = FeatureTaskRuntimeModelResolver.resolve(
        phaseId,
        resolvedAgent.resolvedAgentId,
        runLoop.request.modelAssignment,
      ),
      compaction = runLoop.request.compactionSettings.directiveFor(phaseId),
      request = runLoop.request,
      specSource = runLoop.specSource,
      reentry = reentry,
    )
  }

  internal fun runPreparedPhase(
    runLoop: FeatureTaskRuntimeRunLoop,
    run: PhaseRun,
    state: FeatureTaskRuntimeRunState,
    observability: FeatureTaskRuntimeRunObservability,
    phaseTokenAccumulator: MutableMap<String, Pair<Int, Int>>?,
  ): PhaseOutcome = when (
    val prepared = FeatureTaskRuntimeRunLoopPhaseRunner.prepareGoalReviewRun(
      runLoop,
      run,
      observability,
    )
  ) {
    is GoalReviewRunReady -> when {
      run.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW ->
        FeatureTaskRuntimeRunLoopPhaseRunner.runDeclaredReviewDriverCycle(runLoop, prepared.run, state, observability)
      run.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE ->
        FeatureTaskRuntimeRunLoopValidationGate.runDeclaredValidationGateCycle(
          runLoop,
          prepared.run,
          state,
          observability,
          runLoop.phaseTokenAccumulator,
        )
      run.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_BUILD ->
        FeatureTaskRuntimeRunLoopValidationGate.runDeclaredBuildGateCycle(
          runLoop,
          prepared.run,
          state,
          observability,
          runLoop.phaseTokenAccumulator,
        )
      else -> FeatureTaskRuntimeRunLoopValidationGate.runPhaseAttempts(
        runLoop,
        prepared.run,
        state,
        observability,
        runLoop.phaseTokenAccumulator,
      )
    }
    GoalReviewRunPreparation.CarryForward ->
      FeatureTaskRuntimeRunLoopPhaseRunner.settleCarriedForwardGoalReview(
        runLoop,
        run = run,
        state = state,
        observability = observability,
      )
    is GoalReviewRunPreparation.Blocked -> PhaseOutcome.blocked(prepared.reason)
  }

  fun pauseAt(runLoop: FeatureTaskRuntimeRunLoop, phaseId: String, reason: String, resumableStep: String) {
    runLoop.session.transitionToPaused(
      FeatureTaskRuntimeRunReport.Paused(
        issueKey = runLoop.request.issueKey,
        workflowId = runLoop.request.workflowId,
        featureSize = runLoop.request.runInvariants.featureSize.name,
        pausedPhase = phaseId,
        pauseReason = reason,
        resumableStep = resumableStep,
        completedPhaseIds = runLoop.state.completedPhaseIds(),
        resolvedBranch = runLoop.session.resolvedBranch,
      ),
    )
  }

  fun remediationCheckpointBlockedReason(branch: String, error: String): String =
    "Feature-task-runtime could not establish a remediation checkpoint on the feature branch '$branch' " +
      "before re-entering a mutating phase" + (if (error.isBlank()) "." else " ($error).") +
      " Refusing to re-enter a mutating phase on a dirty, non-reconcilable tree."

  fun auditReviewCheckpointBlockedReason(branch: String, error: String): String =
    "Feature-task-runtime could not commit the audited implementation on the feature branch '$branch' " +
      "before review" + (if (error.isBlank()) "." else " ($error).") +
      " Refusing to review an uncommitted final audit iteration."

  fun capExhaustionReason(
    runLoop: FeatureTaskRuntimeRunLoop,
    loopId: String,
    edgeIteration: Int,
    verdict: FeatureTaskRuntimeVerdict,
    unresolvedFindings: List<FeatureTaskRuntimeReviewFinding> = emptyList(),
  ): String {
    if (FeatureTaskRuntimePhaseWorkflowDefinition.isRegenerationLoopId(loopId)) {
      return regenerationCapExhaustionReason(runLoop, loopId, edgeIteration)
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

  internal fun runPhase(context: FeatureTaskRuntimeRunLoopContext, args: RunPhaseArgs): PhaseOutcome {
    return runPhase(FeatureTaskRuntimeRunLoop(context), args)
  }
}
