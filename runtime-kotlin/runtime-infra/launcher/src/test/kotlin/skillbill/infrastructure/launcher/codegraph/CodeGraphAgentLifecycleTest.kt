package skillbill.infrastructure.launcher.codegraph

import org.junit.jupiter.api.io.TempDir
import skillbill.application.agentrun.AgentRunGoalRunnerSubtaskLauncher
import skillbill.application.agentrun.AgentRunService
import skillbill.application.agentrun.model.AgentRunStartRequest
import skillbill.infrastructure.launcher.agentrun.FileSystemAgentRunLauncher
import skillbill.infrastructure.launcher.launcher.goalContinuationContext
import skillbill.infrastructure.launcher.process.launch.AgentRunProcessRequest
import skillbill.infrastructure.launcher.process.launch.AgentRunProcessResult
import skillbill.infrastructure.launcher.process.launch.AgentRunProcessRunner
import skillbill.ports.agentrun.ExecutableLookup
import skillbill.ports.agentrun.model.AgentRunLaunchFacts
import skillbill.ports.agentrun.model.AgentRunLaunchRequest
import skillbill.ports.agentrun.model.SkillRunRequest
import skillbill.ports.codegraph.model.CodeGraphCommandResult
import skillbill.ports.codegraph.model.CodeGraphSessionPreparation
import skillbill.ports.goalrunner.runner.model.GoalRunnerSubtaskLaunchRequest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CodeGraphAgentLifecycleTest {
  @TempDir lateinit var root: Path

  @Test
  fun `default launcher attempts CodeGraph without a session override or opt in`() {
    val lookups = mutableListOf<String>()
    val launcher = FileSystemAgentRunLauncher(
      processRunner = object : AgentRunProcessRunner {
        override fun run(request: AgentRunProcessRequest): AgentRunProcessResult {
          assertEquals("briefing unchanged", request.stdinText)
          return result("exited")
        }
      },
      executableLookup = ExecutableLookup { executable ->
        lookups += executable
        executable != "codegraph"
      },
    )
    assertEquals(0, assertIs<AgentRunLaunchFacts>(launcher.launch(request(root))).exitStatus)
    assertContains(lookups, "codegraph")
    val records = Files.list(root.resolve(".skill-bill/runtime/codegraph-sessions")).use { paths ->
      paths.filter { Files.isRegularFile(it) }.map { Files.readString(it) }.toList()
    }
    assertEquals(1, records.size)
    assertContains(records.single(), "missing_cli")
  }

  @Test
  fun `normal failed timeout and interrupted child outcomes close the server and persist terminal state`() {
    listOf("exited", "failed", "timed_out", "cancelled").forEach { outcome ->
      val fixture = CodeGraphBoundaryFixture(Files.createDirectory(root.resolve(outcome))).apply { prepared() }
      val file = Files.writeString(fixture.root.resolve("source.kt"), "ordinary source")
      val launcher = launcher(fixture) { request ->
        assertEquals("briefing unchanged", request.stdinText)
        assertEquals("ordinary source", Files.readString(file))
        assertEquals(1, fixture.starts)
        assertTrue(fixture.process.alive)
        result(outcome)
      }
      val facts = assertIs<AgentRunLaunchFacts>(launcher.launch(request(fixture.root)))
      assertEquals("child response", facts.stdout)
      assertFalse(fixture.process.alive)
      assertEquals(1, fixture.process.closes)
      assertContains(fixture.persisted(), "\"lifecycle_state\":\"$outcome\"")
      assertContains(fixture.persisted(), "\"cleanup_attempted\":true")
    }
  }

  @Test
  fun `thrown cancellation and child failure retain primary exceptions when cleanup also fails`() {
    listOf(
      CancellationException("private cancellation"),
      IllegalStateException("private failure"),
    ).forEachIndexed { index, failure ->
      val fixture = CodeGraphBoundaryFixture(Files.createDirectory(root.resolve("case-$index"))).apply {
        prepared()
        process.failCleanup = true
      }
      val launcher = launcher(fixture) { throw failure }
      val thrown = assertFailsWith<Exception> { launcher.launch(request(fixture.root)) }
      assertSame(failure, thrown)
      assertFalse(fixture.process.alive)
      assertEquals(1, fixture.process.closes)
      assertContains(fixture.persisted(), "cleanup_failed")
      assertContains(fixture.persisted(), if (failure is CancellationException) "cancelled" else "failed")
      assertFalse(fixture.persisted().contains("private"))
    }
  }

  @Test
  fun `server crash is cleaned up while the child continues using ordinary files`() {
    val fixture = CodeGraphBoundaryFixture(root).apply { prepared() }
    val lease = assertIs<CodeGraphSessionPreparation.Ready>(fixture.session.prepare(fixture.request())).lease
    try {
      fixture.process.alive = false
      val deadline = System.nanoTime() + 3_000_000_000L
      while (!lease.activeObservation.cleanupAttempted && System.nanoTime() < deadline) Thread.sleep(10)
      assertTrue(lease.activeObservation.cleanupAttempted)
      assertEquals(1, fixture.process.closes)
      assertContains(fixture.persisted(), "query_failure")
      assertContains(fixture.persisted(), "failed")
      assertTrue(Files.exists(lease.launchConfiguration.mcpConfigPath))
      val file = Files.writeString(root.resolve("fallback.kt"), "current source")
      assertEquals("current source", Files.readString(file))
    } finally {
      lease.close()
    }
    assertFalse(Files.exists(lease.launchConfiguration.mcpConfigPath))
    assertEquals(1, fixture.process.closes)
  }

  @Test
  fun `setup and MCP failures preserve prompt ordinary files and successful child exit`() {
    listOf("missing", "initialization", "mcp").forEach { failure ->
      val fixture = CodeGraphBoundaryFixture(Files.createDirectory(root.resolve(failure))).apply {
        if (failure == "missing") available = false
        if (failure == "initialization") initialization = CodeGraphCommandResult(1, "", "failed")
        if (failure == "mcp") startFailure = IllegalStateException("private error")
      }
      val file = Files.writeString(fixture.root.resolve("ordinary.txt"), "usable")
      val facts = launcher(fixture) { command ->
        assertEquals("briefing unchanged", command.stdinText)
        assertFalse(command.command.contains("--mcp-config"))
        assertEquals("usable", Files.readString(file))
        result("exited")
      }.launch(request(fixture.root))
      assertEquals(0, assertIs<AgentRunLaunchFacts>(facts).exitStatus)
      assertContains(fixture.persisted(), "fallback")
    }
  }

  @Test
  fun `goal continuation shell does not prepare CodeGraph or receive provider configuration flags`() {
    val fixture = CodeGraphBoundaryFixture(root)
    launcher(fixture) { command ->
      assertEquals("skill-bill", command.command.first())
      assertFalse(command.command.contains("--mcp-config"))
      assertFalse(command.command.contains("--config"))
      result("exited")
    }.launch(
      request(
        root,
      ).copy(skillRunRequest = SkillRunRequest("SKILL-368", root, goalContinuation = goalContinuationContext())),
    )
    assertEquals(0, fixture.availabilityChecks)
    assertEquals(0, fixture.starts)
    assertTrue(fixture.commands.isEmpty())
  }

  @Test
  fun `goal planning feature phase and delegated skill requests use the same automatic session`() {
    listOf("goal planning", "feature phase", "delegated skill").forEach { task ->
      val fixture = CodeGraphBoundaryFixture(
        Files.createDirectory(root.resolve(task.replace(' ', '-'))),
      ).apply { prepared() }
      val service = AgentRunService(
        launcher(fixture) { command ->
          assertEquals(task, command.stdinText)
          assertTrue(fixture.process.alive)
          result("exited")
        },
      )
      val request = SkillRunRequest("SKILL-368", fixture.root, promptOverride = task)
      val outcome = if (task == "delegated skill") {
        service.launch(AgentRunStartRequest("claude", null, request)).launchOutcome
      } else {
        AgentRunGoalRunnerSubtaskLauncher(service).launch(GoalRunnerSubtaskLaunchRequest("claude", null, request))
      }
      assertEquals("child response", assertIs<AgentRunLaunchFacts>(outcome).stdout)
      assertEquals(1, fixture.availabilityChecks)
      assertEquals(1, fixture.starts)
      assertEquals(1, fixture.process.closes)
      assertContains(fixture.persisted(), "exited")
    }
  }

  private fun launcher(fixture: CodeGraphBoundaryFixture, run: (AgentRunProcessRequest) -> AgentRunProcessResult) =
    FileSystemAgentRunLauncher(
      processRunner = object : AgentRunProcessRunner {
        override fun run(request: AgentRunProcessRequest) = run.invoke(request)
      },
      executableLookup = object : ExecutableLookup {
        override fun onPath(executable: String) = true
      },
      codeGraphSession = fixture.session,
    )

  private fun request(directory: Path) = AgentRunLaunchRequest(
    "claude",
    SkillRunRequest("SKILL-368", directory, promptOverride = "briefing unchanged"),
  )

  private fun result(outcome: String) = AgentRunProcessResult(
    exitStatus = when (outcome) {
      "exited" -> 0
      "timed_out", "cancelled" -> null
      else -> 1
    },
    stdout = "child response",
    stderr = "",
    timedOut = outcome == "timed_out",
    interrupted = outcome == "cancelled",
    spawnFailed = false,
  )
}
