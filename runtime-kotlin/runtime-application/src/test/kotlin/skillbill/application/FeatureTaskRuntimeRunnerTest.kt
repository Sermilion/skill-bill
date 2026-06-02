package skillbill.application

import skillbill.application.model.FeatureTaskRuntimeAgentAssignment
import skillbill.application.model.FeatureTaskRuntimeRunEvent
import skillbill.application.model.FeatureTaskRuntimeRunEventSink
import skillbill.application.model.FeatureTaskRuntimeRunReport
import skillbill.application.model.FeatureTaskRuntimeRunRequest
import skillbill.error.InvalidFeatureTaskRuntimePhaseOutputSchemaError
import skillbill.error.InvalidWorkflowStateSchemaError
import skillbill.install.model.InstallAgent
import skillbill.ports.agentrun.model.AgentRunLaunchFacts
import skillbill.ports.agentrun.model.AgentRunLaunchOutcome
import skillbill.ports.goalrunner.GoalRunnerSubtaskLauncher
import skillbill.ports.goalrunner.model.GoalRunnerSubtaskLaunchRequest
import skillbill.ports.persistence.DatabaseSessionFactory
import skillbill.ports.persistence.LearningRepository
import skillbill.ports.persistence.LifecycleTelemetryRepository
import skillbill.ports.persistence.ReviewRepository
import skillbill.ports.persistence.TelemetryOutboxRepository
import skillbill.ports.persistence.UnitOfWork
import skillbill.ports.persistence.WorkflowStateRepository
import skillbill.ports.persistence.model.FeatureImplementSessionSummary
import skillbill.ports.persistence.model.FeatureVerifySessionSummary
import skillbill.ports.persistence.model.WorkflowStateRecord
import skillbill.workflow.FeatureTaskRuntimePhaseOutputValidator
import skillbill.workflow.WorkflowSnapshotValidator
import skillbill.workflow.taskruntime.model.FEATURE_TASK_RUNTIME_PHASE_LEDGER_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.FEATURE_TASK_RUNTIME_PHASE_RECORDS_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRunInvariants
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FeatureTaskRuntimeRunnerTest {
  // (1) AC1: deterministic phase ordering over the definition's stepIds.
  @Test
  fun `runs phases deterministically in plan implement review audit validate order`() {
    // F-T2: assign a distinct agent per phase so each captured launch request is
    // phase-attributable, and assert the load-bearing launch order against the
    // launcher's ACTUAL captured requests rather than the runner's own events.
    val harness = runnerHarness(agentAssignment = phasePerAgentAssignment())
    val report = harness.runner.run(harness.request())

    val completed = assertIs<FeatureTaskRuntimeRunReport.Completed>(report)
    assertEquals(listOf("plan", "implement", "review", "audit", "validate"), completed.completedPhaseIds)
    // Load-bearing: the actual launcher invocations, in order, one per phase.
    assertEquals(
      listOf("plan", "implement", "review", "audit", "validate"),
      harness.launchedPhaseOrder(),
    )
    // The runner's own event stream agrees with the actual launches.
    assertEquals(
      listOf("plan", "implement", "review", "audit", "validate"),
      harness.launchOrder(),
    )
  }

  // (2) AC2/AC8: handoff briefing carries unconditional run-invariants on EVERY
  // phase, the latest-iteration upstream output, and the derived `diff` for review.
  @Test
  fun `each phase briefing includes unconditional run-invariants latest upstream and derived diff for review`() {
    val invariants = FeatureTaskRuntimeRunInvariants(
      specReference = SPEC_REFERENCE,
      acceptanceCriteria = listOf("AC-1", "AC-2"),
      mandatesAndOverrides = listOf("mandate-X"),
    )
    // Latest-iteration upstream outputs accumulated by the time each phase runs.
    val recorded = listOf(
      skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutput("plan", 1, PLAN_OUTPUT),
      skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutput("implement", 1, IMPLEMENT_OUTPUT),
      skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutput("review", 1, VALID_OUTPUT),
    )

    val briefings = listOf("plan", "implement", "review", "audit", "validate").associateWith { phaseId ->
      val declaration =
        skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition.phaseDeclarations.getValue(phaseId)
      val handoff = skillbill.workflow.taskruntime.FeatureTaskRuntimeHandoffContract.assembleHandoff(
        declaration = declaration,
        runInvariants = invariants,
        recordedOutputs = recorded,
      )
      FeatureTaskRuntimePhaseBriefingAssembler.assemble(handoff)
    }

    // Run-invariants present on EVERY phase regardless of need (AC8).
    briefings.forEach { (phaseId, briefing) ->
      assertEquals(SPEC_REFERENCE, briefing.specReference, "spec reference for $phaseId")
      assertEquals(listOf("AC-1", "AC-2"), briefing.acceptanceCriteria, "criteria for $phaseId")
      assertContains(briefing.briefingText, SPEC_REFERENCE)
      assertContains(briefing.briefingText, "mandate-X")
    }
    // implement consumes the latest plan output (AC2 layer 2).
    assertTrue(briefings.getValue("implement").upstreamOutputsByPhaseId.containsKey("plan"))
    assertEquals(PLAN_OUTPUT, briefings.getValue("implement").upstreamOutputsByPhaseId.getValue("plan"))
    // review declares the derived `diff` context (AC2 layer 3).
    assertEquals(listOf("diff"), briefings.getValue("review").derivedContextKeys)
    assertContains(briefings.getValue("review").briefingText, "diff")
  }

  // (3) AC3: schema-gate rejection — a phase whose output fails validation is not
  // marked complete, the run blocks, and the loop never advances.
  @Test
  fun `schema gate rejection on a non-fix-loop phase blocks without advancing`() {
    val harness = runnerHarness(
      validator = ThrowingValidator(failPhases = setOf("implement")),
      agentAssignment = phasePerAgentAssignment(),
    )

    val report = harness.runner.run(harness.request())

    val blocked = assertIs<FeatureTaskRuntimeRunReport.Blocked>(report)
    assertEquals("implement", blocked.lastIncompletePhase)
    assertContains(blocked.blockedReason, "does not participate in a fix loop")
    assertEquals(listOf("plan"), blocked.completedPhaseIds)
    // F-T2: review/audit/validate must never actually launch after the implement
    // block — asserted on the launcher's captured requests, not just the events.
    assertEquals(listOf("plan", "implement"), harness.launchedPhaseOrder())
    assertEquals(listOf("plan", "implement"), harness.launchOrder())
  }

  // (4) AC5: bounded fix-loop iteration honoring the max-3 cap with latest-
  // iteration semantics.
  @Test
  fun `review fix loop re-runs up to the cap then blocks loudly`() {
    val harness = runnerHarness(
      validator = ThrowingValidator(failPhases = setOf("review")),
      agentAssignment = phasePerAgentAssignment(),
    )

    val report = harness.runner.run(harness.request())

    val blocked = assertIs<FeatureTaskRuntimeRunReport.Blocked>(report)
    assertEquals("review", blocked.lastIncompletePhase)
    assertContains(blocked.blockedReason, "exhausted the bounded fix loop")
    // F-T2: plan + implement actually launched once each; review ACTUALLY launched
    // MAX_FIX_LOOP_ITERATIONS times — asserted on the launcher's captured requests
    // (a regression that emits the fix-loop event but fails to re-launch is now
    // caught), not on the runner's own emitted event stream.
    val launchedPhases = harness.launchedPhaseOrder()
    assertEquals(1, launchedPhases.count { it == "plan" })
    assertEquals(1, launchedPhases.count { it == "implement" })
    assertEquals(
      FeatureTaskRuntimeFixLoopPolicy.MAX_FIX_LOOP_ITERATIONS,
      launchedPhases.count { it == "review" },
    )
    // The event stream agrees with the actual launch count.
    assertEquals(
      FeatureTaskRuntimeFixLoopPolicy.MAX_FIX_LOOP_ITERATIONS,
      harness.launchOrder().count { it == "review" },
    )
  }

  @Test
  fun `review fix loop recovers on a later iteration and advances`() {
    var reviewAttempts = 0
    val harness = runnerHarness(
      validator = object : FeatureTaskRuntimePhaseOutputValidator {
        override fun validatePhaseOutputText(phaseOutputText: String, sourceLabel: String) {
          if (sourceLabel == "review") {
            reviewAttempts += 1
            if (reviewAttempts < 2) {
              throw InvalidFeatureTaskRuntimePhaseOutputSchemaError("review", "still failing")
            }
          }
        }
      },
    )

    val report = harness.runner.run(harness.request())

    assertIs<FeatureTaskRuntimeRunReport.Completed>(report)
    assertEquals(2, reviewAttempts)
    // The latest review record carries iteration 2 (latest-iteration semantics).
    val reviewRecord = requireNotNull(harness.recorder.loadPhaseRecords(WORKFLOW_ID).orEmpty()["review"])
    assertEquals(2, reviewRecord.attemptCount)
    assertEquals("completed", reviewRecord.status)
  }

  // (5) AC6: per-phase agent resolution precedence.
  @Test
  fun `per-phase agent resolution honors override then per-phase then invoked default`() {
    val harness = runnerHarness(
      agentAssignment = FeatureTaskRuntimeAgentAssignment(
        perPhaseAgentIds = mapOf("review" to "claude"),
      ),
    )

    harness.runner.run(harness.request())

    val records = harness.recorder.loadPhaseRecords(WORKFLOW_ID).orEmpty()
    // No override + no per-phase entry -> invoking agent (no codex fallback).
    assertEquals(INVOKED_AGENT, records.getValue("plan").resolvedAgentId)
    // per-phase entry wins for review.
    assertEquals("claude", records.getValue("review").resolvedAgentId)
  }

  @Test
  fun `run-wide override wins over per-phase and invoked for every phase`() {
    val harness = runnerHarness(
      agentAssignment = FeatureTaskRuntimeAgentAssignment(
        perPhaseAgentIds = mapOf("review" to "claude"),
        override = "opencode",
      ),
    )

    harness.runner.run(harness.request())

    val records = harness.recorder.loadPhaseRecords(WORKFLOW_ID).orEmpty()
    listOf("plan", "implement", "review", "audit", "validate").forEach { phaseId ->
      assertEquals("opencode", records.getValue(phaseId).resolvedAgentId, "override must win for $phaseId")
    }
  }

  @Test
  fun `invoked agent wins over env and there is no hardcoded codex default`() {
    val resolved = FeatureTaskRuntimeAgentResolver.resolve(
      phaseId = "plan",
      assignment = FeatureTaskRuntimeAgentAssignment(),
      invokedAgentId = INVOKED_AGENT,
      environment = mapOf("SKILL_BILL_AGENT" to "junie"),
    )
    // The invoking agent is the documented default; env only backs an absent
    // invoked agent. There is never a hardcoded codex fallback.
    assertEquals(INVOKED_AGENT, resolved.invokedAgentId)
    assertEquals(INVOKED_AGENT, resolved.resolvedAgentId)
  }

  // (6) AC7: resume from the last incomplete phase, restoring upstream outputs.
  @Test
  fun `resume restarts from last incomplete phase and restores upstream outputs`() {
    val harness = runnerHarness(agentAssignment = phasePerAgentAssignment())
    // Seed durable state as if plan + implement already completed.
    harness.seedPhase("plan", "completed", 1, INVOKED_AGENT, PLAN_OUTPUT)
    harness.seedPhase("implement", "completed", 1, INVOKED_AGENT, IMPLEMENT_OUTPUT)

    val report = harness.runner.run(harness.request())

    assertIs<FeatureTaskRuntimeRunReport.Completed>(report)
    // F-T2: only the incomplete phases ACTUALLY launch; plan/implement are skipped
    // on resume — asserted on the launcher's captured requests.
    assertEquals(listOf("review", "audit", "validate"), harness.launchedPhaseOrder())
    assertEquals(listOf("review", "audit", "validate"), harness.launchOrder())

    // F-T3: assert the resume ACTUALLY restored the persisted upstream outputs into
    // the downstream phases' durable briefings (now that F-C1 persists them). The
    // review phase consumes implement; the audit phase consumes plan + implement +
    // review — all restored from durable state, not re-launched.
    val briefings = harness.recorder.loadPhaseBriefings(WORKFLOW_ID).orEmpty()
    val reviewBriefing = requireNotNull(briefings["review"]) { "review briefing must be persisted" }
    assertEquals(IMPLEMENT_OUTPUT, reviewBriefing.upstreamOutputsByPhaseId["implement"])
    val auditBriefing = requireNotNull(briefings["audit"]) { "audit briefing must be persisted" }
    assertEquals(PLAN_OUTPUT, auditBriefing.upstreamOutputsByPhaseId["plan"])
    assertEquals(IMPLEMENT_OUTPUT, auditBriefing.upstreamOutputsByPhaseId["implement"])
    // review's own (fresh) output is also restored into audit's briefing.
    assertEquals(VALID_OUTPUT, auditBriefing.upstreamOutputsByPhaseId["review"])
  }

  // (7) AC7: a missing required upstream output loud-fails rather than launching blind.
  @Test
  fun `missing required upstream output blocks loudly without launching the phase`() {
    val harness = runnerHarness()
    // Resume state where plan completed WITH an output, but implement is recorded
    // complete-status yet WITHOUT an output artifact (corrupt/partial durable state).
    // On resume implement is skipped (status complete) but its handoff output is
    // absent, so review must loud-fail rather than launch blind on a missing upstream.
    harness.seedPhase("plan", "completed", 1, INVOKED_AGENT, PLAN_OUTPUT)
    harness.seedPhase("implement", "completed", 1, INVOKED_AGENT, outputArtifact = null)

    val report = harness.runner.run(harness.request())

    val blocked = assertIs<FeatureTaskRuntimeRunReport.Blocked>(report)
    assertEquals("review", blocked.lastIncompletePhase)
    assertContains(blocked.blockedReason, "implement")
    assertContains(blocked.blockedReason, "blind")
    // review never launched without its required implement upstream output.
    assertTrue(harness.launchOrder().none { it == "review" })
  }

  @Test
  fun `all upstreams satisfied produces no spurious missing-upstream block`() {
    val harness = runnerHarness()
    val report = harness.runner.run(harness.request())
    assertIs<FeatureTaskRuntimeRunReport.Completed>(report)
  }

  // (8) + CRITICAL emit->store->read guard: observability events AND the durable
  // ledger are written through the runner and survive read-back via the store.
  @Test
  fun `emits observability events and appends durable ledger read back through the store`() {
    val harness = runnerHarness()

    harness.runner.run(harness.request())

    // Event stream: every phase emits a Started and a Completed.
    val started = harness.events.filterIsInstance<FeatureTaskRuntimeRunEvent.PhaseStarted>().map { it.phaseId }
    val done = harness.events.filterIsInstance<FeatureTaskRuntimeRunEvent.PhaseCompleted>().map { it.phaseId }
    assertEquals(listOf("plan", "implement", "review", "audit", "validate"), started)
    assertEquals(listOf("plan", "implement", "review", "audit", "validate"), done)

    // CRITICAL dead-seam guard: read the durable ledger + per-phase records back
    // through the store and assert START/COMPLETE actions and a completed record.
    val artifacts = harness.repository.taskRuntimeArtifacts(WORKFLOW_ID)

    @Suppress("UNCHECKED_CAST")
    val ledger = artifacts[FEATURE_TASK_RUNTIME_PHASE_LEDGER_ARTIFACT_KEY] as List<Map<String, Any?>>
    val actions = ledger.map { it["action"] as String }
    assertContains(actions, "start")
    assertContains(actions, "complete")
    // Monotonic sequence preserved across the run.
    val sequences = ledger.map { (it["sequence_number"] as Number).toInt() }
    assertEquals(sequences.sorted(), sequences)

    @Suppress("UNCHECKED_CAST")
    val records = artifacts[FEATURE_TASK_RUNTIME_PHASE_RECORDS_ARTIFACT_KEY] as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    val planRecord = records["plan"] as Map<String, Any?>
    assertEquals("completed", planRecord["status"])
    assertTrue((planRecord["started_at"] as String).isNotBlank())
    assertTrue((planRecord["finished_at"] as String).isNotBlank())
  }

  @Test
  fun `blocked run appends a blocked ledger entry`() {
    val harness = runnerHarness(validator = ThrowingValidator(failPhases = setOf("implement")))

    harness.runner.run(harness.request())

    val artifacts = harness.repository.taskRuntimeArtifacts(WORKFLOW_ID)

    @Suppress("UNCHECKED_CAST")
    val ledger = artifacts[FEATURE_TASK_RUNTIME_PHASE_LEDGER_ARTIFACT_KEY] as List<Map<String, Any?>>
    val blockedEntry = ledger.single { it["action"] == "blocked" }
    // Nit: the blocked entry also carries the phaseId and a blockedReason.
    assertEquals("implement", blockedEntry["phase_id"])
    assertTrue((blockedEntry["blocked_reason"] as String).isNotBlank())
  }

  // (F-C1) AC2/AC8: the assembled briefing is no longer thrown away — it is
  // persisted durably per phase and read back through the store, carrying the
  // unconditional layer-1 run-invariants on EVERY phase, the latest-iteration
  // upstream outputs, and the derived `diff` context for review.
  @Test
  fun `persists per-phase briefing durably with run-invariants upstream and review diff`() {
    val harness = runnerHarness()

    harness.runner.run(harness.request())

    val briefings = harness.recorder.loadPhaseBriefings(WORKFLOW_ID).orEmpty()
    assertEquals(setOf("plan", "implement", "review", "audit", "validate"), briefings.keys)

    // Layer 1 run-invariants present unconditionally on EVERY persisted briefing.
    briefings.forEach { (phaseId, briefing) ->
      assertEquals(SPEC_REFERENCE, briefing.specReference, "spec reference for $phaseId")
      assertEquals(listOf("AC-1", "AC-2"), briefing.acceptanceCriteria, "criteria for $phaseId")
      assertEquals(listOf("mandate-X"), briefing.mandatesAndOverrides, "mandates for $phaseId")
      assertContains(briefing.briefingText, SPEC_REFERENCE)
      assertContains(briefing.briefingText, "mandate-X")
    }
    // Layer 2 latest-iteration upstream outputs delivered to the consuming phase.
    assertEquals(VALID_OUTPUT, briefings.getValue("implement").upstreamOutputsByPhaseId["plan"])
    assertEquals(VALID_OUTPUT, briefings.getValue("review").upstreamOutputsByPhaseId["implement"])
    // Layer 3 derived `diff` context for review.
    assertEquals(listOf("diff"), briefings.getValue("review").derivedContextKeys)
    assertContains(briefings.getValue("review").briefingText, "diff")
  }

  // (F-C2) launch/infra failure (spawn failure / timeout) blocks DISTINCTLY with a
  // launch-failure reason — not a schema-invalid block and not a fix-loop
  // exhaustion — and does not burn the bounded fix-loop budget on retries.
  @Test
  fun `launch spawn failure blocks distinctly without schema gate or fix loop retries`() {
    val harness = runnerHarness(
      launcher = RuntimeRecordingLauncher { spawnFailedFacts() },
      agentAssignment = phasePerAgentAssignment(),
    )

    val report = harness.runner.run(harness.request())

    val blocked = assertIs<FeatureTaskRuntimeRunReport.Blocked>(report)
    // Blocks at the first phase on the infra failure.
    assertEquals("plan", blocked.lastIncompletePhase)
    assertContains(blocked.blockedReason, "failed to launch")
    // Distinct from the schema-invalid path and the fix-loop-exhaustion path.
    assertTrue(!blocked.blockedReason.contains("schema"))
    assertTrue(!blocked.blockedReason.contains("exhausted the bounded fix loop"))
    // The infra failure was launched exactly once for plan — no fix-loop retries.
    assertEquals(listOf("plan"), harness.launchedPhaseOrder())
  }

  @Test
  fun `launch timeout on a fix-loop phase blocks distinctly without burning the budget`() {
    val harness = runnerHarness(
      launcher = RuntimeRecordingLauncher { request ->
        // plan + implement succeed; review's launch times out (an infra failure).
        if (request.invokedAgentId == phaseAgent("review")) timedOutFacts() else facts(VALID_OUTPUT)
      },
      agentAssignment = phasePerAgentAssignment(),
    )

    val report = harness.runner.run(harness.request())

    val blocked = assertIs<FeatureTaskRuntimeRunReport.Blocked>(report)
    assertEquals("review", blocked.lastIncompletePhase)
    assertContains(blocked.blockedReason, "timed out")
    assertTrue(!blocked.blockedReason.contains("exhausted the bounded fix loop"))
    // review launched exactly once — the timeout did NOT burn the fix-loop budget.
    assertEquals(1, harness.launchedPhaseOrder().count { it == "review" })
  }

  // (F-P1) a present-but-malformed per-phase records artifact loud-fails on resume
  // rather than silently being read as "no records" (which would re-run completed
  // phases / lose outputs).
  @Test
  fun `malformed per-phase records artifact loud-fails on resume`() {
    val harness = runnerHarness()
    // Seed a valid row, then overwrite the records key with a non-map blob.
    harness.seedPhase("plan", "completed", 1, INVOKED_AGENT, PLAN_OUTPUT)
    harness.repository.corruptRecordsArtifact(WORKFLOW_ID, "not-a-map")

    val failure = assertFailsWith<InvalidWorkflowStateSchemaError> {
      harness.runner.run(harness.request())
    }
    assertContains(failure.message.orEmpty(), FEATURE_TASK_RUNTIME_PHASE_RECORDS_ARTIFACT_KEY)
  }
}

