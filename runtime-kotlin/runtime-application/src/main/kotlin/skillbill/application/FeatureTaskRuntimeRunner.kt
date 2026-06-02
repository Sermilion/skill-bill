package skillbill.application

import me.tatarka.inject.annotations.Inject
import skillbill.application.model.FeatureTaskRuntimeFixLoopDecision
import skillbill.application.model.FeatureTaskRuntimePhaseStateRequest
import skillbill.application.model.FeatureTaskRuntimeResolvedPhaseAgent
import skillbill.application.model.FeatureTaskRuntimeRunReport
import skillbill.application.model.FeatureTaskRuntimeRunRequest
import skillbill.error.InvalidFeatureTaskRuntimePhaseOutputSchemaError
import skillbill.ports.agentrun.model.AgentRunLaunchFacts
import skillbill.ports.agentrun.model.AgentRunLaunchOutcome
import skillbill.ports.agentrun.model.SkillRunRequest
import skillbill.ports.agentrun.model.UnsupportedAgentRunLaunch
import skillbill.ports.goalrunner.GoalRunnerSubtaskLauncher
import skillbill.ports.goalrunner.model.GoalRunnerSubtaskLaunchRequest
import skillbill.workflow.FeatureTaskRuntimePhaseOutputValidator
import skillbill.workflow.taskruntime.FeatureTaskRuntimeHandoffContract
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseDeclaration
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseRecord

/**
 * SKILL-65 Subtask 3 (AC1-AC4, AC7, AC8): the deterministic feature-task-runtime
 * phase-loop runner.
 *
 * Modeled on [GoalRunner]'s while-loop, but iterating the runtime definition's
 * ordered `stepIds` (`plan -> implement -> review -> audit -> validate`) instead
 * of decomposition subtasks. For each phase it:
 *
 *  - (resume, AC7) skips already-complete phases from persisted per-phase records
 *    and restores prior outputs into the handoff store;
 *  - (handoff, AC2/AC8) assembles the three-layer handoff from the STATIC phase
 *    declaration plus the run-invariants, then LOUD-FAILS when a required
 *    upstream output declared in `consumedUpstreamPhaseIds` is missing rather
 *    than launching the phase blind;
 *  - (agent, AC6) resolves the effective per-phase agent and launches exactly one
 *    agent synchronously through the existing [GoalRunnerSubtaskLauncher] port —
 *    no new process/CLI adapter;
 *  - (schema gate, AC3) validates the captured output via
 *    [FeatureTaskRuntimePhaseOutputValidator]; on failure it NEVER marks the
 *    phase complete and either re-runs within the bounded fix loop
 *    ([FeatureTaskRuntimeFixLoopPolicy]) or blocks the run loudly/observably;
 *  - (persist, AC4) on valid output records the per-phase state (status, attempt
 *    count, resolved agent id, finished, output artifact) through
 *    [FeatureTaskRuntimePhaseRecorder] — the live production caller of that write
 *    seam — then advances.
 *
 * Observability events and the append-only attempt ledger are wired through
 * [FeatureTaskRuntimeRunObservability] (Subtask 3, Task 5).
 */
