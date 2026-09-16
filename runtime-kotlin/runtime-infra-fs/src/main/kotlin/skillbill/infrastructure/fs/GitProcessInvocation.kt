package skillbill.infrastructure.fs

import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

internal const val GIT_PROCESS_CLEANUP_BUDGET_SECONDS = 5L
internal const val GIT_PROCESS_POLL_MILLIS = 50L

internal fun invokeGitProcess(repoRoot: Path, args: List<String>, stdin: ByteArray?): GitProcessResult {
  val process = ProcessBuilder(listOf("git", "-C", repoRoot.toString()) + args)
    .redirectErrorStream(true)
    .start()
  return GitProcessSession(process, args, stdin).run()
}

private class GitProcessSession(
  private val process: Process,
  private val args: List<String>,
  private val stdin: ByteArray?,
) {
  private val output = StringBuilder()
  private val readFailure = AtomicReference<IOException?>()
  private val inputFailure = AtomicReference<IOException?>()
  private val inputRuntimeFailure = AtomicReference<RuntimeException?>()
  private val ownedDescendants = linkedSetOf<ProcessHandle>()
  private val operationDeadlineNanos =
    System.nanoTime() + TimeUnit.SECONDS.toNanos(gitTimeoutSeconds(args))
  private var timedOut = false
  private var exitCode = -1
  private var primaryFailure: Throwable? = null
  private var cleanupFailure: Throwable? = null
  private var outputThread: Thread? = null
  private var inputThread: Thread? = null

  fun run(): GitProcessResult {
    try {
      startOutputThread()
      startInputThread()
      awaitProcess()
      settleOutput()
    } catch (interrupted: InterruptedException) {
      primaryFailure = primaryFailure ?: interrupted
      destroyForInterrupted(interrupted)
      throw interrupted
    } finally {
      cleanup()
    }
    if (primaryFailure == null && !timedOut && readFailure.get() == null) {
      cleanupFailure?.let { throw it }
    }
    return GitProcessResult(
      output = output.toString().trim(),
      readFailure = readFailure.get(),
      timedOut = timedOut,
      exitCode = if (timedOut) -1 else exitCode,
    )
  }

  private fun startOutputThread() {
    try {
      outputThread = thread(start = true, name = "skill-bill-git-output") {
        try {
          process.inputStream.bufferedReader().use { reader -> output.append(reader.readText()) }
        } catch (error: IOException) {
          readFailure.compareAndSet(null, error)
        }
      }
    } catch (failure: IllegalThreadStateException) {
      primaryFailure = failure
      throw failure
    } catch (failure: SecurityException) {
      primaryFailure = failure
      throw failure
    }
  }

  private fun startInputThread() {
    if (stdin == null) {
      attemptCleanup { process.outputStream.close() }
      return
    }
    inputThread = startInputWorker(stdin)
    if (!joinWithDeadline(requireNotNull(inputThread), operationDeadlineNanos)) {
      timedOut = true
      captureOwnedDescendants()
      attemptCleanup { destroyProcessTree() }
    }
    inputFailure.get()?.let { failure ->
      primaryFailure = failure
      readFailure.set(failure)
    }
    inputRuntimeFailure.get()?.let { failure ->
      failInput(failure)
    }
  }

  private fun startInputWorker(stdin: ByteArray): Thread = runCatching {
    thread(start = true, name = "skill-bill-git-input") {
      try {
        process.outputStream.use { stream -> stream.write(stdin) }
      } catch (error: IOException) {
        inputFailure.compareAndSet(null, error)
      } catch (error: IllegalStateException) {
        inputRuntimeFailure.compareAndSet(null, error)
      } catch (error: SecurityException) {
        inputRuntimeFailure.compareAndSet(null, error)
      }
    }
  }.getOrElse { failure ->
    primaryFailure = failure
    (failure as? IOException)?.let { readFailure.set(it) }
    throw failure
  }

  private fun failInput(failure: RuntimeException): Nothing {
    primaryFailure = failure
    throw failure
  }

  private fun awaitProcess() {
    if (primaryFailure != null) return
    while (process.isAlive) {
      captureOwnedDescendants()
      val remainingWaitNanos = operationDeadlineNanos - System.nanoTime()
      if (remainingWaitNanos <= 0L) {
        timedOut = true
        break
      }
      process.waitFor(
        minOf(remainingWaitNanos, TimeUnit.MILLISECONDS.toNanos(GIT_PROCESS_POLL_MILLIS)),
        TimeUnit.NANOSECONDS,
      )
    }
    if (!timedOut && !process.isAlive) exitCode = process.exitValue()
    if (timedOut || primaryFailure == null) {
      attemptCleanup { destroyProcessTree() }
    }
  }

  private fun settleOutput() {
    val outputDeadlineNanos = minOf(
      operationDeadlineNanos,
      System.nanoTime() + TimeUnit.SECONDS.toNanos(GIT_PROCESS_CLEANUP_BUDGET_SECONDS),
    )
    if (!closeInputAndJoin(process, requireNotNull(outputThread), outputDeadlineNanos)) {
      readFailure.compareAndSet(null, IOException("git output capture did not settle before deadline"))
    }
    if (Thread.currentThread().isInterrupted) {
      val interrupted = InterruptedException()
      primaryFailure = interrupted
      destroyForInterrupted(interrupted)
      throw interrupted
    }
  }

  private fun cleanup() {
    val cleanupDeadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(GIT_PROCESS_CLEANUP_BUDGET_SECONDS)
    attemptCleanup { process.outputStream.close() }
    inputThread?.let { writerThread ->
      attemptCleanup {
        if (!joinWithDeadline(writerThread, cleanupDeadlineNanos)) {
          readFailure.compareAndSet(null, IOException("git input delivery did not settle before deadline"))
        }
      }
    }
    outputThread?.let { drainThread ->
      attemptCleanup { closeInputAndJoin(process, drainThread, cleanupDeadlineNanos) }
    }
    attemptCleanup { destroyProcessTree() }
    attemptCleanup { process.inputStream.close() }
    attemptCleanup { process.outputStream.close() }
    cleanupFailure?.let { failure ->
      when {
        primaryFailure != null -> primaryFailure?.addSuppressed(failure)
        readFailure.get() != null -> readFailure.get()?.addSuppressed(failure)
        timedOut -> readFailure.compareAndSet(
          null,
          IOException("git process cleanup failed").also { it.addSuppressed(failure) },
        )
      }
    }
  }

  private fun captureOwnedDescendants() {
    runCatching {
      ownedDescendants += process.toHandle().descendants().toList()
    }.onFailure(::recordCleanupFailure)
  }

  private fun destroyProcessTree() {
    destroyOwnedProcessTree(process, ownedDescendants)
  }

  private fun destroyForInterrupted(interrupted: InterruptedException) {
    runCatching { destroyProcessTree() }.onFailure(interrupted::addSuppressed)
  }

  private fun attemptCleanup(action: () -> Unit) {
    runCatching(action).exceptionOrNull()?.let(::recordCleanupFailure)
  }

  private fun recordCleanupFailure(failure: Throwable) {
    cleanupFailure = cleanupFailure?.also { existing -> existing.addSuppressed(failure) } ?: failure
  }
}