// ---------------------------------------------------------------------------
// Test constants + harness
// ---------------------------------------------------------------------------

private const val WORKFLOW_ID = "wftr-20260602-test-0001"
private const val SESSION_ID = "ftr-test-001"
private const val ISSUE_KEY = "SKILL-65"
private const val SPEC_REFERENCE = ".feature-specs/SKILL-65/spec.md"
private const val INVOKED_AGENT = "claude-code"
private const val VALID_OUTPUT = """{"contract_version":"0.1"}"""
private const val PLAN_OUTPUT = """{"plan":"do-the-thing"}"""
private const val IMPLEMENT_OUTPUT = """{"implement":"done"}"""

private val ALL_PHASES = listOf("plan", "implement", "review", "audit", "validate")

// F-T2: a distinct invoking agent per phase so a captured launch request is
// phase-attributable from its resolvedInvoked agent id (the resolver passes the
// per-phase agent through as the request's invokedAgentId).
private fun phaseAgent(phaseId: String): String = "agent-$phaseId"

private fun phasePerAgentAssignment(): FeatureTaskRuntimeAgentAssignment =
  FeatureTaskRuntimeAgentAssignment(perPhaseAgentIds = ALL_PHASES.associateWith(::phaseAgent))

private class RunnerHarness(
  val launcher: RuntimeRecordingLauncher,
  val recorder: FeatureTaskRuntimePhaseRecorder,
  val repository: InMemoryRuntimeWorkflowRepository,
  val runner: FeatureTaskRuntimeRunner,
  val events: MutableList<FeatureTaskRuntimeRunEvent>,
  private val runRequest: FeatureTaskRuntimeRunRequest,
) {
  // Each launch is immediately preceded by a PhaseStarted (attempt 1) or a
  // PhaseFixLoopIteration (a bounded re-run), so the launch order is recoverable
  // from the event stream without threading a phase id through the launch request.
  fun launchOrder(): List<String> = events.mapNotNull { event ->
    when (event) {
      is FeatureTaskRuntimeRunEvent.PhaseStarted -> event.phaseId
      is FeatureTaskRuntimeRunEvent.PhaseFixLoopIteration -> event.phaseId
      else -> null
    }
  }

  // F-T2: the load-bearing launch order derived from the launcher's ACTUAL
  // captured requests (requires phasePerAgentAssignment so each request's
  // invokedAgentId maps back to its phase), not from the runner's own events.
  fun launchedPhaseOrder(): List<String> = launcher.requests.map { request ->
    ALL_PHASES.firstOrNull { phaseId -> phaseAgent(phaseId) == request.invokedAgentId }
      ?: error("Launch request agent '${request.invokedAgentId}' is not phase-attributable.")
  }

  // Seeds a per-phase record for resume tests, ensuring the runtime workflow row
  // exists first (the recorder write seam is a no-op against a missing row).
  fun seedPhase(phaseId: String, status: String, attemptCount: Int, agentId: String, outputArtifact: String?) {
    recorder.ensureWorkflowOpen(WORKFLOW_ID, SESSION_ID)
    recorder.recordPhaseStateForTest(phaseId, status, attemptCount, agentId, outputArtifact)
  }

  fun request(): FeatureTaskRuntimeRunRequest = runRequest
}