@Inject
class FeatureTaskRuntimeRunner(
  private val subtaskLauncher: GoalRunnerSubtaskLauncher,
  private val recorder: FeatureTaskRuntimePhaseRecorder,
  private val outputValidator: FeatureTaskRuntimePhaseOutputValidator,
) {
  fun run(request: FeatureTaskRuntimeRunRequest): FeatureTaskRuntimeRunReport {
    recorder.ensureWorkflowOpen(request.workflowId, request.sessionId, request.dbPathOverride)
    val observability = FeatureTaskRuntimeRunObservability(recorder, request)
    val state = RunState(recorder.loadPhaseRecords(request.workflowId, request.dbPathOverride).orEmpty())
    val orderedPhases = FeatureTaskRuntimePhaseWorkflowDefinition.definition.stepIds
    for (phaseId in orderedPhases) {
      if (state.isComplete(phaseId)) {
        continue
      }
      val outcome = runPhase(phaseId, request, state, observability)
      outcome.blockedReason?.let { reason ->
        return FeatureTaskRuntimeRunReport.Blocked(
          issueKey = request.issueKey,
          workflowId = request.workflowId,
          lastIncompletePhase = phaseId,
          blockedReason = reason,
          completedPhaseIds = state.completedPhaseIds(),
        )
      }
      state.recordCompleted(requireNotNull(outcome.completedOutput))
    }
    return FeatureTaskRuntimeRunReport.Completed(
      issueKey = request.issueKey,
      workflowId = request.workflowId,
      completedPhaseIds = state.completedPhaseIds(),
    )
  }

  // AC2/AC7/AC8: assemble the handoff from the static declaration, loud-fail on a
  // missing required upstream, then run the bounded attempt/fix loop for one phase.
  private fun runPhase(
    phaseId: String,
    request: FeatureTaskRuntimeRunRequest,
    state: RunState,
    observability: FeatureTaskRuntimeRunObservability,
  ): PhaseOutcome {
    val run = PhaseRun(
      phaseId = phaseId,
      declaration = phaseDeclaration(phaseId),
      resolvedAgent = FeatureTaskRuntimeAgentResolver.resolve(
        phaseId = phaseId,
        assignment = request.agentAssignment,
        invokedAgentId = request.invokedAgentId,
        environment = request.environment,
      ),
      request = request,
    )
    missingUpstream(run.declaration, state.outputs())?.let { missing ->
      val reason = "Phase '$phaseId' requires upstream output(s) ${missing.joinToString()} that are not " +
        "present; the runtime blocks rather than launching the phase blind."
      observability.blocked(phaseId, run.resolvedAgent.resolvedAgentId, attemptCount = 1, reason)
      return PhaseOutcome.blocked(reason)
    }
    return runPhaseAttempts(run, state, observability)
  }

  private fun runPhaseAttempts(
    run: PhaseRun,
    state: RunState,
    observability: FeatureTaskRuntimeRunObservability,
  ): PhaseOutcome {
    val agentId = run.resolvedAgent.resolvedAgentId
    var iteration = state.nextIteration(run.phaseId)
    // The first attempt of this run emits START (or RESUME); each bounded re-run
    // emits exactly one FIX_LOOP_ITERATION. Either way, every loop iteration emits
    // exactly one launch marker, so launch count == fix-loop attempt count.
    observability.started(run.phaseId, agentId, iteration, iteration == 1 && state.hasPriorRecord(run.phaseId))
    while (true) {
      attemptOnce(run, state, iteration, observability)?.let { return it }
      when (val decision = FeatureTaskRuntimeFixLoopPolicy.decideAfterFailure(run.phaseId, iteration)) {
        is FeatureTaskRuntimeFixLoopDecision.Retry -> {
          iteration = decision.nextIteration
          observability.fixLoopIteration(run.phaseId, agentId, decision.nextIteration, decision.fixLoopIteration)
        }
        is FeatureTaskRuntimeFixLoopDecision.Block -> {
          observability.blocked(run.phaseId, agentId, iteration, decision.blockedReason)
          return PhaseOutcome.blocked(decision.blockedReason)
        }
      }
    }
  }

  // One bounded attempt: persist RUNNING, launch+reconcile, and on a captured
  // schema-valid output persist COMPLETED. Returns a terminal [PhaseOutcome] when
  // the phase completes or hits a distinct infrastructure failure; null when the
  // output was schema-invalid and the caller should consult the fix-loop policy.
  //
  // F-C2: an infrastructure failure (spawn failure / timeout / interrupt /
  // non-zero exit) is NOT a recoverable bad-output fix-loop iteration. It blocks
  // distinctly with a launch-failure reason rather than being laundered through
  // the schema gate (which would otherwise misreport it as schema-invalid output
  // or burn the bounded fix-loop budget on retries that cannot succeed).
  private fun attemptOnce(
    run: PhaseRun,
    state: RunState,
    iteration: Int,
    observability: FeatureTaskRuntimeRunObservability,
  ): PhaseOutcome? {
    val agentId = run.resolvedAgent.resolvedAgentId
    persistPhase(run, iteration, STATUS_RUNNING, finished = false, outputArtifact = null)
    val launch = launchAndCapture(run, state)
    launch.infraFailureReason?.let { reason ->
      observability.blocked(run.phaseId, agentId, iteration, reason)
      return PhaseOutcome.blocked(reason)
    }
    return completedOutcomeOrNull(run, iteration, requireNotNull(launch.capturedStdout), observability)
  }

  private fun completedOutcomeOrNull(
    run: PhaseRun,
    iteration: Int,
    outputText: String,
    observability: FeatureTaskRuntimeRunObservability,
  ): PhaseOutcome? {
    if (!validates(run.phaseId, outputText)) {
      return null
    }
    persistPhase(run, iteration, STATUS_COMPLETED, finished = true, outputArtifact = outputText)
    observability.completed(run.phaseId, run.resolvedAgent.resolvedAgentId, iteration)
    return PhaseOutcome.completed(FeatureTaskRuntimePhaseOutput(run.phaseId, iteration, outputText))
  }

  // AC4: the live production caller of the per-phase recorder write seam.
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

  // AC1/AC2/AC8 + F-C1: assemble the static three-layer briefing (run-invariants
  // every phase, latest-iteration upstream outputs, derived context) and PERSIST
  // it durably per phase BEFORE launching, so the briefing is the durable handoff
  // a consumer reads (Subtask 4's surface) rather than dead computation. Then
  // launch exactly one agent through the existing launcher port and reconcile the
  // launch facts. The launched agent never selects its own inputs.
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
        ),
      ),
    )
    return reconcileLaunch(run.phaseId, outcome)
  }

  // F-C2: reconcile launch facts, mirroring the GoalRunner outcome-reconciliation
  // intent. An infrastructure failure (spawn failure / timeout / interrupt /
  // non-zero exit, or an unsupported-agent launch) is surfaced as a DISTINCT
  // block reason that names the launch failure — never as captured output handed
  // to the schema gate.
  private fun reconcileLaunch(phaseId: String, outcome: AgentRunLaunchOutcome): LaunchResult = when (outcome) {
    is UnsupportedAgentRunLaunch -> LaunchResult.infraFailure(
      "Feature-task-runtime phase '$phaseId' could not launch an agent: ${outcome.reason}",
    )
    is AgentRunLaunchFacts -> infraFailureReason(phaseId, outcome)
      ?.let(LaunchResult::infraFailure)
      ?: LaunchResult.captured(outcome.stdout)
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

  // AC3: schema gate. True when the output validates; false when it is rejected.
  private fun validates(phaseId: String, outputText: String): Boolean = try {
    outputValidator.validatePhaseOutputText(outputText, sourceLabel = phaseId)
    true
  } catch (_: InvalidFeatureTaskRuntimePhaseOutputSchemaError) {
    false
  }

  // One phase's immutable run context, threaded through the attempt loop to keep
  // helper signatures within the parameter-count budget.
  private data class PhaseRun(
    val phaseId: String,
    val declaration: FeatureTaskRuntimePhaseDeclaration,
    val resolvedAgent: FeatureTaskRuntimeResolvedPhaseAgent,
    val request: FeatureTaskRuntimeRunRequest,
  )

  // F-C2: the reconciled result of one launch attempt — either the captured agent
  // stdout (which then flows into the schema gate) or a distinct infrastructure
  // failure that blocks the run loudly without touching the schema gate.
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

  private sealed interface PhaseOutcome {
    private data class Completed(val output: FeatureTaskRuntimePhaseOutput) : PhaseOutcome
    private data class Blocked(val reason: String) : PhaseOutcome

    /** The completed output when this is a completion, else null. */
    val completedOutput: FeatureTaskRuntimePhaseOutput? get() = (this as? Completed)?.output

    /** The blocked reason when this is a block, else null. */
    val blockedReason: String? get() = (this as? Blocked)?.reason

    companion object {
      fun completed(output: FeatureTaskRuntimePhaseOutput): PhaseOutcome = Completed(output)
      fun blocked(reason: String): PhaseOutcome = Blocked(reason)
    }
  }

  // Per-run handoff store seeded from persisted per-phase records (AC7 resume):
  //  - `completed` is derived from each record's STATUS, so a phase marked
  //    complete is skipped even if (corruptly) it left no output artifact;
  //  - `outputs` carries only records that actually persisted a validated output
  //    artifact, so a complete-but-output-less upstream is ABSENT from the handoff
  //    and triggers the loud missing-upstream block rather than launching blind.
  // Mutated only on the single run thread.
  private class RunState(initialRecords: Map<String, FeatureTaskRuntimePhaseRecord>) {
    private val outputs: MutableList<FeatureTaskRuntimePhaseOutput> =
      initialRecords.values.mapNotNull(::recordToOutput).toMutableList()
    private val completed: MutableSet<String> =
      initialRecords.values.filter { it.status == STATUS_COMPLETED }.map { it.phaseId }.toMutableSet()
    private val priorRecords: MutableSet<String> = initialRecords.keys.toMutableSet()

    fun outputs(): List<FeatureTaskRuntimePhaseOutput> = outputs.toList()

    fun isComplete(phaseId: String): Boolean = phaseId in completed

    fun hasPriorRecord(phaseId: String): Boolean = phaseId in priorRecords

    fun nextIteration(phaseId: String): Int =
      (outputs.filter { it.phaseId == phaseId }.maxOfOrNull { it.iteration } ?: 0) + 1

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
  }
}

// A persisted per-phase record contributes a handoff output only when it carries
// a validated output artifact; the record's attempt count is its iteration.
private fun recordToOutput(record: FeatureTaskRuntimePhaseRecord): FeatureTaskRuntimePhaseOutput? =
  record.outputArtifact?.let { artifact ->
    FeatureTaskRuntimePhaseOutput(
      phaseId = record.phaseId,
      iteration = record.attemptCount,
      payload = artifact,
    )
  }

private fun phaseDeclaration(phaseId: String): FeatureTaskRuntimePhaseDeclaration =
  FeatureTaskRuntimePhaseWorkflowDefinition.phaseDeclarations[phaseId]
    ?: error("No phase declaration for runtime phase '$phaseId'.")

// AC7: a declared upstream dependency with no resolved output blocks the run.
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
