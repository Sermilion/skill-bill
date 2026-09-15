package skillbill.infrastructure.fs.launcher.process

import me.tatarka.inject.annotations.Inject
import skillbill.goalrunner.model.GoalRunnerProcessState
import skillbill.infrastructure.fs.jvm.GateJvmResolver
import skillbill.ports.agentrun.model.AgentRunLivenessSnapshot
import skillbill.ports.agentrun.model.AgentRunOutputStream
import skillbill.ports.review.GovernedReviewEvidenceEndpointHandle
import java.io.IOException
import java.io.InputStream
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Inject
class JvmAgentRunProcessRunner(
  private val clock: Clock,
  private val gateJvmResolver: GateJvmResolver,
) : AgentRunProcessRunner {
  override fun run(request: AgentRunProcessRequest): AgentRunProcessResult {
    request.reviewEvidenceEndpoint?.let(liveEndpoints::add)
    return try {
      runGoverned(request)
    } finally {
      closeEndpoint(request)
    }
  }

  private fun runGoverned(request: AgentRunProcessRequest): AgentRunProcessResult {
    var startedProcess: ProcessStart? = null
    val processStart = runCatching {
      request.spawnAuthorization?.withAuthorization {
        startProcess(request).also { startedProcess = it }
      } ?: startProcess(request).also { startedProcess = it }
    }.getOrElse { failure ->
      cleanupProcessStart(startedProcess)
      throw failure
    }
    return when (processStart) {
      is ProcessStart.Failed -> spawnFailure(processStart.error)
      is ProcessStart.Started -> runStartedProcess(
        process = processStart.process,
        stdoutStream = processStart.process.inputStream,
        stderrStream = processStart.process.errorStream,
        request = request,
      )
    }
  }

  companion object {
    private val liveProcesses = ConcurrentHashMap.newKeySet<Process>()
    private val liveEndpoints =
      ConcurrentHashMap.newKeySet<GovernedReviewEvidenceEndpointHandle>()

    init {
      Runtime.getRuntime().addShutdownHook(object : Thread("skill-bill-agent-run-shutdown") {
        override fun run() {
          reapLiveProcesses(liveProcesses.toList())
          liveEndpoints.toList().forEach { endpoint ->
            liveEndpoints.remove(endpoint)
            runCatching { endpoint.close() }
          }
        }
      })
    }

    internal fun closeEndpoint(request: AgentRunProcessRequest) {
      val endpoint = request.reviewEvidenceEndpoint ?: return
      liveEndpoints.remove(endpoint)
      runCatching { endpoint.close() }.onFailure { failure ->
        runCatching {
          request.outputSink.write(
            AgentRunOutputStream.STDERR,
            "governed review evidence endpoint teardown failed: ${failure.message.orEmpty()}\n",
          )
        }
      }
    }

    internal fun reapLiveProcesses(processes: List<Process>) {
      processes.forEach { process -> runCatching { process.destroy() } }
      processes.forEach { process ->
        runCatching { process.waitFor(DESTROY_WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS) }
        if (process.isAlive) runCatching { process.destroyForcibly() }
      }
    }
  }

  private fun runStartedProcess(
    process: Process,
    stdoutStream: InputStream,
    stderrStream: InputStream,
    request: AgentRunProcessRequest,
  ): AgentRunProcessResult {
    val degradation = ProcessRunDegradationRecorder()
    val outputTracker = OutputObservationTracker()
    val lifecycleEmitter = ProcessLifecycleEmitter(request)
    val stdout = CappedUtf8Drain(
      input = stdoutStream,
      limitBytes = AGENT_RUN_OUTPUT_LIMIT_BYTES,
      outputStream = AgentRunOutputStream.STDOUT,
      outputSink = request.outputSink,
      onChunkRead = { outputTracker.markObserved() },
    )
    val stderr = CappedUtf8Drain(
      input = stderrStream,
      limitBytes = AGENT_RUN_OUTPUT_LIMIT_BYTES,
      outputStream = AgentRunOutputStream.STDERR,
      outputSink = request.outputSink,
      onChunkRead = { outputTracker.markObserved() },
    )
    val lifetime = ProcessRunLifetime(process, liveProcesses, stdout, stderr, degradation)
    var mcpStartupObservedAtStart = false
    var waitResult: Result<ProcessWait>? = null
    val runOutcome = runCatching {
      mcpStartupObservedAtStart = request.mcpStartupProbe.readStartupObserved(degradation).value == true
      stdout.start()
      stderr.start()
      writeAndCloseStdin(process, request.stdinText, degradation)
      lifecycleEmitter.emitStarted(process.isAlive)
      waitResult = runCatching {
        waitForProcess(process, request, outputTracker, lifecycleEmitter, degradation)
      }
    }
    val cleanupFailure = runCatching { lifetime.release(waitResult) }.exceptionOrNull()
    cleanupFailure?.let { failure ->
      runOutcome.exceptionOrNull()?.addSuppressed(failure) ?: throw failure
    }
    runOutcome.getOrThrow()
    waitResult?.exceptionOrNull()
      ?.takeUnless { it is InterruptedException }
      ?.let { throw it }
    return buildRunResult(
      BuildRunResultInput(
        process = process,
        request = request,
        waitResult = requireNotNull(waitResult),
        outputTracker = outputTracker,
        lifecycleEmitter = lifecycleEmitter,
        lifetime = lifetime,
        degradation = degradation,
        mcpStartupObservedAtStart = mcpStartupObservedAtStart,
      ),
    )
  }

  private data class BuildRunResultInput(
    val process: Process,
    val request: AgentRunProcessRequest,
    val waitResult: Result<ProcessWait>,
    val outputTracker: OutputObservationTracker,
    val lifecycleEmitter: ProcessLifecycleEmitter,
    val lifetime: ProcessRunLifetime,
    val degradation: ProcessRunDegradationRecorder,
    val mcpStartupObservedAtStart: Boolean,
  )

  private fun buildRunResult(input: BuildRunResultInput): AgentRunProcessResult {
    val release = input.lifetime.cachedRelease()
    val wait = input.waitResult.getOrNull()
    val terminalOutcome = input.lifetime.terminalOutcome(release, wait)
    input.lifecycleEmitter.emitCompleted(processAlive = false, outcome = terminalOutcome)
    val mcpStartupObserved =
      input.mcpStartupObservedAtStart ||
        input.request.mcpStartupProbe.readStartupObserved(input.degradation).value == true
    if (release.interrupted) {
      return interruptedResult(
        release = release,
        outputTracker = input.outputTracker,
        mcpStartupObserved = mcpStartupObserved,
        degradation = input.degradation,
      )
    }
    val stdout = release.stdoutCapture
    val stderr = release.stderrCapture
    val settledWait = requireNotNull(wait)
    val stderrText = stderr.text.withTimeoutMessage(settledWait, input.request)
    return AgentRunProcessResult(
      exitStatus = if (settledWait.finished) input.process.exitValue() else null,
      stdout = stdout.text,
      stdoutBytes = stdout.bytes,
      stderr = input.degradation.appendToStderr(stderrText),
      timedOut = !settledWait.finished,
      interrupted = false,
      spawnFailed = false,
      liveness = settledWait.liveness,
      processStarted = true,
      mcpStartupObserved = mcpStartupObserved,
      stdoutTruncated = stdout.truncated,
      stdoutByteSize = stdout.totalByteSize,
      stdoutSha256 = stdout.sha256,
      outputCaptureIncomplete = release.outputCaptureIncomplete,
    )
  }

  private fun interruptedResult(
    release: ProcessRunReleaseSnapshot,
    outputTracker: OutputObservationTracker,
    mcpStartupObserved: Boolean,
    degradation: ProcessRunDegradationRecorder,
  ): AgentRunProcessResult {
    val stdout = release.stdoutCapture
    val stderr = release.stderrCapture
    val interruptMessage = "Agent run interrupted by parent signal before completion."
    val stderrBody = stderr.text.let { existing ->
      if (existing.isBlank()) {
        interruptMessage
      } else {
        "$existing\n$interruptMessage"
      }
    }
    return AgentRunProcessResult(
      exitStatus = null,
      stdout = stdout.text,
      stdoutBytes = stdout.bytes,
      stderr = degradation.appendToStderr(stderrBody),
      timedOut = false,
      interrupted = true,
      spawnFailed = false,
      processStarted = true,
      mcpStartupObserved = mcpStartupObserved,
      liveness = AgentRunLivenessSnapshot(
        phase = "watchdog",
        reason = "parent_interrupted",
        processState = GoalRunnerProcessState.KILLED,
        lastOutputAt = outputTracker.lastObservedAt()?.toIsoUtc(),
      ),
      stdoutTruncated = stdout.truncated,
      stdoutByteSize = stdout.totalByteSize,
      stdoutSha256 = stdout.sha256,
      outputCaptureIncomplete = release.outputCaptureIncomplete,
    )
  }

  private fun waitForProcess(
    process: Process,
    request: AgentRunProcessRequest,
    outputTracker: OutputObservationTracker,
    lifecycleEmitter: ProcessLifecycleEmitter,
    degradation: ProcessRunDegradationRecorder,
  ): ProcessWait = ProcessWaitLoop(process, request, outputTracker, lifecycleEmitter, clock, degradation).wait()

  private fun spawnFailure(error: Exception): AgentRunProcessResult = AgentRunProcessResult(
    exitStatus = null,
    stdout = "",
    stderr = error.message.orEmpty(),
    timedOut = false,
    interrupted = false,
    spawnFailed = true,
  )

  private fun startProcess(request: AgentRunProcessRequest): ProcessStart = try {
    ProcessStart.Started(buildProcess(request).start())
  } catch (error: IOException) {
    ProcessStart.Failed(error)
  } catch (error: SecurityException) {
    ProcessStart.Failed(error)
  }

  private fun buildProcess(request: AgentRunProcessRequest): ProcessBuilder = ProcessBuilder(request.command)
    .directory(request.workingDirectory.toFile())
    .also { configureLaunchEnvironment(it, request, gateJvmResolver) }

  private fun cleanupProcessStart(start: ProcessStart?) {
    when (start) {
      is ProcessStart.Started -> reapLiveProcesses(listOf(start.process))
      is ProcessStart.Failed, null -> Unit
    }
  }
}
