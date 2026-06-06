package skillbill.application

import me.tatarka.inject.annotations.Inject
import skillbill.application.model.FeatureTaskRuntimeFixLoopDecision
import skillbill.application.model.FeatureTaskRuntimeGoalContinuationContext
import skillbill.application.model.FeatureTaskRuntimeParallelReviewTelemetry
import skillbill.application.model.FeatureTaskRuntimePhaseStateRequest
import skillbill.application.model.FeatureTaskRuntimePlanningStopDecision
import skillbill.application.model.FeatureTaskRuntimeResolvedPhaseAgent
import skillbill.application.model.FeatureTaskRuntimeReviewLaneTelemetry
import skillbill.application.model.FeatureTaskRuntimeRunEvent
import skillbill.application.model.FeatureTaskRuntimeRunReport
import skillbill.application.model.FeatureTaskRuntimeRunRequest
import skillbill.application.model.FeatureTaskRuntimeSubtaskOutcome
import skillbill.contracts.JsonSupport
import skillbill.error.InvalidFeatureTaskRuntimePhaseOutputSchemaError
import skillbill.install.model.InstallAgent
import skillbill.ports.agentrun.model.AgentRunLaunchFacts
import skillbill.ports.agentrun.model.AgentRunLaunchOutcome
import skillbill.ports.agentrun.model.SkillRunRequest
import skillbill.ports.agentrun.model.UnsupportedAgentRunLaunch
import skillbill.ports.goalrunner.GoalRunnerSubtaskLauncher
import skillbill.ports.goalrunner.model.GoalRunnerSubtaskLaunchRequest
import skillbill.ports.workflow.WorkflowGitOperations
import skillbill.workflow.FeatureTaskRuntimePhaseOutputValidator
import skillbill.workflow.taskruntime.FeatureTaskRuntimeHandoffContract
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeGoalContinuationArtifact
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeGoalContinuationOutcome
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeParallelReviewArtifact
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseDeclaration
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseRecord
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeReviewLaneRecord
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture

/**
 * Runs the feature-task-runtime phase loop deterministically: for each ordered phase it
 * resolves the agent, assembles and persists the handoff, launches one agent synchronously,
 * gates the output through schema validation with a bounded fix loop, and persists per-phase
 * state, resuming from persisted records and blocking loudly on missing upstreams or failures.
 */