private fun runnerHarness(
  launcher: RuntimeRecordingLauncher = RuntimeRecordingLauncher { facts(VALID_OUTPUT) },
  validator: FeatureTaskRuntimePhaseOutputValidator = AlwaysValidValidator,
  agentAssignment: FeatureTaskRuntimeAgentAssignment = FeatureTaskRuntimeAgentAssignment(),
  eventSink: FeatureTaskRuntimeRunEventSink? = null,
): RunnerHarness {
  val repository = InMemoryRuntimeWorkflowRepository()
  val database = RuntimeFakeDatabaseSessionFactory(repository)
  val recorder = FeatureTaskRuntimePhaseRecorder(database, NoopWorkflowSnapshotValidator)
  val runner = FeatureTaskRuntimeRunner(launcher, recorder, validator)
  // Always capture events so launch ordering and AC4 assertions are available;
  // an explicit caller-supplied sink is chained after the capture.
  val captured = mutableListOf<FeatureTaskRuntimeRunEvent>()
  val sink = FeatureTaskRuntimeRunEventSink { event ->
    captured += event
    eventSink?.emit(event)
  }
  val runRequest = FeatureTaskRuntimeRunRequest(
    issueKey = ISSUE_KEY,
    workflowId = WORKFLOW_ID,
    sessionId = SESSION_ID,
    runInvariants = FeatureTaskRuntimeRunInvariants(
      specReference = SPEC_REFERENCE,
      acceptanceCriteria = listOf("AC-1", "AC-2"),
      mandatesAndOverrides = listOf("mandate-X"),
    ),
    invokedAgentId = INVOKED_AGENT,
    agentAssignment = agentAssignment,
    environment = emptyMap(),
    dbPathOverride = null,
    repoRoot = Path.of("/tmp/repo"),
    eventSink = sink,
  )
  return RunnerHarness(launcher, recorder, repository, runner, captured, runRequest)
}

