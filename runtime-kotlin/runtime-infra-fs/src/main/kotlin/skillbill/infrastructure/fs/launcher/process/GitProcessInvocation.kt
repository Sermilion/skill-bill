package skillbill.infrastructure.fs.launcher.process

import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

internal const val GIT_PROCESS_CLEANUP_BUDGET_SECONDS = 5L
internal const val GIT_PROCESS_POLL_MILLIS = 50L

internal fun startGitProcess(repoRoot: Path, args: List<String>, configure: ProcessBuilder.() -> Unit = {}): Process =
  ProcessBuilder(listOf("git", "-C", repoRoot.toString()) + args).apply(configure).start()

internal fun invokeGitProcess(repoRoot: Path, args: List<String>, stdin: ByteArray?): GitProcessResult {
  val process = startGitProcess(repoRoot, args) { redirectErrorStream(true) }
  return GitProcessSession(process, args, stdin).run()
}

internal data class GitProcessBoundedLinesResult(
  val timedOut: Boolean,
  val readFailure: IOException?,
  val exitCode: Int,
)

internal fun invokeGitProcessWithBoundedLines(
  repoRoot: Path,
  args: List<String>,
  readLineMaxBytes: Int,
  shouldStopReading: () -> Boolean,
  onLine: (BoundedDiffLine) -> Unit,
): GitProcessBoundedLinesResult {
  val process = startGitProcess(repoRoot, args) { redirectErrorStream(true) }
  return GitProcessBoundedLineSession(process, args, readLineMaxBytes, shouldStopReading, onLine).run()
}

private class GitProcessBoundedLineSession(
  private val process: Process,
  private val args: List<String>,
  private val readLineMaxBytes: Int,
  private val shouldStopReading: () -> Boolean,
  private val onLine: (BoundedDiffLine) -> Unit,
) {
  private val ownedDescendants = linkedSetOf<ProcessHandle>()
  private val parserTruncated = CountDownLatch(1)
  private var readFailure: IOException? = null
  private var outputThread: Thread? = null
  private var timedOut = false
  private var exitCode = -1
  private var primaryFailure: Throwable? = null
  private var cleanupFailure: Throwable? = null
  private var outputSettled = false

  fun run(): GitProcessBoundedLinesResult {
    try {
      startOutputCapture()
      captureOwnedDescendants()
      if (!awaitProcess()) {
        timedOut = true
      } else {
        exitCode = process.exitValue()
      }
      settleOutput()
    } catch (interrupted: InterruptedException) {
      primaryFailure = interrupted
      destroyForInterrupted(interrupted)
      throw interrupted
    } finally {
      cleanup()
    }
    return GitProcessBoundedLinesResult(
      timedOut = timedOut,
      readFailure = readFailure,
      exitCode = if (timedOut) -1 else exitCode,
    )
  }

  private fun startOutputCapture() {
    outputThread = thread(start = true, name = "skill-bill-git-bounded-lines") {
      try {
        process.inputStream.bufferedReader().use { reader ->
          var keepReading = true
          while (keepReading) {
            val line = reader.readBoundedDiffLine(readLineMaxBytes)
            if (line == null) {
              keepReading = false
            } else {
              onLine(line)
              if (shouldStopReading()) {
                parserTruncated.countDown()
                keepReading = false
              }
            }
          }
        }
      } catch (error: IOException) {
        if (!shouldStopReading()) {
          readFailure = error
        }
      }
    }
  }

  private fun awaitProcess(): Boolean {
    val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(gitTimeoutSeconds(args))
    while (process.isAlive) {
      captureOwnedDescendants()
      val remainingNanos = deadlineNanos - System.nanoTime()
      if (remainingNanos <= 0L) return false
      process.waitFor(
        minOf(remainingNanos, TimeUnit.MILLISECONDS.toNanos(GIT_PROCESS_POLL_MILLIS)),
        TimeUnit.NANOSECONDS,
      )
      if (parserTruncated.await(0, TimeUnit.NANOSECONDS)) {
        attemptCleanup { destroyProcessTree() }
      }
    }
    return true
  }

  private fun settleOutput() {
    val cleanupDeadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(GIT_PROCESS_CLEANUP_BUDGET_SECONDS)
    if (!closeInputAndJoin(process, requireNotNull(outputThread), cleanupDeadlineNanos) && readFailure == null) {
      readFailure = IOException("git bounded line capture did not settle before deadline")
    }
    outputSettled = true
  }

  private fun destroyForInterrupted(interrupted: InterruptedException) {
    runCatching { destroyProcessTree() }.onFailure(interrupted::addSuppressed)
  }

  private fun destroyProcessTree() {
    destroyOwnedProcessTree(process, ownedDescendants)
  }

  private fun captureOwnedDescendants() {
    runCatching {
      ownedDescendants += process.toHandle().descendants().toList()
    }.onFailure(::recordCleanupFailure)
  }

  private fun cleanup() {
    attemptCleanup { destroyProcessTree() }
    attemptCleanup { process.outputStream.close() }
    if (!outputSettled) {
      outputThread?.let { drainThread ->
        attemptCleanup {
          if (!closeInputAndJoin(
              process,
              drainThread,
              System.nanoTime() + TimeUnit.SECONDS.toNanos(GIT_PROCESS_CLEANUP_BUDGET_SECONDS),
            ) && readFailure == null
          ) {
            readFailure = IOException("git bounded line capture did not settle before deadline")
          }
        }
      }
    }
    attemptCleanup { process.inputStream.close() }
    attemptCleanup { process.outputStream.close() }
    cleanupFailure?.let { failure ->
      if (primaryFailure != null) {
        primaryFailure?.addSuppressed(failure)
      } else if (readFailure == null && !timedOut) {
        readFailure = IOException(failure.message.orEmpty())
      }
    }
  }

  private fun attemptCleanup(action: () -> Unit) {
    runCatching(action).exceptionOrNull()?.let(::recordCleanupFailure)
  }

  private fun recordCleanupFailure(failure: Throwable) {
    cleanupFailure = cleanupFailure?.also { existing -> existing.addSuppressed(failure) } ?: failure
  }
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
    if (!timedOut && !process.isAlive) {
      exitCode = process.exitValue()
      settleOwnedDescendants()
    }
    if (timedOut || primaryFailure == null) {
      attemptCleanup { destroyProcessTree() }
    }
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
      readFailure.compareAndSet(null, IOException("git exited with an owned descendant still running"))
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
  attempt { if (process.isAlive) process.destroyForcibly() }
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
