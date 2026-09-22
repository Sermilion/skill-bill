package skillbill.infrastructure.host.process

import skillbill.ports.process.INSTALLER_OUTPUT_TRUNCATION_SENTINEL
import skillbill.ports.process.INSTALLER_PROCESS_OUTPUT_CAP_BYTES
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

data class BoundedExternalProcessRequest(
  val argv: List<String>,
  val workingDirectory: Path? = null,
  val environment: Map<String, String>? = null,
  val mergeEnvironment: Map<String, String> = emptyMap(),
  val clearEnvironment: Boolean = false,
  val redirectOutputFile: Path? = null,
  val deadlineSeconds: Long,
  val outputCapBytes: Long? = INSTALLER_PROCESS_OUTPUT_CAP_BYTES.toLong(),
)

data class BoundedExternalProcessResult(
  val exitCode: Int,
  val output: String = "",
  val timedOut: Boolean = false,
  val launchFailure: Boolean = false,
)

object BoundedExternalProcessRunner {
  fun run(request: BoundedExternalProcessRequest): BoundedExternalProcessResult {
    val session = BoundedExternalProcessSession(request)
    return session.run()
  }
}

private class BoundedExternalProcessSession(
  private val request: BoundedExternalProcessRequest,
) {
  private val deadlineSeconds = request.deadlineSeconds.coerceAtLeast(1L)
  private val operationDeadlineNanos =
    System.nanoTime() + TimeUnit.SECONDS.toNanos(deadlineSeconds)
  private val ownedDescendants = linkedSetOf<ProcessHandle>()
  private val output = StringBuilder()
  private val truncated = AtomicBoolean(false)
  private val readFailure = AtomicReference<IOException?>()
  private var process: Process? = null
  private var outputThread: Thread? = null
  private var timedOut = false
  private var exitCode = 1
  private var primaryFailure: Throwable? = null
  private var cleanupFailure: Throwable? = null

  fun run(): BoundedExternalProcessResult {
    try {
      if (!startProcess()) {
        return launchFailureResult()
      }
      awaitProcess()
      settleOutput()
    } catch (interrupted: InterruptedException) {
      primaryFailure = primaryFailure ?: interrupted
      destroyForInterrupted(interrupted)
      throw interrupted
    } finally {
      cleanup()
    }
    return buildResult()
  }

  private fun startProcess(): Boolean {
    val started =
      runCatching {
        ProcessBuilder(request.argv).apply {
          request.workingDirectory?.let { directory(it.toFile()) }
          if (request.clearEnvironment) {
            environment().clear()
          }
          request.environment?.let { environment().putAll(it) }
          if (request.mergeEnvironment.isNotEmpty()) {
            environment().putAll(request.mergeEnvironment)
          }
          redirectErrorStream(true)
          request.redirectOutputFile?.let { redirectOutput(it.toFile()) }
        }.start()
      }.getOrElse { failure ->
        primaryFailure = failure
        return false
      }
    process = started
    runCatching { started.outputStream.close() }.onFailure(::recordCleanupFailure)
    if (request.redirectOutputFile == null) {
      startOutputThread(started)
    }
    return true
  }

  private fun launchFailureResult(): BoundedExternalProcessResult {
    val message = primaryFailure?.message.orEmpty().ifBlank { primaryFailure?.javaClass?.simpleName.orEmpty() }
    return BoundedExternalProcessResult(
      exitCode = 1,
      output = "Failed to launch process: $message",
      launchFailure = true,
    )
  }

  private fun startOutputThread(process: Process) {
    outputThread =
      thread(start = true, name = "skill-bill-bounded-process-output") {
        drainCapped(process.inputStream)
      }
  }

  private fun drainCapped(stream: InputStream) {
    val cap = request.outputCapBytes
    if (cap == null) {
      output.append(stream.bufferedReader().readText())
      return
    }
    val buffer = ByteArray(BUFFER_BYTES)
    var capturedBytes = 0
    try {
      var done = false
      while (!done) {
        val count = stream.read(buffer)
        if (count < 0) {
          done = true
        } else if (capturedBytes + count > cap) {
          val remaining = cap - capturedBytes
          if (remaining > 0) {
            output.append(String(buffer, 0, remaining.toInt(), Charsets.UTF_8))
            capturedBytes += remaining.toInt()
          }
          truncated.set(true)
        } else {
          output.append(String(buffer, 0, count, Charsets.UTF_8))
          capturedBytes += count
        }
      }
    } catch (error: IOException) {
      readFailure.compareAndSet(null, error)
    }
  }

  private fun awaitProcess() {
    val active = process ?: return
    while (active.isAlive) {
      captureOwnedDescendants(active)
      val remainingWaitNanos = operationDeadlineNanos - System.nanoTime()
      if (remainingWaitNanos <= 0L) {
        timedOut = true
        break
      }
      active.waitFor(
        minOf(remainingWaitNanos, TimeUnit.MILLISECONDS.toNanos(PROCESS_POLL_MILLIS)),
        TimeUnit.NANOSECONDS,
      )
    }
    if (!timedOut && !active.isAlive) {
      exitCode = active.exitValue()
      settleOwnedDescendants()
    }
    if (timedOut) {
      attemptCleanup { destroyProcessTree(active) }
    }
  }

  private fun settleOutput() {
    val active = process ?: return
    if (request.redirectOutputFile != null) {
      return
    }
    val outputDeadlineNanos =
      if (timedOut) {
        System.nanoTime() + TimeUnit.SECONDS.toNanos(PROCESS_CLEANUP_BUDGET_SECONDS)
      } else {
        minOf(
          operationDeadlineNanos,
          System.nanoTime() + TimeUnit.SECONDS.toNanos(PROCESS_CLEANUP_BUDGET_SECONDS),
        )
      }
    outputThread?.let { drainThread ->
      if (!closeInputAndJoin(active, drainThread, outputDeadlineNanos)) {
        readFailure.compareAndSet(null, IOException("process output capture did not settle before deadline"))
      }
    }
    if (Thread.currentThread().isInterrupted) {
      val interrupted = InterruptedException()
      primaryFailure = interrupted
      destroyForInterrupted(interrupted)
      throw interrupted
    }
  }

  private fun cleanup() {
    val active = process ?: return
    val cleanupDeadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(PROCESS_CLEANUP_BUDGET_SECONDS)
    attemptCleanup { active.outputStream.close() }
    outputThread?.let { drainThread ->
      attemptCleanup { closeInputAndJoin(active, drainThread, cleanupDeadlineNanos) }
    }
    attemptCleanup { destroyProcessTree(active) }
    attemptCleanup { active.inputStream.close() }
    attemptCleanup { active.outputStream.close() }
    runCatching {
      if (active.isAlive) {
        active.destroyForcibly()
        active.waitFor(DESTROY_WAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
      }
    }.onFailure(::recordCleanupFailure)
  }

  private fun buildResult(): BoundedExternalProcessResult {
    val fileOutput =
      request.redirectOutputFile?.let { path ->
        if (!Files.exists(path)) "" else Files.readString(path)
      }
    val captured =
      buildString {
        if (fileOutput != null) {
          append(fileOutput)
        } else {
          append(output)
          if (truncated.get()) append(INSTALLER_OUTPUT_TRUNCATION_SENTINEL)
        }
      }
    if (timedOut) {
      return BoundedExternalProcessResult(
        exitCode = TIMEOUT_EXIT_CODE,
        output = captured.ifBlank { readFailure.get()?.message.orEmpty() },
        timedOut = true,
      )
    }
    readFailure.get()?.let { failure ->
      return BoundedExternalProcessResult(
        exitCode = if (exitCode == 0) 1 else exitCode,
        output = captured.ifBlank { failure.message.orEmpty() },
      )
    }
    return BoundedExternalProcessResult(exitCode = exitCode, output = captured)
  }

  private fun captureOwnedDescendants(process: Process) {
    runCatching {
      ownedDescendants += process.toHandle().descendants().toList()
    }.onFailure(::recordCleanupFailure)
  }

  private fun settleOwnedDescendants() {
    val deadlineNanos =
      minOf(
        operationDeadlineNanos,
        System.nanoTime() + TimeUnit.SECONDS.toNanos(PROCESS_CLEANUP_BUDGET_SECONDS),
      )
    while (ownedDescendants.any { it.isAlive } && System.nanoTime() < deadlineNanos) {
      Thread.sleep(PROCESS_POLL_MILLIS)
    }
    if (ownedDescendants.any { it.isAlive }) {
      readFailure.compareAndSet(null, IOException("process exited with an owned descendant still running"))
    }
  }

  private fun destroyProcessTree(process: Process) {
    destroyOwnedProcessTree(process, ownedDescendants)
  }

  private fun destroyForInterrupted(interrupted: InterruptedException) {
    process?.let { active -> runCatching { destroyProcessTree(active) }.onFailure(interrupted::addSuppressed) }
  }

  private fun attemptCleanup(action: () -> Unit) {
    runCatching(action).exceptionOrNull()?.let(::recordCleanupFailure)
  }

  private fun recordCleanupFailure(failure: Throwable) {
    cleanupFailure = cleanupFailure?.also { existing -> existing.addSuppressed(failure) } ?: failure
  }
}

private const val BUFFER_BYTES = 8192
private const val TIMEOUT_EXIT_CODE = 124