internal fun destroyOwnedProcessTree(process: Process, knownDescendants: Set<ProcessHandle> = emptySet()) {
  var failure: Throwable? = null
  fun attempt(action: () -> Unit) {
    val error = runCatching(action).exceptionOrNull() ?: return
    failure = failure?.also { existing -> existing.addSuppressed(error) } ?: error
  }
  val handle = runCatching { process.toHandle() }
    .onFailure { error -> failure = error }
    .getOrNull()
  handle?.let { ownedProcess ->
    val descendants = linkedSetOf<ProcessHandle>()
    descendants += knownDescendants
    descendants += ownedProcess.descendants().toList()
    descendants.forEach { descendant ->
      attempt { descendant.destroyForcibly() }
    }
    attempt {
      ownedProcess.destroyForcibly()
    }
  }
  attempt { process.destroyForcibly() }
  failure?.let { throw it }
}

internal fun closeInputAndJoin(process: Process, outputThread: Thread, joinDeadlineNanos: Long): Boolean {
  if (joinWithDeadline(outputThread, joinDeadlineNanos)) {
    return true
  }
  runCatching { process.inputStream.close() }
  joinWithDeadline(outputThread, joinDeadlineNanos)
  return false
}

private fun joinWithDeadline(thread: Thread, deadlineNanos: Long): Boolean {
  val remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime())
  if (remainingMillis <= 0L) {
    return false
  }
  thread.join(remainingMillis)
  return !thread.isAlive
}
