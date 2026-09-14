package skillbill.infrastructure.fs.launcher.process

import skillbill.workflow.goal.model.GoalProgressOutcome
import java.util.concurrent.TimeUnit

internal data class ProcessRunReleaseSnapshot(
  val interrupted: Boolean,
  val outputCaptureIncomplete: Boolean,
  val stdoutCapture: CappedUtf8DrainCapture,
  val stderrCapture: CappedUtf8DrainCapture,
)

internal class ProcessRunLifetime(
  private val process: Process,
  private val liveProcesses: MutableSet<Process>,
  private val stdout: CappedUtf8Drain,
  private val stderr: CappedUtf8Drain,
  private val degradation: ProcessRunDegradationRecorder,
) {
  @Volatile
  private var released = false
  private var releaseSnapshot: ProcessRunReleaseSnapshot? = null

  init {
    liveProcesses.add(process)
  }

  fun release(waitResult: Result<ProcessWait>?): ProcessRunReleaseSnapshot {
    if (released) return requireNotNull(releaseSnapshot)
    released = true
    var interrupted = waitResult?.exceptionOrNull() is InterruptedException || Thread.interrupted()
    val wait = waitResult?.getOrNull()
    val finished = wait?.finished == true
    if (!finished) {
      runCatching {
        process.destroyForcibly()
        val exited = process.waitFor(DESTROY_WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        if (!exited && process.isAlive) {
          degradation.recordCleanupFailure(
            "process_destroy_wait",
            IllegalStateException("child process remained alive after forced termination"),
          )
        }
      }.onFailure { failure ->
        degradation.recordCleanupFailure("process_destroy_wait", failure)
        if (failure is InterruptedException) interrupted = true
      }
    }
    runCatching { process.outputStream.close() }
      .onFailure { failure -> degradation.recordCleanupFailure("stdin_stream_close", failure) }
    val stdoutIncomplete = runCatching { stdout.joinAndFreeze() }
      .onFailure { failure ->
        degradation.recordCleanupFailure("stdout_drain_join", failure)
        if (failure is InterruptedException) interrupted = true
      }
      .getOrDefault(true)
    stdout.workerFailure?.let { failure ->
      degradation.recordCleanupFailure("stdout_drain", failure)
    }
    val stderrIncomplete = runCatching { stderr.joinAndFreeze() }
      .onFailure { failure ->
        degradation.recordCleanupFailure("stderr_drain_join", failure)
        if (failure is InterruptedException) interrupted = true
      }
      .getOrDefault(true)
    stderr.workerFailure?.let { failure ->
      degradation.recordCleanupFailure("stderr_drain", failure)
    }
    runCatching { process.inputStream.close() }
      .onFailure { failure -> degradation.recordCleanupFailure("stdout_stream_close", failure) }
    runCatching { process.errorStream.close() }
      .onFailure { failure -> degradation.recordCleanupFailure("stderr_stream_close", failure) }
    if (process.isAlive) {
      degradation.recordCleanupFailure(
        "process_cleanup",
        IllegalStateException("child process remained alive after cleanup"),
      )
    } else {
      liveProcesses.remove(process)
    }
    val snapshot = ProcessRunReleaseSnapshot(
      interrupted = interrupted,
      outputCaptureIncomplete = stdoutIncomplete || stderrIncomplete ||
        stdout.workerFailure != null || stderr.workerFailure != null,
      stdoutCapture = stdout.capture(),
      stderrCapture = stderr.capture(),
    )
    releaseSnapshot = snapshot
    if (interrupted) {
      Thread.currentThread().interrupt()
    }
    return snapshot
  }

  fun cachedRelease(): ProcessRunReleaseSnapshot =
    requireNotNull(releaseSnapshot) { "process lifetime was not released" }

  fun terminalOutcome(snapshot: ProcessRunReleaseSnapshot, wait: ProcessWait?): GoalProgressOutcome = when {
    snapshot.interrupted -> GoalProgressOutcome.CANCELLED
    wait?.finished == true -> GoalProgressOutcome.SUCCEEDED
    else -> GoalProgressOutcome.TIMED_OUT
  }
}