@Inject
@Suppress("TooManyFunctions", "ReturnCount", "LargeClass")
class FeatureTaskRuntimeRunner(
  private val subtaskLauncher: GoalRunnerSubtaskLauncher,
  private val recorder: FeatureTaskRuntimePhaseRecorder,
  private val goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
  private val runInvariantsStore: FeatureTaskRuntimeRunInvariantsStore,
  private val outputValidator: FeatureTaskRuntimePhaseOutputValidator,
  private val phaseGates: FeatureTaskRuntimePhaseGates,
) {
  private val branchSetupRunner get() = phaseGates.branchSetupRunner
  private val planningStopper get() = phaseGates.planningStopper
  private val lifecycleTelemetry get() = phaseGates.lifecycleTelemetry
  private val specStatusProjector get() = phaseGates.specStatusProjector

  fun run(request: FeatureTaskRuntimeRunRequest): FeatureTaskRuntimeRunReport {
    recorder.ensureWorkflowOpen(request.workflowId, request.sessionId, request.dbPathOverride)
    val durableRunInvariants = runInvariantsStore.resolve(
      workflowId = request.workflowId,
      dbOverride = request.dbPathOverride,
      proposed = request.runInvariants,
    ) ?: request.runInvariants
    val runRequest = request.copy(runInvariants = durableRunInvariants)
    persistGoalContinuationContext(goalContinuationRecorder, runRequest)
    runRequest.eventSink.emit(
      FeatureTaskRuntimeRunEvent.RunStarted(runRequest.workflowId, runRequest.runInvariants.featureSize.name),
    )
    // Runtime-owned lifecycle telemetry: the runtime mints and emits the started/finished events from
    // its own per-phase records (AC4), never the agent. Per-phase records and ledger remain the
    // authoritative observability source and are unchanged; this telemetry is additive (AC6). Every
    // telemetry call is failure-isolated (logged, never swallowed silently) so a telemetry fault can
    // neither abort the run nor falsely-fail a successful run, and the run exception always propagates.
    // The telemetry seam owns failure isolation: started/finished/finishedError each log on failure and
    // never throw, so a telemetry fault can neither abort the run nor falsely-fail a successful run.
    val telemetrySessionId = lifecycleTelemetry.started(runRequest)
    val observability = FeatureTaskRuntimeRunObservability(recorder, runRequest)
    // Best-effort per-phase outcomes for the finished events; resolved lazily inside the telemetry
    // seam's failure isolation so even loading them cannot abort or falsely-fail the run.
    val phaseOutcomes = {
      recorder.loadPhaseRecords(runRequest.workflowId, runRequest.dbPathOverride)
        .orEmpty()
        .mapValues { (_, record) -> record.status }
    }
    val report = runCatching {
      val state = RunState(recorder.loadPhaseRecords(runRequest.workflowId, runRequest.dbPathOverride).orEmpty())
      val loop = RunLoop(runRequest, state, observability)
      for (phaseId in phasesFor(runRequest)) {
        if (loop.advance(phaseId)) {
          break
        }
      }
      loop.report()
    }.onFailure { error ->
      // An exception escaping the loop (recorder write, launcher RuntimeException, validator
      // non-schema error) would otherwise leave a dangling started-but-never-finished session.
      // Emit the error terminal from best-effort per-phase records, then rethrow the original.
      lifecycleTelemetry.finishedError(telemetrySessionId, phaseOutcomes, runRequest.dbPathOverride)
    }.getOrThrow()
    val terminalReport =
      persistGoalContinuationOutcome(goalContinuationRecorder, recorder, phaseGates.gitOperations, runRequest, report)
    lifecycleTelemetry.finished(
      telemetrySessionId,
      terminalReport,
      phaseOutcomes,
      runRequest.dbPathOverride,
      parallelReviewTelemetry = { parallelReviewTelemetry(recorder, runRequest) },
    )
    return terminalReport
  }

  // Drives the ordered phase loop for one run, owning the run-scoped resolved branch so the loop
  // body stays a single advance() call. The resolved branch is null until the first file-mutating
  // phase forces setup, which re-attaches the persisted branch on resume (never force-switching) so
  // a re-run never creates a second or divergent branch.
  private inner class RunLoop(
    private val request: FeatureTaskRuntimeRunRequest,
    private val state: RunState,
    private val observability: FeatureTaskRuntimeRunObservability,
  ) {
    private var resolvedBranch: String? = null
    private var blocked: FeatureTaskRuntimeRunReport.Blocked? = null
    private var decomposed: FeatureTaskRuntimeRunReport.Decomposed? = null

    // Advances one phase: skips already-complete phases, guarantees the feature branch before a
    // file-mutating phase (preplan/plan may precede setup), then launches the phase.
    // Returns true when the run is now blocked, decomposed, or the loop must otherwise stop.
    fun advance(phaseId: String): Boolean {
      // For an already-complete PLAN, re-evaluate the decompose determination on resume before
      // advancing, so a crash after PLAN persisted completed but before the decompose terminal was
      // observed never silently advances to implement (AC2). Idempotent: a recorded terminal is
      // reconstructed, never duplicate-written.
      val reason = if (state.isComplete(phaseId)) {
        state.outputFor(phaseId)
          ?.takeIf { phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PLAN }
          ?.let { applyPlanningStop(phaseId, it) }
      } else {
        establishBranchIfNeeded(phaseId) ?: run {
          // Just before the commit phase launches, flip the run's spec-file status frontmatter to
          // complete so the commit_push agent stages and commits it with the feature work, instead
          // of leaving the spec stuck at "Pending" after the run finishes. Every preceding gate has
          // passed by commit_push, so the spec is durably complete; the projector no-ops for any
          // other phase and for goal-continuation children (the goal runner owns their status).
          specStatusProjector.projectCompleteBeforeCommitPhase(phaseId, request)
          runPhaseFor(phaseId)
        }
      }
      return when {
        decomposed != null -> true
        reason != null -> blockAt(phaseId, reason)
        else -> false
      }
    }

    // Runs the phase and records its completed output; returns a blocked reason when it blocks.
    private fun runPhaseFor(phaseId: String): String? {
      val outcome = runPhase(phaseId, request, state, observability)
      return outcome.blockedReason ?: run {
        val completedOutput = requireNotNull(outcome.completedOutput)
        state.recordCompleted(completedOutput)
        applyPlanningStop(phaseId, completedOutput)
      }
    }

    // Resolves the plan-phase stop for the given PLAN output, whether freshly completed or re-read on
    // resume. Sets `decomposed` on a decompose terminal and persists a durable block on a malformed
    // package; returns the blocked reason when the run must block, else null.
    private fun applyPlanningStop(phaseId: String, planOutput: FeatureTaskRuntimePhaseOutput): String? {
      if (phaseId != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PLAN) {
        return null
      }
      return when (val decision = resolvePlanningStop(planOutput)) {
        is FeatureTaskRuntimePlanningStopDecision.Proceed -> null
        is FeatureTaskRuntimePlanningStopDecision.Decomposed -> {
          decomposed = decision.report
          null
        }
        is FeatureTaskRuntimePlanningStopDecision.Blocked -> {
          persistPlanningStopBlock(phaseId, decision.reason)
          decision.reason
        }
      }
    }

    private fun resolvePlanningStop(
      planOutput: FeatureTaskRuntimePhaseOutput,
    ): FeatureTaskRuntimePlanningStopDecision = planningStopper.resolve(
      request = request,
      completedOutput = planOutput,
      completedPhaseIds = state.completedPhaseIds(),
      resolvedBranch = resolvedBranch,
    )

    // Persists a durable terminal blocked record for the plan phase and emits the blocked
    // observability/ledger event so a malformed-decompose block is visible to status and the audit
    // trail, consistent with every other phase block, rather than living only in the in-memory report.
    private fun persistPlanningStopBlock(phaseId: String, reason: String) {
      val resolvedAgentId = FeatureTaskRuntimeAgentResolver.resolve(
        phaseId = phaseId,
        assignment = request.agentAssignment,
        invokedAgentId = request.invokedAgentId,
      ).resolvedAgentId
      recorder.recordPhaseState(
        FeatureTaskRuntimePhaseStateRequest(
          workflowId = request.workflowId,
          phaseId = phaseId,
          status = STATUS_BLOCKED,
          attemptCount = 1,
          resolvedAgentId = resolvedAgentId,
          finished = false,
          outputArtifact = null,
          blockedReason = reason,
        ),
        request.dbPathOverride,
      )
      observability.blocked(phaseId, resolvedAgentId, 1, reason)
    }

    fun report(): FeatureTaskRuntimeRunReport {
      val branch = resolvedBranch
        ?: recorder.loadResolvedBranch(request.workflowId, request.dbPathOverride)?.branch
      return decomposed ?: blocked ?: FeatureTaskRuntimeRunReport.Completed(
        issueKey = request.issueKey,
        workflowId = request.workflowId,
        featureSize = request.runInvariants.featureSize.name,
        completedPhaseIds = state.completedPhaseIds(),
        resolvedBranch = branch,
      )
    }

    // Returns a blocked reason when a file-mutating phase cannot get a feature branch, else null.
    // A branch-setup block is made first-class and symmetric with the per-phase block path: it
    // persists a durable blocked per-phase record and emits the typed branch-setup-blocked
    // observability event + ledger entry so the failure is visible to status queries and the audit
    // trail, not lost in the in-memory report alone.
    private fun establishBranchIfNeeded(phaseId: String): String? {
      if (!isFileMutating(phaseId)) {
        return null
      }
      val setup = branchSetupRunner.ensureFeatureBranch(request, observability)
      return setup.blockedReason?.also { reason -> persistBranchSetupBlock(phaseId, reason) } ?: run {
        resolvedBranch = requireNotNull(setup.establishedBranch)
        clearRecoveredBranchSetupBlock(phaseId)
        null
      }
    }

    // Branch setup recovered on resume (the operator fixed the transient git condition). A prior
    // branch-setup-origin blocked record persisted under "implement" would otherwise be seen by
    // preLaunchBlock and permanently re-block the phase without launching the agent. Clear it only
    // in-memory so the stale durable block remains intact until the real phase launch atomically
    // overwrites it with that phase agent's running record. Genuine phase-agent blocks are untouched.
    private fun clearRecoveredBranchSetupBlock(phaseId: String) {
      if (!state.hasBranchSetupBlock(phaseId)) {
        return
      }
      state.clearBranchSetupBlock(phaseId)
    }

    // Mirrors blockAndPersist for the branch-setup step: persists a durable terminal blocked
    // per-phase record (so blocked-ness survives ledger pruning and surfaces in status queries) and
    // emits the typed branch-setup-blocked observability event plus its ledger entry.
    private fun persistBranchSetupBlock(phaseId: String, reason: String) {
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
        request.dbPathOverride,
      )
      observability.branchSetupBlocked(phaseId, BRANCH_SETUP_AGENT_ID, reason)
    }

    private fun blockAt(phaseId: String, reason: String): Boolean {
      blocked = FeatureTaskRuntimeRunReport.Blocked(
        issueKey = request.issueKey,
        workflowId = request.workflowId,
        featureSize = request.runInvariants.featureSize.name,
        lastIncompletePhase = phaseId,
        blockedReason = reason,
        completedPhaseIds = state.completedPhaseIds(),
        resolvedBranch = resolvedBranch,
      )
      return true
    }
  }

  private fun runPhase(
    phaseId: String,
    request: FeatureTaskRuntimeRunRequest,
    state: RunState,
    observability: FeatureTaskRuntimeRunObservability,
  ): PhaseOutcome {
    val run = PhaseRun(
      phaseId = phaseId,
      declaration = phaseDeclaration(phaseId, request.runInvariants.featureSize),
      resolvedAgent = FeatureTaskRuntimeAgentResolver.resolve(
        phaseId = phaseId,
        assignment = request.agentAssignment,
        invokedAgentId = request.invokedAgentId,
      ),
      request = request,
    )
    // Pre-launch blocks: a phase already durably blocked on a prior run (the record survives ledger
    // pruning, so the budget is never silently reset), or a missing required upstream (never launch
    // blind). Both resolve to a single block here so the agent is not relaunched.
    return preLaunchBlock(run, state, observability) ?: runPhaseAttempts(run, state, observability)
  }

  // Returns a blocked outcome when the phase must block before launching, else null. A persisted
  // blocked record re-blocks at the resumed iteration; a missing upstream blocks at attempt 1.
  private fun preLaunchBlock(
    run: PhaseRun,
    state: RunState,
    observability: FeatureTaskRuntimeRunObservability,
  ): PhaseOutcome? {
    val persisted = state.persistedBlockedReason(run.phaseId)?.let { persistedReason ->
      val reason = persistedReason.ifBlank {
        "Phase '${run.phaseId}' is durably blocked from a prior run; the runtime re-blocks rather than relaunching."
      }
      state.nextIteration(run.phaseId) to reason
    }
    val missing = persisted ?: missingUpstream(run.declaration, state.outputs())?.let { missingIds ->
      1 to "Phase '${run.phaseId}' requires upstream output(s) ${missingIds.joinToString()} that are not " +
        "present; the runtime blocks rather than launching the phase blind."
    }
    return missing?.let { (attemptCount, reason) -> blockAndPersist(run, attemptCount, reason, observability) }
  }

  private fun runPhaseAttempts(
    run: PhaseRun,
    state: RunState,
    observability: FeatureTaskRuntimeRunObservability,
  ): PhaseOutcome {
    if (run.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW &&
      run.request.parallelReview != null
    ) {
      return runParallelReviewPhaseAttempts(run, state, observability)
    }
    val agentId = run.resolvedAgent.resolvedAgentId
    var iteration = state.nextIteration(run.phaseId)
    // The resumed iteration may already exceed the bounded budget (e.g. a fix-loop phase that
    // burned the cap on a prior run with no valid artifact). Block before launching rather than
    // relaunching the agent and bypassing the budget across resumes/crashes.
    FeatureTaskRuntimeFixLoopPolicy.blockReasonIfBudgetExhausted(run.phaseId, iteration)?.let { reason ->
      return blockAndPersist(run, iteration, reason, observability)
    }
    observability.started(run.phaseId, agentId, iteration, iteration > 1 || state.hasPriorRecord(run.phaseId))
    var outcome: PhaseOutcome? = null
    while (outcome == null) {
      val attempt = attemptOnce(run, state, iteration, observability)
      outcome = attempt.settledOutcome
        ?: when (val decision = FeatureTaskRuntimeFixLoopPolicy.decideAfterFailure(run.phaseId, iteration)) {
          is FeatureTaskRuntimeFixLoopDecision.Retry -> {
            iteration = decision.nextIteration
            observability.fixLoopIteration(run.phaseId, agentId, decision.nextIteration, decision.fixLoopIteration)
            null
          }
          is FeatureTaskRuntimeFixLoopDecision.Block -> blockAndPersist(
            run,
            iteration,
            withSchemaGateDetail(decision.blockedReason, requireNotNull(attempt.schemaInvalidReason)),
            observability,
          )
        }
    }
    return outcome
  }

  // Persists a durable terminal blocked per-phase record (so blocked-ness survives ledger
  // pruning), emits the blocked observability/ledger event, and returns the blocked outcome.
  private fun blockAndPersist(
    run: PhaseRun,
    attemptCount: Int,
    reason: String,
    observability: FeatureTaskRuntimeRunObservability,
  ): PhaseOutcome {
    recorder.recordPhaseState(
      FeatureTaskRuntimePhaseStateRequest(
        workflowId = run.request.workflowId,
        phaseId = run.phaseId,
        status = STATUS_BLOCKED,
        attemptCount = attemptCount.coerceAtLeast(1),
        resolvedAgentId = run.resolvedAgent.resolvedAgentId,
        finished = false,
        outputArtifact = null,
        blockedReason = reason,
      ),
      run.request.dbPathOverride,
    )
    observability.blocked(run.phaseId, run.resolvedAgent.resolvedAgentId, attemptCount.coerceAtLeast(1), reason)
    return PhaseOutcome.blocked(reason)
  }

  // Returns SchemaInvalid only on schema-invalid output (caller consults the fix-loop policy).
  // An infrastructure failure must block distinctly rather than be laundered through the schema
  // gate, which would misreport it as bad output and burn the fix-loop budget on doomed retries.
  private fun attemptOnce(
    run: PhaseRun,
    state: RunState,
    iteration: Int,
    observability: FeatureTaskRuntimeRunObservability,
  ): AttemptResult {
    persistPhase(run, iteration, STATUS_RUNNING, finished = false, outputArtifact = null)
    val launch = launchAndCapture(run, state)
    launch.infraFailureReason?.let { reason ->
      return AttemptResult.settled(blockAndPersist(run, iteration, reason, observability))
    }
    return gateOutput(run, iteration, requireNotNull(launch.capturedStdout), observability)
  }

  private fun runParallelReviewPhaseAttempts(
    run: PhaseRun,
    state: RunState,
    observability: FeatureTaskRuntimeRunObservability,
  ): PhaseOutcome {
    val laneSet = resolveParallelReviewLaneSet(run)
      ?: return blockAndPersist(
        run,
        1,
        "parallel_review_unavailable: invalid parallel review request.",
        observability,
      )
    laneSet.blockedReason?.let { reason ->
      return blockAndPersist(run, state.nextIteration(run.phaseId), reason, observability)
    }
    val lanes = requireNotNull(laneSet.lanes)
    var iteration = initialParallelReviewIteration(run, state)
    FeatureTaskRuntimeFixLoopPolicy.blockReasonIfBudgetExhausted(run.phaseId, iteration)?.let { reason ->
      return blockAndPersist(run, iteration, reason, observability)
    }
    recorder.recordParallelReviewRequest(
      run.request.workflowId,
      FeatureTaskRuntimeParallelReviewArtifact(
        requested = true,
        defaultReviewAgentId = lanes.default.agentId,
        alternativeReviewAgentId = lanes.alternative.agentId,
        laneCount = lanes.all.size,
      ),
      run.request.dbPathOverride,
    )
    observability.started(run.phaseId, run.resolvedAgent.resolvedAgentId, iteration, state.hasPriorRecord(run.phaseId))
    var outcome: PhaseOutcome? = null
    while (outcome == null) {
      val attempt = attemptParallelReviewOnce(run, state, iteration, lanes, observability)
      outcome = attempt.settledOutcome
        ?: when (val decision = FeatureTaskRuntimeFixLoopPolicy.decideAfterFailure(run.phaseId, iteration)) {
          is FeatureTaskRuntimeFixLoopDecision.Retry -> {
            iteration = decision.nextIteration
            observability.fixLoopIteration(
              run.phaseId,
              run.resolvedAgent.resolvedAgentId,
              decision.nextIteration,
              decision.fixLoopIteration,
            )
            null
          }
          is FeatureTaskRuntimeFixLoopDecision.Block -> blockAndPersist(
            run,
            iteration,
            withSchemaGateDetail(decision.blockedReason, requireNotNull(attempt.schemaInvalidReason)),
            observability,
          )
        }
    }
    return outcome
  }

  private fun attemptParallelReviewOnce(
    run: PhaseRun,
    state: RunState,
    iteration: Int,
    lanes: ParallelReviewLaneSet,
    observability: FeatureTaskRuntimeRunObservability,
  ): AttemptResult {
    persistPhase(run, iteration, STATUS_RUNNING, finished = false, outputArtifact = null)
    val handoff = FeatureTaskRuntimeHandoffContract.assembleHandoff(
      declaration = run.declaration,
      runInvariants = run.request.runInvariants,
      recordedOutputs = state.outputs(),
    )
    val briefing = FeatureTaskRuntimePhaseBriefingAssembler.assemble(handoff)
    recorder.recordPhaseBriefing(run.request.workflowId, briefing, run.request.dbPathOverride)
    val existing = recorder.loadReviewLaneRecords(run.request.workflowId, run.request.dbPathOverride).orEmpty()
    val completed = existing.filterValues { lane ->
      lane.attemptCount == iteration &&
        lane.status == STATUS_COMPLETED &&
        lane.outputArtifact != null &&
        laneOutputSchemaValid(lane)
    }
    val missing = lanes.all.filter { lane -> completed[lane.laneId] == null }
    val launched = if (missing.isEmpty()) {
      emptyList()
    } else {
      launchReviewLanesConcurrently(run, briefing, iteration, missing)
    }
    launched.firstOrNull { it.infraFailureReason != null }?.let { failed ->
      return AttemptResult.settled(
        blockAndPersist(
          run,
          iteration,
          "Feature-task-runtime parallel review lane '${failed.lane.laneId}' failed: " +
            requireNotNull(failed.infraFailureReason),
          observability,
        ),
      )
    }
    launched.firstOrNull { it.schemaInvalidReason != null }?.let { invalid ->
      return AttemptResult.schemaInvalid(
        "parallel review lane '${invalid.lane.laneId}' produced schema-invalid output: " +
          requireNotNull(invalid.schemaInvalidReason),
      )
    }
    val laneRecords = recorder.loadReviewLaneRecords(run.request.workflowId, run.request.dbPathOverride).orEmpty()
    val completedLaneRecords = lanes.all.mapNotNull { lane -> laneRecords[lane.laneId] }
    if (completedLaneRecords.size != lanes.all.size) {
      return AttemptResult.schemaInvalid("parallel review did not record all lane outputs for attempt $iteration.")
    }
    val mergedOutput = mergeReviewLaneOutputs(run.phaseId, iteration, completedLaneRecords)
    return gateOutput(run, iteration, mergedOutput, observability)
  }

  private fun initialParallelReviewIteration(run: PhaseRun, state: RunState): Int {
    val laneAttempt = recorder.loadReviewLaneRecords(run.request.workflowId, run.request.dbPathOverride)
      .orEmpty()
      .values
      .maxOfOrNull { it.attemptCount }
    return laneAttempt ?: state.nextIteration(run.phaseId)
  }

  private fun laneOutputSchemaValid(lane: FeatureTaskRuntimeReviewLaneRecord): Boolean = runCatching {
    outputValidator.validatePhaseOutputText(requireNotNull(lane.outputArtifact), sourceLabel = "review")
  }.isSuccess

  private fun launchReviewLanesConcurrently(
    run: PhaseRun,
    briefing: skillbill.application.model.FeatureTaskRuntimePhaseLaunchBriefing,
    iteration: Int,
    lanes: List<ParallelReviewLane>,
  ): List<ParallelReviewLaneLaunchResult> {
    val futures = lanes.map { lane ->
      CompletableFuture.supplyAsync {
        launchReviewLane(run, briefing, iteration, lane)
      }
    }
    return futures.map { future -> future.get() }
  }

  @Suppress("LongMethod")
  private fun launchReviewLane(
    run: PhaseRun,
    briefing: skillbill.application.model.FeatureTaskRuntimePhaseLaunchBriefing,
    iteration: Int,
    lane: ParallelReviewLane,
  ): ParallelReviewLaneLaunchResult {
    val startedAt = Instant.now().toString()
    recordReviewLaneState(
      run.request,
      run.request.workflowId,
      FeatureTaskRuntimeReviewLaneRecord(
        laneId = lane.laneId,
        agentId = lane.agentId,
        status = STATUS_RUNNING,
        attemptCount = iteration,
        startedAt = startedAt,
      ),
    )
    run.request.eventSink.emit(
      FeatureTaskRuntimeRunEvent.ReviewLaneStarted(
        workflowId = run.request.workflowId,
        phaseId = run.phaseId,
        laneId = lane.laneId,
        agentId = lane.agentId,
        attemptCount = iteration,
      ),
    )
    val outcome = subtaskLauncher.launch(
      GoalRunnerSubtaskLaunchRequest(
        invokedAgentId = lane.agentId,
        configuredAgentOverrideId = null,
        skillRunRequest = SkillRunRequest(
          issueKey = run.request.issueKey,
          repoRoot = run.request.repoRoot,
          dbPathOverride = run.request.dbPathOverride,
          timeout = run.request.timeout,
          promptOverride = FeatureTaskRuntimePhasePromptComposer.compose(
            issueKey = run.request.issueKey,
            briefing = briefing,
            suppressDecomposition = isGoalContinuationRun(run.request),
          ),
        ),
      ),
    )
    val launch = reconcileLaunch(run.phaseId, outcome)
    launch.infraFailureReason?.let { reason ->
      recordReviewLaneBlocked(run, lane, iteration, startedAt, reason)
      run.request.eventSink.emit(
        FeatureTaskRuntimeRunEvent.ReviewLaneCompleted(
          workflowId = run.request.workflowId,
          phaseId = run.phaseId,
          laneId = lane.laneId,
          agentId = lane.agentId,
          attemptCount = iteration,
          findingCount = 0,
          blocked = true,
        ),
      )
      return ParallelReviewLaneLaunchResult(lane, infraFailureReason = reason)
    }
    val output = requireNotNull(launch.capturedStdout)
    val validationReason = runCatching {
      outputValidator.validatePhaseOutputText(output, sourceLabel = "review")
    }.exceptionOrNull()?.let { error ->
      (error as? InvalidFeatureTaskRuntimePhaseOutputSchemaError)?.reason ?: error.message.orEmpty()
    }
    if (validationReason != null) {
      recordReviewLaneBlocked(run, lane, iteration, startedAt, validationReason)
      run.request.eventSink.emit(
        FeatureTaskRuntimeRunEvent.ReviewLaneCompleted(
          workflowId = run.request.workflowId,
          phaseId = run.phaseId,
          laneId = lane.laneId,
          agentId = lane.agentId,
          attemptCount = iteration,
          findingCount = 0,
          blocked = true,
        ),
      )
      return ParallelReviewLaneLaunchResult(lane, schemaInvalidReason = validationReason)
    }
    recordReviewLaneCompleted(run, lane, iteration, startedAt, output)
    val completedFindingCount = extractReviewFindings(output).size
    run.request.eventSink.emit(
      FeatureTaskRuntimeRunEvent.ReviewLaneCompleted(
        workflowId = run.request.workflowId,
        phaseId = run.phaseId,
        laneId = lane.laneId,
        agentId = lane.agentId,
        attemptCount = iteration,
        findingCount = completedFindingCount,
        blocked = false,
      ),
    )
    return ParallelReviewLaneLaunchResult(lane)
  }

  private fun recordReviewLaneCompleted(
    run: PhaseRun,
    lane: ParallelReviewLane,
    iteration: Int,
    startedAt: String,
    output: String,
  ) {
    val finishedAt = Instant.now().toString()
    recordReviewLaneState(
      run.request,
      run.request.workflowId,
      FeatureTaskRuntimeReviewLaneRecord(
        laneId = lane.laneId,
        agentId = lane.agentId,
        status = STATUS_COMPLETED,
        attemptCount = iteration,
        startedAt = startedAt,
        finishedAt = finishedAt,
        durationMillis = reviewLaneDurationMillis(startedAt, finishedAt),
        outputArtifact = output,
        findingCount = extractReviewFindings(output).size,
      ),
    )
  }

  private fun recordReviewLaneBlocked(
    run: PhaseRun,
    lane: ParallelReviewLane,
    iteration: Int,
    startedAt: String,
    reason: String,
  ) {
    val finishedAt = Instant.now().toString()
    recordReviewLaneState(
      run.request,
      run.request.workflowId,
      FeatureTaskRuntimeReviewLaneRecord(
        laneId = lane.laneId,
        agentId = lane.agentId,
        status = STATUS_BLOCKED,
        attemptCount = iteration,
        startedAt = startedAt,
        finishedAt = finishedAt,
        durationMillis = reviewLaneDurationMillis(startedAt, finishedAt),
        blockedReason = reason,
      ),
    )
  }

  private fun recordReviewLaneState(
    request: FeatureTaskRuntimeRunRequest,
    workflowId: String,
    lane: FeatureTaskRuntimeReviewLaneRecord,
  ) {
    synchronized(recorder) {
      recorder.recordReviewLaneState(workflowId, lane, request.dbPathOverride)
    }
  }

  private fun gateOutput(
    run: PhaseRun,
    iteration: Int,
    outputText: String,
    observability: FeatureTaskRuntimeRunObservability,
  ): AttemptResult {
    // Schema-invalid output carries the validator's reason out so a terminal block is
    // diagnosable from the persisted blocked_reason, not only from transient JVM logs.
    try {
      outputValidator.validatePhaseOutputText(outputText, sourceLabel = run.phaseId)
    } catch (error: InvalidFeatureTaskRuntimePhaseOutputSchemaError) {
      return AttemptResult.schemaInvalid(error.reason)
    }
    persistPhase(run, iteration, STATUS_COMPLETED, finished = true, outputArtifact = outputText)
    observability.completed(run.phaseId, run.resolvedAgent.resolvedAgentId, iteration)
    return AttemptResult.settled(
      PhaseOutcome.completed(FeatureTaskRuntimePhaseOutput(run.phaseId, iteration, outputText)),
    )
  }

  private fun persistPhase(run: PhaseRun, iteration: Int, status: String, finished: Boolean, outputArtifact: String?) {
    recorder.recordPhaseState(
      FeatureTaskRuntimePhaseStateRequest(
        workflowId = run.request.workflowId,
        phaseId = run.phaseId,
        status = status,
        attemptCount = iteration,
        resolvedAgentId = run.resolvedAgent.resolvedAgentId,
        finished = finished,
        outputArtifact = outputArtifact,
      ),
      run.request.dbPathOverride,
    )
  }

  // Persist the briefing before launching so it is a durable handoff a consumer can read
  // back, then deliver the same briefing to the agent as the launch prompt: the phase agent
  // only ever sees what the prompt carries, so a persisted-but-undelivered briefing would
  // leave it running the default goal-continuation flow and failing the schema gate.
  private fun launchAndCapture(run: PhaseRun, state: RunState): LaunchResult {
    val handoff = FeatureTaskRuntimeHandoffContract.assembleHandoff(
      declaration = run.declaration,
      runInvariants = run.request.runInvariants,
      recordedOutputs = state.outputs(),
    )
    val briefing = FeatureTaskRuntimePhaseBriefingAssembler.assemble(handoff)
    recorder.recordPhaseBriefing(run.request.workflowId, briefing, run.request.dbPathOverride)
    val outcome = subtaskLauncher.launch(
      GoalRunnerSubtaskLaunchRequest(
        invokedAgentId = run.resolvedAgent.invokedAgentId,
        configuredAgentOverrideId = run.resolvedAgent.configuredAgentOverrideId,
        skillRunRequest = SkillRunRequest(
          issueKey = run.request.issueKey,
          repoRoot = run.request.repoRoot,
          dbPathOverride = run.request.dbPathOverride,
          timeout = run.request.timeout,
          promptOverride = FeatureTaskRuntimePhasePromptComposer.compose(
            issueKey = run.request.issueKey,
            briefing = briefing,
            suppressDecomposition = isGoalContinuationRun(run.request),
          ),
        ),
      ),
    )
    return reconcileLaunch(run.phaseId, outcome)
  }

  private fun reconcileLaunch(phaseId: String, outcome: AgentRunLaunchOutcome): LaunchResult = when (outcome) {
    is UnsupportedAgentRunLaunch -> LaunchResult.infraFailure(
      "Feature-task-runtime phase '$phaseId' could not launch an agent: ${outcome.reason}",
    )
    is AgentRunLaunchFacts -> infraFailureReason(phaseId, outcome)
      ?.let(LaunchResult::infraFailure)
      ?: LaunchResult.captured(outcome.stdout)
  }

  private fun resolveParallelReviewLaneSet(run: PhaseRun): ParallelReviewLaneSetResolution? {
    val alternative = run.request.parallelReview ?: return null
    val defaultAgentId = normalizeSupportedAgent(run.resolvedAgent.resolvedAgentId)
      ?: return ParallelReviewLaneSetResolution.blocked(
        "parallel_review_unavailable: default review agent '${run.resolvedAgent.resolvedAgentId}' is not supported.",
      )
    val alternativeAgentId = normalizeSupportedAgent(alternative.alternativeAgentId)
      ?: return ParallelReviewLaneSetResolution.blocked(
        "parallel_review_unavailable: alternative review agent '${alternative.alternativeAgentId}' is not supported.",
      )
    if (defaultAgentId == alternativeAgentId) {
      return ParallelReviewLaneSetResolution.blocked(
        "parallel_review_unavailable: alternative review agent '$alternativeAgentId' duplicates the default " +
          "review lane agent.",
      )
    }
    return ParallelReviewLaneSetResolution.lanes(
      ParallelReviewLaneSet(
        default = ParallelReviewLane(DEFAULT_REVIEW_LANE_ID, defaultAgentId),
        alternative = ParallelReviewLane(ALTERNATIVE_REVIEW_LANE_ID, alternativeAgentId),
      ),
    )
  }

  private fun normalizeSupportedAgent(agentId: String): String? =
    runCatching { InstallAgent.fromNormalizedId(agentId).id }.getOrNull()

  private fun mergeReviewLaneOutputs(
    phaseId: String,
    iteration: Int,
    lanes: List<FeatureTaskRuntimeReviewLaneRecord>,
  ): String {
    val findings = lanes.flatMap { lane ->
      extractReviewFindings(requireNotNull(lane.outputArtifact)).map { finding ->
        val sourceId = finding.sourceFindingId
        linkedMapOf<String, Any?>(
          "lane_id" to lane.laneId,
          "agent_id" to lane.agentId,
          "source_finding_id" to sourceId,
          "contributing_agents" to listOf(lane.agentId),
          "finding" to finding.payload,
        )
      }
    }
    return JsonSupport.mapToJsonString(
      linkedMapOf(
        "contract_version" to "0.1",
        "phase_id" to phaseId,
        "status" to "completed",
        "summary" to "Merged ${lanes.size} parallel review lane output(s) for attempt $iteration.",
        "produced_outputs" to linkedMapOf(
          "parallel_review" to linkedMapOf(
            "requested" to true,
            "lane_count" to lanes.size,
            "lanes" to lanes.map { lane ->
              linkedMapOf(
                "lane_id" to lane.laneId,
                "agent_id" to lane.agentId,
                "status" to lane.status,
                "finding_count" to lane.findingCount,
              )
            },
            "merged_finding_count" to findings.size,
            "accepted_count" to 0,
            "rejected_count" to 0,
            "unresolved_count" to findings.size,
          ),
          "findings" to findings,
        ),
      ),
    )
  }

  private fun extractReviewFindings(output: String): List<ReviewFindingPayload> {
    val root = JsonSupport.parseObjectOrNull(output)
      ?.let(JsonSupport::jsonElementToValue)
      ?.let(JsonSupport::anyToStringAnyMap)
      ?: return emptyList()
    val produced = JsonSupport.anyToStringAnyMap(root["produced_outputs"]) ?: return emptyList()
    val rawFindings = produced["findings"] as? List<*> ?: return emptyList()
    return rawFindings.mapIndexedNotNull { index, raw ->
      val payload = JsonSupport.anyToStringAnyMap(raw) ?: return@mapIndexedNotNull null
      val id = (payload["id"] as? String)?.takeIf(String::isNotBlank)
        ?: (payload["finding_id"] as? String)?.takeIf(String::isNotBlank)
        ?: (payload["source_finding_id"] as? String)?.takeIf(String::isNotBlank)
        ?: "finding-${index + 1}"
      ReviewFindingPayload(sourceFindingId = id, payload = payload)
    }
  }

  private data class PhaseRun(
    val phaseId: String,
    val declaration: FeatureTaskRuntimePhaseDeclaration,
    val resolvedAgent: FeatureTaskRuntimeResolvedPhaseAgent,
    val request: FeatureTaskRuntimeRunRequest,
  )

  private data class ParallelReviewLane(
    val laneId: String,
    val agentId: String,
  )

  private data class ParallelReviewLaneSet(
    val default: ParallelReviewLane,
    val alternative: ParallelReviewLane,
  ) {
    val all: List<ParallelReviewLane> = listOf(default, alternative)
  }

  private data class ParallelReviewLaneSetResolution(
    val lanes: ParallelReviewLaneSet? = null,
    val blockedReason: String? = null,
  ) {
    companion object {
      fun lanes(lanes: ParallelReviewLaneSet): ParallelReviewLaneSetResolution =
        ParallelReviewLaneSetResolution(lanes = lanes)

      fun blocked(reason: String): ParallelReviewLaneSetResolution =
        ParallelReviewLaneSetResolution(blockedReason = reason)
    }
  }

  private data class ParallelReviewLaneLaunchResult(
    val lane: ParallelReviewLane,
    val infraFailureReason: String? = null,
    val schemaInvalidReason: String? = null,
  )

  private data class ReviewFindingPayload(
    val sourceFindingId: String,
    val payload: Map<String, Any?>,
  )

  private sealed interface LaunchResult {
    private data class Captured(val stdout: String) : LaunchResult
    private data class InfraFailure(val reason: String) : LaunchResult

    val capturedStdout: String? get() = (this as? Captured)?.stdout
    val infraFailureReason: String? get() = (this as? InfraFailure)?.reason

    companion object {
      fun captured(stdout: String): LaunchResult = Captured(stdout)
      fun infraFailure(reason: String): LaunchResult = InfraFailure(reason)
    }
  }

  // One launch attempt either settles the phase (completed or blocked) or fails the schema
  // gate with the validator's reason, which the fix-loop caller threads into a terminal block.
  private sealed interface AttemptResult {
    private data class Settled(val outcome: PhaseOutcome) : AttemptResult
    private data class SchemaInvalid(val validationReason: String) : AttemptResult

    val settledOutcome: PhaseOutcome? get() = (this as? Settled)?.outcome
    val schemaInvalidReason: String? get() = (this as? SchemaInvalid)?.validationReason

    companion object {
      fun settled(outcome: PhaseOutcome): AttemptResult = Settled(outcome)
      fun schemaInvalid(validationReason: String): AttemptResult = SchemaInvalid(validationReason)
    }
  }

  private sealed interface PhaseOutcome {
    private data class Completed(val output: FeatureTaskRuntimePhaseOutput) : PhaseOutcome
    private data class Blocked(val reason: String) : PhaseOutcome

    val completedOutput: FeatureTaskRuntimePhaseOutput? get() = (this as? Completed)?.output

    val blockedReason: String? get() = (this as? Blocked)?.reason

    companion object {
      fun completed(output: FeatureTaskRuntimePhaseOutput): PhaseOutcome = Completed(output)
      fun blocked(reason: String): PhaseOutcome = Blocked(reason)
    }
  }

  // `completed` is derived from record status while `outputs` carries only records with a
  // validated artifact, so a complete-but-output-less upstream is absent from the handoff and
  // triggers a loud missing-upstream block instead of a blind launch. The loaded per-phase
  // records (not just outputs) are retained so the bounded fix loop resumes from the durable
  // attempt count rather than resetting to iteration 1 on resume/crash. Single-threaded.
  private class RunState(initialRecords: Map<String, FeatureTaskRuntimePhaseRecord>) {
    // A legacy PLAN completed without its now-required PREPLAN predecessor is invalidated up front so
    // the loop re-runs PLAN rather than honouring a pre-PREPLAN completion.
    private val completed: MutableSet<String> =
      initialRecords.values
        .filter { it.status == STATUS_COMPLETED }
        .map { it.phaseId }
        .toMutableSet()
        .also(::invalidateLegacyPlanWithoutPreplan)
    private val outputs: MutableList<FeatureTaskRuntimePhaseOutput> =
      initialRecords.values
        .mapNotNull(::recordToOutput)
        .filterNot { it.phaseId == FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PLAN && it.phaseId !in completed }
        .toMutableList()
    private val priorRecords: MutableSet<String> = initialRecords.keys.toMutableSet()

    // Durable per-phase attempt count from the loaded record (0 when no record exists).
    private val persistedAttemptCounts: MutableMap<String, Int> =
      initialRecords.mapValues { (_, record) -> record.attemptCount }.toMutableMap()

    // Phases already persisted with a durable genuine-phase-agent blocked record. Branch-setup-origin
    // blocked records (resolvedAgentId == BRANCH_SETUP_AGENT_ID) are deliberately excluded: branch
    // setup is re-attemptable on resume, so a recoverable branch-setup failure must never short-circuit
    // a real phase launch once setup succeeds. Genuine per-phase agent blocks still re-block on resume.
    private val blockedRecords: MutableMap<String, String> = initialRecords
      .filterValues { it.status == STATUS_BLOCKED && it.resolvedAgentId != BRANCH_SETUP_AGENT_ID }
      .mapValues { (_, record) -> record.blockedReason.orEmpty() }
      .toMutableMap()

    // Phases carrying a durable branch-setup-origin blocked record from a prior run. Tracked
    // separately from blockedRecords (which only holds genuine phase-agent blocks) so the runner
    // can supersede the stale durable record once branch setup recovers on resume.
    private val branchSetupBlockedPhases: MutableSet<String> = initialRecords
      .filterValues { it.status == STATUS_BLOCKED && it.resolvedAgentId == BRANCH_SETUP_AGENT_ID }
      .keys
      .toMutableSet()

    fun outputs(): List<FeatureTaskRuntimePhaseOutput> = outputs.toList()

    // The latest validated output for the phase (highest iteration), or null when none is present.
    fun outputFor(phaseId: String): FeatureTaskRuntimePhaseOutput? =
      outputs.filter { it.phaseId == phaseId }.maxByOrNull { it.iteration }

    fun isComplete(phaseId: String): Boolean = phaseId in completed

    fun hasPriorRecord(phaseId: String): Boolean = phaseId in priorRecords

    // The durable per-phase record's blocked reason, when the phase already exhausted its
    // budget on a prior run, so resume re-blocks immediately instead of relaunching the agent.
    fun persistedBlockedReason(phaseId: String): String? = blockedRecords[phaseId]

    // True when the phase carries a stale branch-setup-origin blocked record from a prior run that
    // must be superseded now that branch setup has recovered.
    fun hasBranchSetupBlock(phaseId: String): Boolean = phaseId in branchSetupBlockedPhases

    // Branch setup succeeded for the phase this run, so any prior branch-setup-origin block is
    // recovered: forget it in-memory so the phase resumes normally. (The durable record is
    // superseded back to a running state by the runner before the phase launches.)
    fun clearBranchSetupBlock(phaseId: String) {
      branchSetupBlockedPhases.remove(phaseId)
      // The branch-setup block recorded an attemptCount but the phase agent never launched, so the
      // phase must resume at iteration 1, not be charged for the branch-setup attempt.
      persistedAttemptCounts.remove(phaseId)
    }

    // Resume the bounded fix loop from durable state: the next attempt is one past the greater
    // of the persisted record's attempt count and the latest validated output iteration. A phase
    // that already burned N attempts resumes at attempt N+1; the budget is never reset by resume.
    fun nextIteration(phaseId: String): Int {
      val latestOutputIteration = outputs.filter { it.phaseId == phaseId }.maxOfOrNull { it.iteration } ?: 0
      val persistedAttempts = persistedAttemptCounts[phaseId] ?: 0
      return maxOf(persistedAttempts, latestOutputIteration) + 1
    }

    fun recordCompleted(output: FeatureTaskRuntimePhaseOutput) {
      outputs += output
      completed += output.phaseId
      priorRecords += output.phaseId
    }

    fun completedPhaseIds(): List<String> =
      FeatureTaskRuntimePhaseWorkflowDefinition.definition.stepIds.filter { it in completed }
  }

  private companion object {
    const val STATUS_RUNNING = "running"
    const val STATUS_COMPLETED = "completed"
    const val STATUS_BLOCKED = "blocked"

    // Branch setup is a distinct pre-implement step with no resolved phase agent; this sentinel
    // attributes its durable blocked record and ledger entry rather than a real agent id.
    const val BRANCH_SETUP_AGENT_ID = "branch-setup"
    const val DEFAULT_REVIEW_LANE_ID = "default"
    const val ALTERNATIVE_REVIEW_LANE_ID = "alternative"

    // Preplan and plan are non-file-mutating; every later phase mutates or depends on a working
    // tree pinned to the feature branch.
    fun isFileMutating(phaseId: String): Boolean = phaseId !in NON_FILE_MUTATING_PHASES

    val NON_FILE_MUTATING_PHASES = setOf(
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PREPLAN,
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PLAN,
    )

    fun phasesFor(request: FeatureTaskRuntimeRunRequest): List<String> {
      val phases = FeatureTaskRuntimePhaseWorkflowDefinition.definition.stepIds
      return if (isGoalContinuationRun(request)) {
        phases.takeWhile { it != FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PR }
      } else {
        phases
      }
    }

    // Bound on the validator detail appended to a persisted blocked reason so a pathological
    // multi-violation reason cannot bloat the durable record or the CLI progress line.
    const val SCHEMA_GATE_DETAIL_MAX_CHARS = 500

    // Appends the schema validator's formatted reason (bounded) to the fix-loop policy's
    // terminal block reason so a blocked run is diagnosable without access to transient JVM logs.
    fun withSchemaGateDetail(policyReason: String, validationReason: String): String {
      val bounded = if (validationReason.length <= SCHEMA_GATE_DETAIL_MAX_CHARS) {
        validationReason
      } else {
        validationReason.take(SCHEMA_GATE_DETAIL_MAX_CHARS) + "… [truncated]"
      }
      return "$policyReason Last schema-gate failure: $bounded"
    }
  }
}