private fun facts(stdout: String): AgentRunLaunchOutcome = AgentRunLaunchFacts(
  agent = InstallAgent.CLAUDE,
  exitStatus = 0,
  stdout = stdout,
  stderr = "",
  timedOut = false,
  spawnFailed = false,
)

// F-C2: an infrastructure spawn failure (no exit status, empty stdout).
private fun spawnFailedFacts(): AgentRunLaunchOutcome = AgentRunLaunchFacts(
  agent = InstallAgent.CLAUDE,
  exitStatus = null,
  stdout = "",
  stderr = "spawn failed",
  timedOut = false,
  spawnFailed = true,
)

// F-C2: an infrastructure timeout (no exit status, partial/empty stdout).
private fun timedOutFacts(): AgentRunLaunchOutcome = AgentRunLaunchFacts(
  agent = InstallAgent.CLAUDE,
  exitStatus = null,
  stdout = "",
  stderr = "timed out",
  timedOut = true,
  spawnFailed = false,
)

private class RuntimeRecordingLauncher(
  private val handler: (GoalRunnerSubtaskLaunchRequest) -> AgentRunLaunchOutcome,
) : GoalRunnerSubtaskLauncher {
  val requests = mutableListOf<GoalRunnerSubtaskLaunchRequest>()

  override fun launch(request: GoalRunnerSubtaskLaunchRequest): AgentRunLaunchOutcome {
    requests += request
    return handler(request)
  }
}

