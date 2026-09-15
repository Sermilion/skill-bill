package skillbill.infrastructure.fs.launcher.process

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import skillbill.contracts.time.JvmSystemClock
import skillbill.infrastructure.fs.jvm.hostPath
import skillbill.infrastructure.fs.jvm.testGateJvmResolver
import skillbill.infrastructure.fs.launcher.review.GovernedReviewEvidenceEndpoint
import skillbill.infrastructure.fs.launcher.testAgentRunProcessRequest
import skillbill.ports.agentrun.model.AgentRunMcpStartupProbe
import skillbill.ports.agentrun.model.AgentRunOutputSink
import skillbill.ports.agentrun.model.AgentRunOutputStream
import skillbill.ports.agentrun.model.AgentRunProgressProbe
import skillbill.ports.agentrun.model.AgentRunSpawnAuthorization
import skillbill.ports.agentrun.model.ConversationIsolation
import skillbill.ports.review.BrokerBackedNativeReviewOperationProtocol
import skillbill.ports.review.ReviewEvidenceBroker
import skillbill.ports.review.model.ReviewEvidenceBatchRequest
import skillbill.ports.review.model.ReviewLaneAccounting
import skillbill.ports.review.model.ReviewToolCall
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class JvmAgentRunProcessRunnerTest {
  private val trackedChildPids = mutableListOf<Long>()
  private val trackedChildPidFiles = mutableListOf<Path>()

  @AfterEach
  fun destroyTrackedChildren() {
    trackedChildPids.forEach { pid ->
      runCatching {
        ProcessHandle.of(pid).ifPresent { handle ->
          if (handle.isAlive) {
            handle.destroyForcibly()
          }
        }
      }
    }
    trackedChildPids.clear()
    trackedChildPidFiles.forEach { pidFile ->
      runCatching { Files.readString(pidFile).trim().toLong() }.getOrNull()?.let { pid ->
        ProcessHandle.of(pid).ifPresent { handle ->
          if (handle.isAlive) {
            handle.destroyForcibly()
          }
        }
      }
      runCatching { Files.deleteIfExists(pidFile) }
    }
    trackedChildPidFiles.clear()
  }

  @Test
  fun `throwing output sink still reaps a live child process`() {
    val pidFile = Files.createTempFile("skillbill-child", ".pid")
    trackedChildPidFiles.add(pidFile)
    val sink = AgentRunOutputSink { stream, _ ->
      if (stream == AgentRunOutputStream.STDERR) {
        error("output sink failed")
      }
    }
    assertThrows<IllegalStateException> {
      JvmAgentRunProcessRunner(JvmSystemClock, testGateJvmResolver()).run(
        testAgentRunProcessRequest(
          listOf("sh", "-c", "echo $$ > '$pidFile'; exec sleep 120"),
          Path.of("."),
        ) {
          outputSink = sink
          timeout = 30.seconds
          statusHeartbeatInterval = 1.milliseconds
        },
      )
    }
    val pid = Files.readString(pidFile).trim().toLong()
    trackedChildPids.add(pid)
    Files.deleteIfExists(pidFile)
    assertFalse(childProcessAlive(pid), "F-001: child must not outlive runner cleanup")
  }

  @Test
  fun `probe failure is recorded without resetting the idle deadline`() {
    val result = JvmAgentRunProcessRunner(JvmSystemClock, testGateJvmResolver()).run(
      testAgentRunProcessRequest(
        listOf("sh", "-c", "sleep 5"),
        Path.of("."),
      ) {
        timeout = 2.seconds
        progressIdleTimeout = 100.milliseconds
        progressProbe = AgentRunProgressProbe { error("probe failed") }
      },
    )

    assertTrue(result.timedOut)
    assertTrue(result.stderr.contains("probe_failure"))
    assertTrue(result.stderr.contains("progress_token"))
  }

  @Test
  fun `progress absence after an observation does not extend the idle deadline`() {
    var observations = 0
    val result = JvmAgentRunProcessRunner(JvmSystemClock, testGateJvmResolver()).run(
      testAgentRunProcessRequest(
        listOf("sh", "-c", "sleep 5"),
        Path.of("."),
      ) {
        timeout = 2.seconds
        progressIdleTimeout = 100.milliseconds
        progressProbe = AgentRunProgressProbe {
          if (observations++ == 0) "observed" else null
        }
      },
    )

    assertTrue(result.timedOut)
    assertTrue(result.stderr.contains("probe_absence"))
    assertTrue(result.stderr.contains("progress_token"))
  }

  @Test
  fun `stdin delivery failure is reported without using the output sink`() {
    val recorder = ProcessRunDegradationRecorder()

    writeAndCloseStdin(FakeReapableProcess(staysAlive = false), "input", recorder)

    val diagnostic = recorder.appendToStderr("")
    assertTrue(diagnostic.contains("stdin_delivery_failure"))
    assertTrue(diagnostic.contains("stdin"))
  }

  @Test
  fun `cancellation from a probe is not converted into missing progress`() {
    assertThrows<CancellationException> {
      JvmAgentRunProcessRunner(JvmSystemClock, testGateJvmResolver()).run(
        testAgentRunProcessRequest(
          listOf("sh", "-c", "exec sleep 120"),
          Path.of("."),
        ) {
          progressProbe = AgentRunProgressProbe { throw CancellationException("cancelled") }
        },
      )
    }
  }

  @Test
  fun `drain join timeout reports incomplete capture instead of settled evidence`() {
    val blocking = BlockingInputStream()
    val drain = CappedUtf8Drain(
      input = blocking,
      limitBytes = AGENT_RUN_OUTPUT_LIMIT_BYTES,
      outputStream = AgentRunOutputStream.STDOUT,
      outputSink = AgentRunOutputSink.NONE,
      onChunkRead = {},
    )
    drain.start()
    val incomplete = drain.joinAndFreeze()
    assertTrue(incomplete)
    val capture = drain.capture()
    assertTrue(capture.incomplete)
    val firstDigest = capture.sha256
    blocking.unblock()
    drain.joinAndFreeze()
    assertEquals(firstDigest, drain.capture().sha256)
  }

  @Test
  fun `interrupt during drain settlement remains set after release`() {
    val stdoutInput = BlockingInputStream()
    val stderrInput = BlockingInputStream()
    val stdout = testDrain(stdoutInput)
    val stderr = testDrain(stderrInput)
    stdout.start()
    stderr.start()
    val process = FakeReapableProcess(staysAlive = false)
    val lifetime = ProcessRunLifetime(
      process = process,
      liveProcesses = mutableSetOf(),
      stdout = stdout,
      stderr = stderr,
      degradation = ProcessRunDegradationRecorder(),
    )
    val releaseThread = thread(start = true) {
      lifetime.release(
        Result.success(
          ProcessWait(
            finished = true,
            progressIdleTimedOut = false,
            fileActivityGraceExhausted = false,
            wallClockTimedOut = false,
          ),
        ),
      )
    }
    assertTrue(stdoutInput.awaitReadStarted())
    releaseThread.interrupt()
    releaseThread.join(5_000)
    stdoutInput.unblock()
    stderrInput.unblock()
    stdout.join()
    stderr.join()

    assertFalse(releaseThread.isAlive)
    assertTrue(releaseThread.isInterrupted)
  }

  @Test
  fun `parent interrupt during wait keeps interrupted result without idle timeout`() {
    val runner = JvmAgentRunProcessRunner(JvmSystemClock, testGateJvmResolver())
    var result: AgentRunProcessResult? = null
    val worker = thread(start = true) {
      result = runner.run(
        testAgentRunProcessRequest(
          listOf("sh", "-c", "sleep 120"),
          Path.of(".").toAbsolutePath().normalize(),
        ) {
          timeout = 120.seconds
          progressIdleTimeout = 120.seconds
        },
      )
    }
    Thread.sleep(200)
    worker.interrupt()
    worker.join(10_000)
    assertFalse(worker.isAlive)
    val completed = requireNotNull(result)
    assertTrue(completed.interrupted)
    assertFalse(completed.timedOut)
    assertEquals("parent_interrupted", completed.liveness?.reason)
  }

  @Test
  fun `an over-cap stream retains its terminal event instead of its preamble`() {
    val flood =
      """awk 'BEGIN{p=sprintf("%0500d",0); """ +
        """for(i=0;i<4000;i++) printf "{\"type\":\"assistant\",\"pad\":\"%s\"}\n", p; """ +
        """printf "{\"type\":\"result\",\"result\":\"TERMINAL\"}\n"}'"""
    val result = JvmAgentRunProcessRunner(JvmSystemClock, testGateJvmResolver()).run(
      testAgentRunProcessRequest(
        listOf("sh", "-c", flood),
        Path.of("."),
      ),
    )

    assertEquals(0, result.exitStatus)
    assertTrue(result.stdoutTruncated, "the flood must exceed the retention cap for this to prove anything")
    assertTrue(
      result.stdout.trimEnd().endsWith("""{"type":"result","result":"TERMINAL"}"""),
      "the terminal event is the only harvestable one; retaining the head would discard it",
    )
    assertTrue(
      result.stdout.startsWith("{"),
      "retention must resume at a record boundary so a line-oriented decoder can parse the tail",
    )
    assertTrue(
      result.stdoutByteSize > result.stdoutBytes.size,
      "the observed total stays the full stream even though only the tail is retained",
    )
  }

  @Test
  fun `foreground process result is returned once as the bounded terminal result`() {
    val result = JvmAgentRunProcessRunner(JvmSystemClock, testGateJvmResolver()).run(
      testAgentRunProcessRequest(
        listOf("sh", "-c", "printf terminal-result"),
        Path.of("."),
      ),
    )

    assertEquals(0, result.exitStatus)
    assertEquals("terminal-result", result.stdout)
    assertEquals(false, result.timedOut)
    assertEquals(false, result.interrupted)
    assertEquals(false, result.spawnFailed)
    assertEquals(true, result.processStarted)
  }

  @Test
  fun `nonzero child exit remains an ordinary completed process result`() {
    val result = JvmAgentRunProcessRunner(JvmSystemClock, testGateJvmResolver()).run(
      testAgentRunProcessRequest(
        listOf("sh", "-c", "printf failure >&2; exit 17"),
        Path.of("."),
      ),
    )

    assertEquals(17, result.exitStatus)
    assertFalse(result.timedOut)
    assertFalse(result.spawnFailed)
    assertTrue(result.stderr.contains("failure"))
  }

  @Test
  fun `spawn refusal remains distinct from a started process`() {
    val result = JvmAgentRunProcessRunner(JvmSystemClock, testGateJvmResolver()).run(
      testAgentRunProcessRequest(
        listOf("/skillbill/does-not-exist"),
        Path.of("."),
      ),
    )

    assertTrue(result.spawnFailed)
    assertFalse(result.processStarted)
    assertNull(result.exitStatus)
  }

  @Test
  fun `MCP startup is counted only when an explicit launcher probe observes it`() {
    val result = JvmAgentRunProcessRunner(JvmSystemClock, testGateJvmResolver()).run(
      testAgentRunProcessRequest(
        listOf("sh", "-c", "printf terminal-result"),
        Path.of("."),
      ) {
        mcpStartupProbe = AgentRunMcpStartupProbe { true }
      },
    )

    assertTrue(result.mcpStartupObserved)
  }

  @Test
  fun `spawn authorization surrounds process creation and not terminal waiting`() {
    var authorizationEntered = false
    var authorizationExited = false
    val result = JvmAgentRunProcessRunner(JvmSystemClock, testGateJvmResolver()).run(
      testAgentRunProcessRequest(
        listOf("sh", "-c", "printf terminal-result"),
        Path.of("."),
      ) {
        spawnAuthorization = object : AgentRunSpawnAuthorization {
          override fun <T> withAuthorization(spawn: () -> T): T {
            authorizationEntered = true
            return spawn().also { authorizationExited = true }
          }
        }
      },
    )

    assertEquals(0, result.exitStatus)
    assertEquals("terminal-result", result.stdout)
    assertEquals(true, authorizationEntered)
    assertEquals(true, authorizationExited)
  }

  @Test
  fun `reap destroys a process that exits and does not forcibly kill it`() {
    val process = FakeReapableProcess(staysAlive = false)

    JvmAgentRunProcessRunner.reapLiveProcesses(listOf(process))

    assertEquals(1, process.destroyCount)
    assertEquals(0, process.forcibleCount)
  }

  @Test
  fun `reap forcibly kills a process that stays alive after destroy`() {
    val process = FakeReapableProcess(staysAlive = true)

    JvmAgentRunProcessRunner.reapLiveProcesses(listOf(process))

    assertEquals(1, process.destroyCount)
    assertEquals(1, process.forcibleCount)
  }

  /**
   * SKILL-141: a delegated review lane launches with inheritEnvironment=false. Clearing the whole
   * environment left the worker with no PATH to exec from and no home under which its registered
   * native agents live, so preflight reported every review worker as uninstalled.
   */
  @Test
  fun `isolated launch keeps the agent locatable and its user installation resolvable`() {
    val parent = mapOf(
      "HOME" to "/home/dev",
      "PATH" to "/usr/bin",
      "CLAUDE_CONFIG_DIR" to "/home/dev/.claude-work",
      "XDG_CONFIG_HOME" to "/home/dev/.config",
      "ANTHROPIC_SESSION_SECRET" to "ambient",
      "SOME_CALLER_STATE" to "ambient",
    )

    val isolated = isolatedLaunchEnvironment(parent, mapOf("SKILL_BILL_GOAL_CONTINUATION" to "1"))

    assertEquals("/home/dev", isolated["HOME"])
    assertEquals("/usr/bin", isolated["PATH"])
    assertEquals("/home/dev/.claude-work", isolated["CLAUDE_CONFIG_DIR"])
    assertEquals("/home/dev/.config", isolated["XDG_CONFIG_HOME"])
    assertEquals("1", isolated["SKILL_BILL_GOAL_CONTINUATION"])
    assertNull(isolated["ANTHROPIC_SESSION_SECRET"])
    assertNull(isolated["SOME_CALLER_STATE"])
  }

  @Test
  fun `isolated launch overrides win over inherited passthrough values`() {
    val isolated = isolatedLaunchEnvironment(
      mapOf("HOME" to "/home/dev", "PATH" to "/usr/bin"),
      mapOf("HOME" to "/tmp/sandbox-home"),
    )

    assertEquals("/tmp/sandbox-home", isolated["HOME"])
    assertEquals("/usr/bin", isolated["PATH"])
  }

  @Test
  fun `isolated launch passes through additional keys declared by the command builder`() {
    val parent = mapOf(
      "HOME" to "/home/dev",
      "PATH" to "/usr/bin",
      "ANTHROPIC_API_KEY" to "sk-ant-ambient",
      "SOME_AMBIENT_SECRET" to "should-be-stripped",
    )

    val isolated = isolatedLaunchEnvironment(
      parent,
      overrides = mapOf("SKILL_BILL_GOAL_CONTINUATION" to "1"),
      additionalPassthroughKeys = setOf("ANTHROPIC_API_KEY"),
    )

    assertEquals("sk-ant-ambient", isolated["ANTHROPIC_API_KEY"])
    assertEquals("/home/dev", isolated["HOME"])
    assertNull(isolated["SOME_AMBIENT_SECRET"])
  }

  @Test
  fun `configureLaunchEnvironment applies isolation to a real ProcessBuilder environment map`() {
    val builder = ProcessBuilder("echo", "test")
    builder.environment().clear()
    builder.environment()["HOME"] = "/home/dev"
    builder.environment()["PATH"] = "/usr/bin"
    builder.environment()["ANTHROPIC_API_KEY"] = "sk-ant-ambient"
    builder.environment()["SOME_AMBIENT_SECRET"] = "should-be-stripped"

    configureLaunchEnvironment(
      builder,
      testAgentRunProcessRequest(
        listOf("echo"),
        Path.of("."),
      ) {
        environment = mapOf("SKILL_BILL_GOAL_CONTINUATION" to "1")
        inheritEnvironment = false
        environmentPassthroughKeys = setOf("ANTHROPIC_API_KEY")
      },
      testGateJvmResolver(),
    )

    assertEquals("/home/dev", builder.environment()["HOME"])
    assertEquals("/usr/bin", builder.environment()["PATH"])
    assertEquals("sk-ant-ambient", builder.environment()["ANTHROPIC_API_KEY"])
    assertEquals("1", builder.environment()["SKILL_BILL_GOAL_CONTINUATION"])
    assertNull(builder.environment()["SOME_AMBIENT_SECRET"])
  }

  @Test
  fun `an inherited JAVA_HOME inside the runtime image never reaches the child environment`() {
    val leaked = Path.of(System.getProperty("java.home")).resolve("lib").toString()
    val builder = ProcessBuilder("echo", "test")
    builder.environment().clear()
    builder.environment()["PATH"] = hostPath()
    builder.environment()["JAVA_HOME"] = leaked

    configureLaunchEnvironment(
      builder,
      testAgentRunProcessRequest(listOf("echo"), Path.of(".")) { inheritEnvironment = true },
      testGateJvmResolver(),
    )

    assertNotEquals(leaked, builder.environment()["JAVA_HOME"])
  }

  @Test
  fun `a timed-out governed launch leaves no endpoint bound`() {
    val endpoint = GovernedReviewEvidenceEndpoint.bind(
      "architecture",
      BrokerBackedNativeReviewOperationProtocol(TeardownProbeBroker),
      listOf("/bin/true"),
    )

    val result = JvmAgentRunProcessRunner(JvmSystemClock, testGateJvmResolver()).run(
      testAgentRunProcessRequest(
        listOf("sh", "-c", "sleep 30"),
        Path.of("."),
      ) {
        timeout = 1.seconds
        conversationIsolation = ConversationIsolation.NONE
        reviewEvidenceBroker = TeardownProbeBroker
        nativeReviewOperations = BrokerBackedNativeReviewOperationProtocol(TeardownProbeBroker)
        reviewEvidenceEndpoint = endpoint
      },
    )

    assertTrue(result.timedOut)
    assertTrue(Files.notExists(endpoint.descriptor.socketPath))
    assertTrue(Files.notExists(endpoint.descriptor.mcpConfigPath))
  }

  private object TeardownProbeBroker : ReviewEvidenceBroker {
    override fun readBatch(request: ReviewEvidenceBatchRequest) = error("unused")
    override fun recordToolCall(call: ReviewToolCall) = error("unused")
    override fun recordModelTurn() = null
    override fun validateLaneResult(result: String) = null
    override fun observeLaneResultChunk(chunk: String) = null
    override fun accounting() = ReviewLaneAccounting(
      lane = "architecture",
      evidenceBytes = 0,
      expansions = emptyList(),
      toolCalls = 0,
      modelTurns = 0,
      resultBytes = 0,
    )

    override fun terminalOutcome() = null
  }

  private fun childProcessAlive(pid: Long): Boolean =
    ProcessHandle.of(pid).map { handle -> handle.isAlive }.orElse(false)

  private fun testDrain(input: InputStream) = CappedUtf8Drain(
    input = input,
    limitBytes = AGENT_RUN_OUTPUT_LIMIT_BYTES,
    outputStream = AgentRunOutputStream.STDOUT,
    outputSink = AgentRunOutputSink.NONE,
    onChunkRead = {},
  )
}

private class BlockingInputStream : InputStream() {
  @Volatile
  private var open = true
  private val readStarted = CountDownLatch(1)

  override fun read(): Int {
    readStarted.countDown()
    while (open) {
      Thread.sleep(10)
    }
    return -1
  }

  override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
    readStarted.countDown()
    while (open) {
      Thread.sleep(10)
    }
    return -1
  }

  fun unblock() {
    open = false
  }

  fun awaitReadStarted(): Boolean = readStarted.await(5, TimeUnit.SECONDS)
}

private class FakeReapableProcess(private val staysAlive: Boolean) : Process() {
  var destroyCount = 0
    private set
  var forcibleCount = 0
    private set

  override fun getOutputStream() = error("unused")
  override fun getInputStream() = error("unused")
  override fun getErrorStream() = error("unused")
  override fun waitFor(): Int = error("unused")
  override fun exitValue(): Int = error("unused")
  override fun destroy() {
    destroyCount++
  }
  override fun destroyForcibly(): Process {
    forcibleCount++
    return this
  }
  override fun isAlive(): Boolean = staysAlive
  override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = !staysAlive
}