private fun persistGoalContinuationContext(
  recorder: FeatureTaskRuntimeGoalContinuationRecorder,
  request: FeatureTaskRuntimeRunRequest,
) {
  val context = request.goalContinuation
  if (context != null) {
    recorder.recordGoalContinuationState(
      workflowId = request.workflowId,
      continuation = FeatureTaskRuntimeGoalContinuationArtifact(
        issueKey = context.parentIssueKey,
        subtaskId = context.subtaskId,
        suppressPr = context.suppressPr,
        goalBranch = context.goalBranch,
        parentWorkflowId = context.parentWorkflowId,
      ),
      dbOverride = request.dbPathOverride,
    )
  }
}

private fun parallelReviewTelemetry(
  recorder: FeatureTaskRuntimePhaseRecorder,
  request: FeatureTaskRuntimeRunRequest,
): FeatureTaskRuntimeParallelReviewTelemetry {
  val parallel = recorder.loadParallelReviewRequest(request.workflowId, request.dbPathOverride)
    ?: return FeatureTaskRuntimeParallelReviewTelemetry.NONE
  val lanes = recorder.loadReviewLaneRecords(request.workflowId, request.dbPathOverride).orEmpty().values
    .sortedBy { it.laneId }
  val mergedCount = lanes.sumOf { it.findingCount }
  return FeatureTaskRuntimeParallelReviewTelemetry(
    requested = parallel.requested,
    defaultReviewAgentId = parallel.defaultReviewAgentId,
    alternativeReviewAgentId = parallel.alternativeReviewAgentId,
    laneCount = parallel.laneCount,
    laneStatuses = lanes.map { lane ->
      FeatureTaskRuntimeReviewLaneTelemetry(
        laneId = lane.laneId,
        agentId = lane.agentId,
        status = lane.status,
        findingCount = lane.findingCount,
      )
    },
    mergedFindingCount = mergedCount,
    acceptedFindingCount = 0,
    rejectedFindingCount = 0,
    unresolvedFindingCount = mergedCount,
  )
}

