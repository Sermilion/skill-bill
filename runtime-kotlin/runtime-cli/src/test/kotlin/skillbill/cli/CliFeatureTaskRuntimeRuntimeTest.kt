package skillbill.cli

import skillbill.install.model.InstallAgent
import skillbill.ports.agentrun.AgentRunLauncher
import skillbill.ports.agentrun.model.AgentRunLaunchFacts
import skillbill.ports.agentrun.model.AgentRunLaunchOutcome
import skillbill.ports.agentrun.model.AgentRunLaunchRequest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 * SKILL-65 Subtask 4 (AC1, AC2): CLI command coverage for the experimental
 * `feature-task-runtime` group. Mirrors `CliGoalRuntimeTest`: it asserts the
 * `--help` registration of run/status/resume, option validation, the
 * agent-default resolution order, and delegation to the application runner +
 * status service via in-test doubles (no real agent process).
 */
class CliFeatureTaskRuntimeRuntimeTest {
  @Test
  fun `feature-task-runtime command registers run status and resume`() {
    val help = CliRuntime.run(listOf("feature-task-runtime", "--help"), CliRuntimeContext())

    assertEquals(0, help.exitCode, help.stdout)
    assertContains(help.stdout, "EXPERIMENTAL")
    assertContains(help.stdout, "status")
    assertContains(help.stdout, "resume")
    assertContains(help.stdout, "--phase-agent")
    assertContains(help.stdout, "--agent-override")
    assertContains(help.stdout, "--monitor")
    assertContains(help.stdout, "--max-wall-clock-minutes")
  }

  @Test
  fun `feature-task-runtime run requires issue key and spec path`() {
    val missingArgs = CliRuntime.run(listOf("feature-task-runtime"), CliRuntimeContext())
    assertEquals(1, missingArgs.exitCode, missingArgs.stdout)
    assertContains(missingArgs.stdout, "issue_key is required")

    val fixture = runtimeFixture()
    val missingSpec = CliRuntime.run(
      listOf("--db", fixture.dbPath.toString(), "feature-task-runtime", "SKILL-650"),
      fixture.context(RecordingPhaseLauncher()),
    )
    assertEquals(1, missingSpec.exitCode, missingSpec.stdout)
    assertContains(missingSpec.stdout, "spec_path is required")
  }

  @Test
  fun `feature-task-runtime run completes every phase and delegates to the runner`() {
    val fixture = runtimeFixture()
    val launcher = RecordingPhaseLauncher()

    val result = CliRuntime.run(
      fixture.runCommand(extra = listOf("--agent", "codex")),
      fixture.context(launcher),
    )

    assertEquals(0, result.exitCode, result.stdout)
    assertContains(result.stdout, "status: complete")
    assertContains(result.stdout, "completed_phases: plan, implement, review, audit, validate")
    // The runner launched exactly one agent per ordered phase, in order.
    assertEquals(listOf("codex"), launcher.requests.map { it.agentId }.distinct())
    assertEquals(5, launcher.requests.size)
  }

  @Test
  fun `feature-task-runtime run defaults invoked agent to detected invoking context`() {
    val fixture = runtimeFixture()
    val launcher = RecordingPhaseLauncher()

    val result = CliRuntime.run(
      fixture.runCommand(),
      fixture.context(launcher, environment = mapOf("CLAUDECODE" to "1")),
    )

    assertEquals(0, result.exitCode, result.stdout)
    // No --agent / SKILL_BILL_AGENT: detection must resolve claude.
    assertEquals(listOf("claude"), launcher.requests.map { it.agentId }.distinct())
  }

  @Test
  fun `feature-task-runtime run SKILL_BILL_AGENT wins over detected invoking context`() {
    val fixture = runtimeFixture()
    val launcher = RecordingPhaseLauncher()

    val result = CliRuntime.run(
      // No --agent: SKILL_BILL_AGENT must win over the CLAUDECODE detection marker.
      fixture.runCommand(),
      fixture.context(launcher, environment = mapOf("CLAUDECODE" to "1", "SKILL_BILL_AGENT" to "opencode")),
    )

    assertEquals(0, result.exitCode, result.stdout)
    assertEquals(listOf("opencode"), launcher.requests.map { it.agentId }.distinct())
  }