// A schema validator that rejects only the named phases (AC3 / fix-loop tests).
private class ThrowingValidator(private val failPhases: Set<String>) : FeatureTaskRuntimePhaseOutputValidator {
  override fun validatePhaseOutputText(phaseOutputText: String, sourceLabel: String) {
    if (sourceLabel in failPhases) {
      throw InvalidFeatureTaskRuntimePhaseOutputSchemaError(sourceLabel, "rejected by fake validator")
    }
  }
}

private object AlwaysValidValidator : FeatureTaskRuntimePhaseOutputValidator {
  override fun validatePhaseOutputText(phaseOutputText: String, sourceLabel: String) = Unit
}

// The runner only drives openRecord/updateRecord (no snapshotView casts), so a
// no-op snapshot validator is sufficient for these application-layer tests.
private object NoopWorkflowSnapshotValidator : WorkflowSnapshotValidator {
  override fun validate(snapshot: Map<String, Any?>, slug: String) = Unit
}

// Records a per-phase state through the live recorder write seam, recording the
// launcher phase as a side effect so the test can assert launch ordering.
private fun FeatureTaskRuntimePhaseRecorder.recordPhaseStateForTest(
  phaseId: String,
  status: String,
  attemptCount: Int,
  resolvedAgentId: String,
  outputArtifact: String?,
): Boolean = recordPhaseState(
  skillbill.application.model.FeatureTaskRuntimePhaseStateRequest(
    workflowId = WORKFLOW_ID,
    phaseId = phaseId,
    status = status,
    attemptCount = attemptCount,
    resolvedAgentId = resolvedAgentId,
    finished = status == "completed",
    outputArtifact = outputArtifact,
  ),
)

