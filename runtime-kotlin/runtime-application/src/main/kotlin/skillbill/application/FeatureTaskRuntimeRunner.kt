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
 * Runs the feature-task-runtime phase loop deterministically: for each ordered phase it
 * resolves the agent, assembles and persists the handoff, launches one agent synchronously,
 * gates the output through schema validation with a bounded fix loop, and persists per-phase
 * state, resuming from persisted records and blocking loudly on missing upstreams or failures.
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

  // Returns null only on schema-invalid output (caller consults the fix-loop policy). An
  // infrastructure failure must block distinctly rather than be laundered through the schema
  // gate, which would misreport it as bad output and burn the fix-loop budget on doomed retries.
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
  // back, not dead computation thrown away after the launch.
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

  private fun infraFailureReason(phaseId: String, facts: AgentRunLaunchFacts): String? = when {
    facts.spawnFailed ->
      "Feature-task-runtime phase '$phaseId' failed to launch: the agent process could not be spawned."
    facts.timedOut -> "Feature-task-runtime phase '$phaseId' launch timed out before the agent produced an output."
    facts.interrupted -> "Feature-task-runtime phase '$phaseId' launch was interrupted before completion."
    facts.exitStatus != null && facts.exitStatus != 0 ->
      "Feature-task-runtime phase '$phaseId' agent exited with non-zero status ${facts.exitStatus}."
    else -> null
  }

  private fun validates(phaseId: String, outputText: String): Boolean = try {
    outputValidator.validatePhaseOutputText(outputText, sourceLabel = phaseId)
    true
  } catch (_: InvalidFeatureTaskRuntimePhaseOutputSchemaError) {
    false
  }

  private data class PhaseRun(
    val phaseId: String,
    val declaration: FeatureTaskRuntimePhaseDeclaration,
    val resolvedAgent: FeatureTaskRuntimeResolvedPhaseAgent,
    val request: FeatureTaskRuntimeRunRequest,
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
  // triggers a loud missing-upstream block instead of a blind launch. Single-threaded.
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