private fun persistGoalContinuationOutcome(
  goalContinuationRecorder: FeatureTaskRuntimeGoalContinuationRecorder,
  phaseRecorder: FeatureTaskRuntimePhaseRecorder,
  gitOperations: WorkflowGitOperations,
  request: FeatureTaskRuntimeRunRequest,
  report: FeatureTaskRuntimeRunReport,
): FeatureTaskRuntimeRunReport {
  val context = request.goalContinuation ?: return report
  val outcome = goalContinuationOutcomeFor(phaseRecorder, gitOperations, request, context, report)
  outcome?.let { terminal ->
    goalContinuationRecorder.recordGoalContinuationState(
      workflowId = request.workflowId,
      outcome = FeatureTaskRuntimeGoalContinuationOutcome(
        issueKey = terminal.issueKey,
        subtaskId = terminal.subtaskId,
        status = terminal.status,
        workflowId = terminal.workflowId,
        commitSha = terminal.commitSha,
        blockedReason = terminal.blockedReason,
        lastResumableStep = terminal.lastResumableStep,
      ),
      workflowStatus = if (terminal.status == "complete") "completed" else "blocked",
      dbOverride = request.dbPathOverride,
    )
  }
  return when {
    report is FeatureTaskRuntimeRunReport.Completed && outcome != null -> report.copy(subtaskOutcome = outcome)
    report is FeatureTaskRuntimeRunReport.Blocked && outcome != null -> report.copy(subtaskOutcome = outcome)
    else -> report
  }
}

