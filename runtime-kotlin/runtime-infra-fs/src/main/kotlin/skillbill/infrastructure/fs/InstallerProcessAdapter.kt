package skillbill.infrastructure.fs

import me.tatarka.inject.annotations.Inject
import skillbill.infrastructure.fs.launcher.process.DESTROY_WAIT_TIMEOUT_MILLIS
import skillbill.ports.process.INSTALLER_OUTPUT_TRUNCATION_SENTINEL
import skillbill.ports.process.INSTALLER_PROCESS_OUTPUT_CAP_BYTES
import skillbill.ports.process.InstallerProcessPort
import skillbill.ports.process.InstallerProcessRequest
import skillbill.ports.process.InstallerProcessResult
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

@Inject
class InstallerProcessAdapter() : InstallerProcessPort {
  override fun run(request: InstallerProcessRequest): InstallerProcessResult {
    val deadlineSeconds = request.deadlineSeconds.coerceAtLeast(1L)
    val session = InstallerProcessSession(request, deadlineSeconds)
    return session.run()
  }
}

private class InstallerProcessSession(
  private val request: InstallerProcessRequest,
  private val deadlineSeconds: Long,
) {
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

  fun run(): InstallerProcessResult {
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
        ProcessBuilder(listOf(request.executable) + request.arguments)
          .redirectErrorStream(true)
          .apply { environment().clear(); environment().putAll(request.environment) }
          .start()
      }.getOrElse { failure ->
        primaryFailure = failure
        return false
      }
    process = started
    runCatching { started.outputStream.close() }.onFailure(::recordCleanupFailure)
    startOutputThread(started)
    return true
  }

  private fun launchFailureResult(): InstallerProcessResult {
    val message = primaryFailure?.message.orEmpty().ifBlank { primaryFailure?.javaClass?.simpleName.orEmpty() }
    return InstallerProcessResult(
      exitCode = 1,
      output = "Failed to launch installer: $message",
      launchFailure = true,
    )
  }

  private fun startOutputThread(process: Process) {
    outputThread =
      thread(start = true, name = "skill-bill-installer-output") {
        drainCapped(process.inputStream)
      }
  }

  private fun drainCapped(stream: InputStream) {
    val buffer = ByteArray(BUFFER_BYTES)
    var capturedBytes = 0
    try {
      var done = false
      while (!done) {
        val count = stream.read(buffer)
        if (count < 0) {
          done = true
        } else if (capturedBytes + count > INSTALLER_PROCESS_OUTPUT_CAP_BYTES) {
          val remaining = INSTALLER_PROCESS_OUTPUT_CAP_BYTES - capturedBytes
          if (remaining > 0) {
            output.append(String(buffer, 0, remaining, Charsets.UTF_8))
            capturedBytes += remaining
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
        minOf(remainingWaitNanos, TimeUnit.MILLISECONDS.toNanos(GIT_PROCESS_POLL_MILLIS)),
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
    val outputDeadlineNanos =
      if (timedOut) {
        System.nanoTime() + TimeUnit.SECONDS.toNanos(GIT_PROCESS_CLEANUP_BUDGET_SECONDS)
      } else {
        minOf(
          operationDeadlineNanos,
          System.nanoTime() + TimeUnit.SECONDS.toNanos(GIT_PROCESS_CLEANUP_BUDGET_SECONDS),
        )
      }
    outputThread?.let { drainThread ->
      if (!closeInputAndJoin(active, drainThread, outputDeadlineNanos)) {
        readFailure.compareAndSet(null, IOException("installer output capture did not settle before deadline"))
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
    val cleanupDeadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(GIT_PROCESS_CLEANUP_BUDGET_SECONDS)
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
    if (cleanupFailure != null && primaryFailure == null && !timedOut && readFailure.get() == null) {
      readFailure.set(IOException("installer cleanup failed").also { failure ->
        failure.addSuppressed(requireNotNull(cleanupFailure))
      })
    }
  }

  private fun buildResult(): InstallerProcessResult {
    val captured =
      buildString {
        append(output)
        if (truncated.get()) append(INSTALLER_OUTPUT_TRUNCATION_SENTINEL)
      }
    if (timedOut) {
      return InstallerProcessResult(
        exitCode = INSTALLER_TIMEOUT_EXIT_CODE,
        output = captured.ifBlank { readFailure.get()?.message.orEmpty() },
        timedOut = true,
      )
    }
    readFailure.get()?.let { failure ->
      return InstallerProcessResult(
        exitCode = if (exitCode == 0) 1 else exitCode,
        output = captured.ifBlank { failure.message.orEmpty() },
      )
    }
    return InstallerProcessResult(exitCode = exitCode, output = captured)
  }

  private fun captureOwnedDescendants(process: Process) {
    runCatching {
      ownedDescendants += process.toHandle().descendants().toList()
    }.onFailure(::recordCleanupFailure)
  }

  private fun settleOwnedDescendants() {
    val deadlineNanos = minOf(
      operationDeadlineNanos,
      System.nanoTime() + TimeUnit.SECONDS.toNanos(GIT_PROCESS_CLEANUP_BUDGET_SECONDS),
    )
    while (ownedDescendants.any { it.isAlive } && System.nanoTime() < deadlineNanos) {
      Thread.sleep(GIT_PROCESS_POLL_MILLIS)
    }
    if (ownedDescendants.any { it.isAlive }) {
      readFailure.compareAndSet(null, IOException("installer exited with an owned descendant still running"))
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
private const val INSTALLER_TIMEOUT_EXIT_CODE = 124
