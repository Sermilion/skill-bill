package skillbill.engine.featuretask

import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunRequest
import skillbill.application.review.RuntimeOwnedReviewMode
import skillbill.engine.featuretask.model.FeatureTaskRuntimePhaseStateRequest
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunReport
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
import skillbill.engine.featuretask.FeatureTaskRuntimeGoalContinuationRecorder
import skillbill.workflow.decomposition.model.SpecSource
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseOutputValidator
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeTransitionDeclaration
import java.time.Clock
import skillbill.engine.featuretask.FeatureTaskPhaseSettlementService
import skillbill.application.idestatus.AgentActivityStampWriter
import skillbill.ports.goalrunner.runner.GoalRunnerSubtaskLauncher

object FeatureTaskRuntimeRunLoopPlanningBranch {
  internal fun clearRecoveredBranchSetupBlock(state: FeatureTaskRuntimeRunState, phaseId: String){
    if (!state.hasBranchSetupBlock(phaseId)) {
      return
    }
    state.clearBranchSetupBlock(phaseId)
  }

  internal fun persistBranchSetupBlock(request: FeatureTaskRuntimeRunRequest, recorder: FeatureTaskRuntimePhaseRecorder, observability: FeatureTaskRuntimeRunObservability, phaseId: String, reason: String){
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

  internal fun blockAt(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, session: FeatureTaskRuntimeRunLoopSession, phaseId: String, reason: String){
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

  internal fun blockOnCapExhaustion(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, observability: FeatureTaskRuntimeRunObservability, session: FeatureTaskRuntimeRunLoopSession, specSource: SpecSource, phaseId: String, transition: FeatureTaskRuntimeNextPhase.TerminalBlock){
    val unresolvedFindings = state.unresolvedReviewFindings(phaseId)
    val reason = capExhaustionReason(request, recorder, transition.loopId, transition.edgeIteration, transition.unresolvedVerdict, unresolvedFindings)
    val resolvedAgent = FeatureTaskRuntimeAgentResolver.resolve(
      phaseId = phaseId,
      assignment = request.agentAssignment,
      invokedAgentId = request.invokedAgentId,
    )
    val run = PhaseRun(
      phaseId = phaseId,
      declaration = phaseDeclaration(
        phaseId,
        request.runInvariants.featureSize,
        FeatureTaskRuntimeRunLoopTransitions.qualityGateSelection(request),
      ),
      resolvedAgent = resolvedAgent,
      modelDirective = FeatureTaskRuntimeModelResolver.resolve(
        phaseId,
        resolvedAgent.resolvedAgentId,
        request.modelAssignment,
      ),
      compaction = request.compactionSettings.directiveFor(phaseId),
      request = request,
      specSource = specSource,
    )
    FeatureTaskRuntimeRunLoopPhaseAttempts.blockAndPersist(request, state, recorder, observability, null, BlockAndPersistArgs(
        run = run,
        attemptCount = state.nextIteration(phaseId),
        reason = reason,
        observability = observability,
        loopId = transition.loopId,
        edgeIteration = transition.edgeIteration,
        failureDisposition = FeatureTaskRuntimeFailureDisposition.NEEDS_USER_ACTION,
        payload = BlockAndPersistPayload(outputArtifact = state.outputFor(phaseId)?.payload),
      ))
    blockAt(request, state, session, phaseId, reason)
  }

  internal fun effectiveEdgeIterationCount(state: FeatureTaskRuntimeRunState, edge: FeatureTaskRuntimeBackwardEdge): Int =
    state.edgeIterationCount(edge.loopId)

  internal fun persistResolvedReviewTier(request: FeatureTaskRuntimeRunRequest, goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder, run: PhaseRun, resolution: ReviewPassResolution){
    if (run.phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW ||
      !isGoalContinuationRun(request)
    ) {
      return
    }
    goalContinuationRecorder.updateReviewState(
      request.workflowId,
    ) { state ->
      state.copy(
        resolvedTier = RuntimeOwnedReviewMode.execute(resolution.resolvedTier),
        decidingRule = resolution.decidingRule,
      )
    }
  }

  internal fun priorBlockerFindingIds(request: FeatureTaskRuntimeRunRequest, goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder): List<String> {
    val priorPass = goalReviewStateOrNull(request, goalContinuationRecorder)?.passResults?.lastOrNull() ?: return emptyList()
    return priorPass.findings
      .filter { it.severity == GOAL_SUBTASK_REVIEW_BLOCKER_SEVERITY }
      .mapIndexed { index, finding -> finding.findingId ?: "pass${priorPass.passNumber}-blocker-${index + 1}" }
  }

  internal fun goalReviewStateOrNull(request: FeatureTaskRuntimeRunRequest, goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder): GoalSubtaskReviewState? =
    if (!isGoalContinuationRun(request)) {
      null
    } else {
      goalContinuationRecorder.reviewState(request.workflowId)
    }

  internal fun regenerationCapExhaustionReason(request: FeatureTaskRuntimeRunRequest, recorder: FeatureTaskRuntimePhaseRecorder, loopId: String, edgeIteration: Int): String {
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

  internal fun runPhase(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, recorder: FeatureTaskRuntimePhaseRecorder, observability: FeatureTaskRuntimeRunObservability, session: FeatureTaskRuntimeRunLoopSession, goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder, phaseSettlementService: FeatureTaskPhaseSettlementService, outputValidator: FeatureTaskRuntimePhaseOutputValidator, diagnostics: RuntimeDiagnostics, phaseGates: FeatureTaskRuntimePhaseGates, clock: Clock, transitions: FeatureTaskRuntimeTransitionDeclaration, specSource: SpecSource, phaseTokenAccumulator: MutableMap<String, Pair<Int, Int>>?, subtaskLauncher: GoalRunnerSubtaskLauncher, activityStampWriter: AgentActivityStampWriter, args: RunPhaseArgs): PhaseOutcome {
    val phaseId = args.phaseId
    val request = args.request
    val state = args.state
    val observability = args.observability
    val specSource = args.specSource
    val reentry = args.reentry
    val phaseTokenAccumulator = args.phaseTokenAccumulator
    val declaration = phaseDeclarationForRun(request, phaseId)
    val run = buildPhaseRun(request, specSource, BuildPhaseRunArgs(phaseId, request, declaration, specSource, reentry))
    FeatureTaskRuntimeRunLoopPhaseRunner.preLaunchBlock(request, recorder, session, run, state, observability)?.let { return it }
    return runPreparedPhase(
      request, recorder, session,
      goalContinuationRecorder,
      phaseSettlementService,
      outputValidator,
      diagnostics,
      phaseGates,
      clock,
      transitions,
      subtaskLauncher,
      activityStampWriter,
      phaseTokenAccumulator,
      run,
      state,
      observability,
    )
  }

  internal fun phaseDeclarationForRun(request: FeatureTaskRuntimeRunRequest, phaseId: String): FeatureTaskRuntimePhaseDeclaration {
    val declaration = phaseDeclaration(
      phaseId,
      request.runInvariants.featureSize,
      FeatureTaskRuntimeRunLoopTransitions.qualityGateSelection(request),
    )
    return declaration
  }

  internal fun buildPhaseRun(request: FeatureTaskRuntimeRunRequest, specSource: SpecSource, args: BuildPhaseRunArgs): PhaseRun {
    val phaseId = args.phaseId
    val declaration = args.declaration
    val reentry = args.reentry
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

  internal fun runPreparedPhase(request: FeatureTaskRuntimeRunRequest, recorder: FeatureTaskRuntimePhaseRecorder, session: FeatureTaskRuntimeRunLoopSession, goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder, phaseSettlementService: FeatureTaskPhaseSettlementService, outputValidator: FeatureTaskRuntimePhaseOutputValidator, diagnostics: RuntimeDiagnostics, phaseGates: FeatureTaskRuntimePhaseGates, clock: Clock, transitions: FeatureTaskRuntimeTransitionDeclaration, subtaskLauncher: GoalRunnerSubtaskLauncher, activityStampWriter: AgentActivityStampWriter, phaseTokenAccumulator: MutableMap<String, Pair<Int, Int>>?, run: PhaseRun, state: FeatureTaskRuntimeRunState, observability: FeatureTaskRuntimeRunObservability): PhaseOutcome = when (
    val prepared = FeatureTaskRuntimeRunLoopPhaseRunner.prepareGoalReviewRun(request, state, recorder, goalContinuationRecorder, phaseGates, run, observability)
  ) {
    is GoalReviewRunReady -> when {
      run.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW ->
        FeatureTaskRuntimeRunLoopPhaseRunner.runDeclaredReviewDriverCycle(request, recorder, session, goalContinuationRecorder, outputValidator, diagnostics, phaseGates, clock, prepared.run, state, observability)
      run.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_VALIDATE ->
        FeatureTaskRuntimeRunLoopValidationGate.runDeclaredValidationGateCycle(request, recorder, session, phaseSettlementService, outputValidator, goalContinuationRecorder, diagnostics, phaseGates, clock, transitions, subtaskLauncher, activityStampWriter, phaseTokenAccumulator, prepared.run, state, observability)
      run.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_BUILD ->
        FeatureTaskRuntimeRunLoopValidationGate.runDeclaredBuildGateCycle(request, recorder, session, phaseSettlementService, outputValidator, goalContinuationRecorder, diagnostics, phaseGates, clock, transitions, subtaskLauncher, activityStampWriter, phaseTokenAccumulator, prepared.run, state, observability)
      else -> FeatureTaskRuntimeRunLoopValidationGate.runPhaseAttempts(request, recorder, session, phaseSettlementService, outputValidator, goalContinuationRecorder, diagnostics, phaseGates, clock, transitions, subtaskLauncher, activityStampWriter, prepared.run, state, observability, phaseTokenAccumulator)
    }
    GoalReviewRunPreparation.CarryForward ->
      FeatureTaskRuntimeRunLoopPhaseRunner.settleCarriedForwardGoalReview(request, recorder, goalContinuationRecorder, outputValidator, run, state, observability)
    is GoalReviewRunPreparation.Blocked -> PhaseOutcome.blocked(prepared.reason)
  }

  internal fun pauseAt(request: FeatureTaskRuntimeRunRequest, state: FeatureTaskRuntimeRunState, session: FeatureTaskRuntimeRunLoopSession, phaseId: String, reason: String, resumableStep: String){
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

  internal fun remediationCheckpointBlockedReason(branch: String, error: String): String =
    "Feature-task-runtime could not establish a remediation checkpoint on the feature branch '$branch' " +
      "before re-entering a mutating phase" + (if (error.isBlank()) "." else " ($error).") +
      " Refusing to re-enter a mutating phase on a dirty, non-reconcilable tree."

  internal fun auditReviewCheckpointBlockedReason(branch: String, error: String): String =
    "Feature-task-runtime could not commit the audited implementation on the feature branch '$branch' " +
      "before review" + (if (error.isBlank()) "." else " ($error).") +
      " Refusing to review an uncommitted final audit iteration."

  internal fun capExhaustionReason(request: FeatureTaskRuntimeRunRequest, recorder: FeatureTaskRuntimePhaseRecorder, loopId: String, edgeIteration: Int, verdict: FeatureTaskRuntimeVerdict, unresolvedFindings: List<FeatureTaskRuntimeReviewFinding> = emptyList()): String {
    if (FeatureTaskRuntimePhaseWorkflowDefinition.isRegenerationLoopId(loopId)) {
      return regenerationCapExhaustionReason(request, recorder, loopId, edgeIteration)
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