private class RuntimeFakeDatabaseSessionFactory(
  private val repository: InMemoryRuntimeWorkflowRepository,
) : DatabaseSessionFactory {
  private val dbPath = Path.of("/fake/metrics.db")

  override fun resolveDbPath(dbOverride: String?): Path = dbPath

  override fun databaseExists(dbOverride: String?): Boolean = true

  override fun <T> read(dbOverride: String?, block: (UnitOfWork) -> T): T = block(unitOfWork())

  override fun <T> transaction(dbOverride: String?, block: (UnitOfWork) -> T): T = block(unitOfWork())

  private fun unitOfWork(): UnitOfWork = object : UnitOfWork {
    override val dbPath: Path = this@RuntimeFakeDatabaseSessionFactory.dbPath
    override val reviews: ReviewRepository get() = error("unused")
    override val learnings: LearningRepository get() = error("unused")
    override val lifecycleTelemetry: LifecycleTelemetryRepository get() = error("unused")
    override val telemetryOutbox: TelemetryOutboxRepository get() = error("unused")
    override val workflowStates: WorkflowStateRepository = repository
  }
}

private class InMemoryRuntimeWorkflowRepository : WorkflowStateRepository {
  private val taskRuntimeRows = linkedMapOf<String, WorkflowStateRecord>()