  @Test
  fun `feature-task-runtime run falls back to the documented codex default when nothing resolves`() {
    val fixture = runtimeFixture()
    val launcher = RecordingPhaseLauncher()

    val result = CliRuntime.run(
      // No --agent, no SKILL_BILL_AGENT, no detectable invoking-agent marker:
      // the documented last-resort default codex must be used.
      fixture.runCommand(),
      fixture.context(launcher, environment = emptyMap()),
    )

    assertEquals(0, result.exitCode, result.stdout)
    assertEquals(listOf("codex"), launcher.requests.map { it.agentId }.distinct())
  }

  @Test
  fun `feature-task-runtime run agent-override wins over invoking agent`() {
    val fixture = runtimeFixture()
    val launcher = RecordingPhaseLauncher()

    val result = CliRuntime.run(
      fixture.runCommand(extra = listOf("--agent", "codex", "--agent-override", "claude")),
      fixture.context(launcher),
    )

    assertEquals(0, result.exitCode, result.stdout)
    assertEquals(listOf("claude"), launcher.requests.map { it.agentId }.distinct())
  }

  @Test
  fun `feature-task-runtime run routes a per-phase agent for only that phase`() {
    val fixture = runtimeFixture()
    val launcher = RecordingPhaseLauncher()

    val result = CliRuntime.run(
      // Default agent codex for every phase, but the plan phase is pinned to claude.
      fixture.runCommand(extra = listOf("--agent", "codex", "--phase-agent", "plan=claude")),
      fixture.context(launcher),
    )

    assertEquals(0, result.exitCode, result.stdout)
    // The runner launches the ordered phases one-per-launch, so launch index maps
    // to phase id (plan, implement, review, audit, validate).
    val orderedPhases = listOf("plan", "implement", "review", "audit", "validate")
    val agentByPhase = orderedPhases.mapIndexed { index, phaseId ->
      phaseId to launcher.requests[index].agentId
    }.toMap()
    assertEquals(5, launcher.requests.size, result.stdout)
    // plan diverges to its assigned agent; every other phase keeps the default.
    assertEquals("claude", agentByPhase["plan"], result.stdout)
    assertEquals(
      listOf("codex", "codex", "codex", "codex"),
      orderedPhases.drop(1).map { agentByPhase.getValue(it) },
      result.stdout,
    )
  }

  @Test
  fun `feature-task-runtime run rejects a malformed per-phase agent assignment`() {
    val fixture = runtimeFixture()

    val result = CliRuntime.run(
      fixture.runCommand(extra = listOf("--agent", "codex", "--phase-agent", "plan")),
      fixture.context(RecordingPhaseLauncher()),
    )

    assertEquals(1, result.exitCode, result.stdout)
    assertContains(result.stdout, "--phase-agent must be phase=agent")
  }

  @Test
  fun `feature-task-runtime run rejects an unknown per-phase agent phase`() {
    val fixture = runtimeFixture()

    val result = CliRuntime.run(
      fixture.runCommand(extra = listOf("--agent", "codex", "--phase-agent", "bogus=claude")),
      fixture.context(RecordingPhaseLauncher()),
    )

    assertEquals(1, result.exitCode, result.stdout)
    assertContains(result.stdout, "is not a runtime phase")
  }

  @Test
  fun `feature-task-runtime run rejects a non-positive max wall-clock minutes at the CLI boundary`() {
    val fixture = runtimeFixture()
    val launcher = RecordingPhaseLauncher()

    val result = CliRuntime.run(
      fixture.runCommand(extra = listOf("--agent", "codex", "--max-wall-clock-minutes", "0")),
      fixture.context(launcher),
    )

    // The non-positive timeout is rejected as a clean usage error BEFORE any phase
    // is launched or any durable workflow row is opened.
    assertEquals(1, result.exitCode, result.stdout)
    assertEquals(emptyList(), launcher.requests, result.stdout)
  }