private fun goalContinuationOutcomeFor(
  recorder: FeatureTaskRuntimePhaseRecorder,
  gitOperations: WorkflowGitOperations,
  request: FeatureTaskRuntimeRunRequest,
  context: FeatureTaskRuntimeGoalContinuationContext,
  report: FeatureTaskRuntimeRunReport,
): FeatureTaskRuntimeSubtaskOutcome? = when (report) {
  is FeatureTaskRuntimeRunReport.Completed ->
    completedGoalContinuationOutcome(recorder, gitOperations, request, context)
  is FeatureTaskRuntimeRunReport.Blocked -> FeatureTaskRuntimeSubtaskOutcome(
    issueKey = context.parentIssueKey,
    subtaskId = context.subtaskId,
    status = "blocked",
    commitSha = null,
    workflowId = request.workflowId,
    blockedReason = report.blockedReason,
    lastResumableStep = report.lastIncompletePhase,
  )
  is FeatureTaskRuntimeRunReport.Decomposed -> null
}

private fun infraFailureReason(phaseId: String, facts: AgentRunLaunchFacts): String? = when {
  facts.spawnFailed ->
    "Feature-task-runtime phase '$phaseId' failed to launch: the agent process could not be spawned."
  facts.timedOut -> "Feature-task-runtime phase '$phaseId' launch timed out before the agent produced an output."
  facts.interrupted -> "Feature-task-runtime phase '$phaseId' launch was interrupted before completion."
  facts.exitStatus != null && facts.exitStatus != 0 ->
    "Feature-task-runtime phase '$phaseId' agent exited with non-zero status ${facts.exitStatus}."
  else -> null
}