  fun taskRuntimeArtifacts(workflowId: String): Map<String, Any?> {
    val record = requireNotNull(taskRuntimeRows[workflowId]) { "no runtime row for $workflowId" }
    return skillbill.contracts.JsonSupport.parseObjectOrNull(record.artifactsJson)
      ?.let(skillbill.contracts.JsonSupport::jsonElementToValue)
      ?.let(skillbill.contracts.JsonSupport::anyToStringAnyMap)
      .orEmpty()
  }

  // F-P1: overwrite the per-phase records key with a present-but-non-map blob to
  // simulate corrupt durable state that must loud-fail on resume rather than be
  // silently read as "no records".
  fun corruptRecordsArtifact(workflowId: String, corruptValue: Any?) {
    val record = requireNotNull(taskRuntimeRows[workflowId]) { "no runtime row for $workflowId" }
    val artifacts = LinkedHashMap(taskRuntimeArtifacts(workflowId)).apply {
      put(FEATURE_TASK_RUNTIME_PHASE_RECORDS_ARTIFACT_KEY, corruptValue)
    }
    taskRuntimeRows[workflowId] = record.copy(
      artifactsJson = skillbill.contracts.JsonSupport.mapToJsonString(artifacts),
    )
  }

  override fun saveFeatureTaskRuntimeWorkflow(row: WorkflowStateRecord) {
    taskRuntimeRows[row.workflowId] = row
  }