  @Test
  fun `feature-task-runtime status reports per-phase projection after a completed run`() {
    val fixture = runtimeFixture()
    val launcher = RecordingPhaseLauncher()
    val run = CliRuntime.run(fixture.runCommand(extra = listOf("--agent", "codex")), fixture.context(launcher))
    assertEquals(0, run.exitCode, run.stdout)
    val workflowId = run.stdout.lines().single { it.startsWith("workflow_id:") }.substringAfter(":").trim()

    val status = CliRuntime.run(
      listOf("--db", fixture.dbPath.toString(), "feature-task-runtime", "status", workflowId),
      fixture.context(launcher),
    )

    assertEquals(0, status.exitCode, status.stdout)
    assertContains(status.stdout, "status: ok")
    assertContains(status.stdout, "complete: 5")
    assertContains(status.stdout, "pending: 0")
    assertContains(status.stdout, "blocked: 0")
    assertContains(status.stdout, "current_phase: none")
    assertContains(status.stdout, "phase: id=plan status=completed")
  }

  @Test
  fun `feature-task-runtime status reports not_found for an unknown workflow id`() {
    val fixture = runtimeFixture()

    val status = CliRuntime.run(
      listOf("--db", fixture.dbPath.toString(), "feature-task-runtime", "status", "wftr-missing"),
      fixture.context(RecordingPhaseLauncher()),
    )

    assertEquals(1, status.exitCode, status.stdout)
    assertContains(status.stdout, "status: not_found")
  }

  @Test
  fun `feature-task-runtime status reports a blocked phase derived from the ledger`() {
    val fixture = runtimeFixture()
    // Plan completes (launch 0 valid); implement never validates and blocks after
    // the bounded fix loop, recording only an append-only ledger BLOCKED entry.
    val launcher = RecordingPhaseLauncher(invalidFromLaunchIndex = 1)
    val run = CliRuntime.run(fixture.runCommand(extra = listOf("--agent", "codex")), fixture.context(launcher))
    assertEquals(1, run.exitCode, run.stdout)
    assertContains(run.stdout, "status: blocked")
    val workflowId = run.stdout.lines().single { it.startsWith("workflow_id:") }.substringAfter(":").trim()

    val status = CliRuntime.run(
      listOf("--db", fixture.dbPath.toString(), "feature-task-runtime", "status", workflowId),
      fixture.context(launcher),
    )

    assertEquals(0, status.exitCode, status.stdout)
    assertContains(status.stdout, "status: ok")
    assertContains(status.stdout, "complete: 1")
    assertContains(status.stdout, "blocked: 1")
    // The blocked phase is the current phase, not silently reported as running.
    assertContains(status.stdout, "current_phase: implement")
    assertContains(status.stdout, "phase: id=plan status=completed")
    assertContains(status.stdout, "phase: id=implement status=blocked")
  }

  @Test
  fun `feature-task-runtime resume re-runs against an existing workflow id without re-launching complete phases`() {
    val fixture = runtimeFixture()
    val launcher = RecordingPhaseLauncher()
    val run = CliRuntime.run(fixture.runCommand(extra = listOf("--agent", "codex")), fixture.context(launcher))
    assertEquals(0, run.exitCode, run.stdout)
    val workflowId = run.stdout.lines().single { it.startsWith("workflow_id:") }.substringAfter(":").trim()
    val launchesAfterRun = launcher.requests.size

    val resume = CliRuntime.run(
      listOf(
        "--db",
        fixture.dbPath.toString(),
        "feature-task-runtime",
        "resume",
        workflowId,
        "SKILL-650",
        fixture.specPath.toString(),
        "--agent",
        "codex",
      ),
      fixture.context(launcher),
    )

    assertEquals(0, resume.exitCode, resume.stdout)
    assertContains(resume.stdout, "status: complete")
    // Every phase was already complete; resume must not launch any agent again.
    assertEquals(launchesAfterRun, launcher.requests.size)
  }
}