private fun reviewLaneDurationMillis(startedAt: String, finishedAt: String): Long =
  Duration.between(Instant.parse(startedAt), Instant.parse(finishedAt)).toMillis().coerceAtLeast(0)

// Drops a legacy PLAN completion that predates the now-required PREPLAN phase so the loop re-runs
// PLAN rather than honouring a pre-PREPLAN completion.
private fun invalidateLegacyPlanWithoutPreplan(completed: MutableSet<String>) {
  val plan = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PLAN
  val preplan = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PREPLAN
  if (plan in completed && preplan !in completed) {
    completed.remove(plan)
  }
}

private fun recordToOutput(record: FeatureTaskRuntimePhaseRecord): FeatureTaskRuntimePhaseOutput? =
  record.outputArtifact?.let { artifact ->
    FeatureTaskRuntimePhaseOutput(
      phaseId = record.phaseId,
      iteration = record.attemptCount,
      payload = artifact,
    )
  }

private fun phaseDeclaration(
  phaseId: String,
  featureSize: skillbill.workflow.taskruntime.model.FeatureTaskRuntimeFeatureSize,
): FeatureTaskRuntimePhaseDeclaration = FeatureTaskRuntimePhaseWorkflowDefinition.phaseDeclaration(phaseId, featureSize)

private fun missingUpstream(
  declaration: FeatureTaskRuntimePhaseDeclaration,
  recordedOutputs: List<FeatureTaskRuntimePhaseOutput>,
): List<String>? {
  val resolved = FeatureTaskRuntimeHandoffContract
    .resolveUpstreamOutputs(declaration, recordedOutputs)
    .outputsByPhaseId
    .keys
  return declaration.consumedUpstreamPhaseIds.filterNot(resolved::contains).takeIf { it.isNotEmpty() }
}