  override fun getFeatureTaskRuntimeWorkflow(workflowId: String): WorkflowStateRecord? = taskRuntimeRows[workflowId]

  override fun listFeatureTaskRuntimeWorkflows(limit: Int): List<WorkflowStateRecord> =
    taskRuntimeRows.values.toList().asReversed().take(limit)

  override fun latestFeatureTaskRuntimeWorkflow(): WorkflowStateRecord? =
    listFeatureTaskRuntimeWorkflows(1).firstOrNull()

  override fun saveFeatureImplementWorkflow(row: WorkflowStateRecord) = Unit

  override fun saveFeatureVerifyWorkflow(row: WorkflowStateRecord) = Unit

  override fun getFeatureImplementWorkflow(workflowId: String): WorkflowStateRecord? = null

  override fun getFeatureVerifyWorkflow(workflowId: String): WorkflowStateRecord? = null

  override fun listFeatureImplementWorkflows(limit: Int): List<WorkflowStateRecord> = emptyList()

  override fun listFeatureVerifyWorkflows(limit: Int): List<WorkflowStateRecord> = emptyList()

  override fun latestFeatureImplementWorkflow(): WorkflowStateRecord? = null

  override fun latestFeatureVerifyWorkflow(): WorkflowStateRecord? = null

  override fun getFeatureImplementSessionSummary(sessionId: String): FeatureImplementSessionSummary? = null

  override fun getFeatureVerifySessionSummary(sessionId: String): FeatureVerifySessionSummary? = null
}