private data class FeatureTaskRuntimeCliFixture(
  val tempDir: Path,
  val dbPath: Path,
  val specPath: Path,
) {
  fun context(launcher: AgentRunLauncher, environment: Map<String, String> = emptyMap()): CliRuntimeContext =
    CliRuntimeContext(
      userHome = tempDir,
      agentRunLauncher = launcher,
      environment = environment,
    )

  fun runCommand(extra: List<String> = emptyList()): List<String> = buildList {
    add("--db")
    add(dbPath.toString())
    add("feature-task-runtime")
    add("SKILL-650")
    add(specPath.toString())
    add("--repo-root")
    add(tempDir.toString())
    addAll(extra)
  }
}

private fun runtimeFixture(): FeatureTaskRuntimeCliFixture {
  val tempDir = Files.createTempDirectory("skillbill-cli-feature-task-runtime")
  val specPath = tempDir.resolve(".feature-specs/SKILL-650-runtime/spec.md")
  Files.createDirectories(specPath.parent)
  Files.writeString(
    specPath,
    """
    # SKILL-650 runtime spec

    ## Acceptance Criteria

    1. The runtime drives every ordered phase to a validated output.
    2. The CLI delegates to the application runner without owning orchestration.

    ## Mandates and Overrides

    - Stay on the experimental path only when explicitly requested.
    """.trimIndent(),
  )
  return FeatureTaskRuntimeCliFixture(
    tempDir = tempDir,
    dbPath = tempDir.resolve("metrics.db"),
    specPath = specPath,
  )
}

// A stateful fake that returns one schema-valid phase output per launch. The
// phase-output schema constrains shape (contract version, status, summary,
// produced_outputs) but not the phase id, so a single well-formed payload
// validates for every ordered phase, letting the happy-path run complete.
//
// F-009: the runner launches the ordered runtime phases deterministically
// (plan -> implement -> review -> audit -> validate), one launch per phase, so the
// Nth launch corresponds to the Nth ordered phase. The launcher echoes THAT phase
// id back into the emitted payload (instead of a constant "plan") only so the
// fixture is self-describing per launch; it does NOT add wrong-phase-output
// regression protection, because the runner labels validation with its own
// phaseId and never parses or cross-checks the agent-supplied phase_id (the
// canonical phase-output schema constrains phase_id only as a non-empty string).
// Phase ordering and the one-launch-per-phase invariant are asserted directly by
// the happy-path test (completed_phases order + requests.size == 5).
// AgentRunLaunchRequest carries no phase id, so launch order is the only
// deterministic source the test double has.
//
// F-008: when [invalidFromLaunchIndex] is set, every launch at or after that index
// returns a schema-INVALID payload. With index 1, the plan phase (launch 0)
// completes and the implement phase (launch 1+) never validates, so the runner
// exhausts its bounded fix loop and blocks at implement — exercising the
// ledger-derived blocked status projection (F-001).
private class RecordingPhaseLauncher(
  private val invalidFromLaunchIndex: Int? = null,
) : AgentRunLauncher {
  val requests: MutableList<AgentRunLaunchRequest> = mutableListOf()

  override fun launch(request: AgentRunLaunchRequest): AgentRunLaunchOutcome {
    val launchIndex = requests.size
    val phaseId = ORDERED_PHASES.getOrElse(launchIndex) { "plan" }
    requests += request
    val invalid = invalidFromLaunchIndex?.let { launchIndex >= it } ?: false
    return AgentRunLaunchFacts(
      agent = InstallAgent.fromNormalizedId(request.agentId, label = "agentId"),
      exitStatus = 0,
      stdout = if (invalid) INVALID_PHASE_OUTPUT else validPhaseOutput(phaseId),
      stderr = "",
      timedOut = false,
      spawnFailed = false,
    )
  }

  private companion object {
    val ORDERED_PHASES = listOf("plan", "implement", "review", "audit", "validate")

    // Missing the required status/summary/produced_outputs fields, so the per-phase
    // output validator rejects it and the runner never marks the phase complete.
    val INVALID_PHASE_OUTPUT =
      """
      contract_version: "0.1"
      phase_id: "implement"
      """.trimIndent()

    fun validPhaseOutput(phaseId: String): String = """
      contract_version: "0.1"
      phase_id: "$phaseId"
      status: "completed"
      summary: "Phase produced a validated output."
      produced_outputs:
        tasks: ["task-1"]
    """.trimIndent()
  }
}
