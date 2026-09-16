package skillbill.infrastructure.fs

import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

internal const val GIT_PROCESS_CLEANUP_BUDGET_SECONDS = 5L

internal fun invokeGitProcess(
  repoRoot: Path,
  args: List<String>,
  stdin: ByteArray?,
): GitProcessResult {
  val timeoutSeconds = gitTimeoutSeconds(args)
  val operationDeadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
  val process = ProcessBuilder(listOf("git", "-C", repoRoot.toString()) + args)
    .redirectErrorStream(true)
    .start()
  val output = StringBuilder()
  val readFailure = AtomicReference<IOException?>()
  var timedOut = false
  var exitCode = -1
  var primaryFailure: Throwable? = null
  var cleanupFailure: Throwable? = null
  var outputThread: Thread? = null
  var inputThread: Thread? = null
  val inputFailure = AtomicReference<Throwable?>()
  val ownedDescendants = linkedSetOf<ProcessHandle>()
  fun recordCleanupFailure(failure: Throwable) {
    cleanupFailure = cleanupFailure?.also { existing -> existing.addSuppressed(failure) } ?: failure
  }
  fun captureOwnedDescendants() {
    runCatching {
      ownedDescendants += process.toHandle().descendants().toList()
    }.onFailure(::recordCleanupFailure)
  }
  fun destroyProcessTree() {
    destroyOwnedProcessTree(process, ownedDescendants)
  }
  try {
    outputThread = try {
      thread(start = true, name = "skill-bill-git-output") {
        try {
          process.inputStream.bufferedReader().use { reader ->
            output.append(reader.readText())
          }
        } catch (error: IOException) {
          readFailure.compareAndSet(null, error)
        }
      }
    } catch (failure: Throwable) {
      primaryFailure = failure
      throw failure
    }
    if (stdin != null) {
      inputThread = try {
        thread(start = true, name = "skill-bill-git-input") {
          try {
            process.outputStream.use { stream -> stream.write(stdin) }
          } catch (error: Throwable) {
            inputFailure.compareAndSet(null, error)
          }
        }
      } catch (error: Throwable) {
        primaryFailure = error
        if (error is IOException) {
          readFailure.set(error)
        }
        throw error
      }
      val inputThreadRef = inputThread
      if (!joinWithDeadline(inputThreadRef, operationDeadlineNanos)) {
        timedOut = true
        captureOwnedDescendants()
        runCatching { destroyProcessTree() }
          .onFailure(::recordCleanupFailure)
      }
      inputFailure.get()?.let { error ->
        primaryFailure = error
        if (error is IOException) {
          readFailure.set(error)
        } else {
          throw error
        }
      }
    } else {
      runCatching { process.outputStream.close() }
        .onFailure(::recordCleanupFailure)
    }
    if (primaryFailure != null && !timedOut) {
      runCatching { destroyProcessTree() }
        .onFailure(::recordCleanupFailure)
    }
    if (primaryFailure == null) {
      try {
        while (process.isAlive) {
          captureOwnedDescendants()
          val remainingWaitNanos = operationDeadlineNanos - System.nanoTime()
          if (remainingWaitNanos <= 0L) {
            timedOut = true
            break
          }
          process.waitFor(
            minOf(remainingWaitNanos, TimeUnit.MILLISECONDS.toNanos(50)),
            TimeUnit.NANOSECONDS,
          )
        }
        if (!timedOut && !process.isAlive) {
          exitCode = process.exitValue()
        }
      } catch (interrupted: InterruptedException) {
        primaryFailure = interrupted
        runCatching { destroyProcessTree() }
          .onFailure(interrupted::addSuppressed)
        throw interrupted
      }
    }
    if (timedOut) {
      runCatching { destroyProcessTree() }
        .onFailure(::recordCleanupFailure)
    } else if (primaryFailure == null) {
      runCatching { destroyProcessTree() }
        .onFailure(::recordCleanupFailure)
    }
    val outputSettlementDeadlineNanos = minOf(
      operationDeadlineNanos,
      System.nanoTime() + TimeUnit.SECONDS.toNanos(GIT_PROCESS_CLEANUP_BUDGET_SECONDS),
    )
    val outputThreadRef = requireNotNull(outputThread)
    val settled = closeInputAndJoin(process, outputThreadRef, outputSettlementDeadlineNanos)
    if (!settled) {
      readFailure.compareAndSet(
        null,
        IOException("git output capture did not settle before deadline"),
      )
    }
    if (Thread.currentThread().isInterrupted) {
      val interrupted = InterruptedException()
      primaryFailure = interrupted
      runCatching { destroyProcessTree() }
        .onFailure(interrupted::addSuppressed)
      throw interrupted
    }
  } catch (interrupted: InterruptedException) {
    if (primaryFailure == null) {
      primaryFailure = interrupted
    }
    throw interrupted
  } finally {
    fun attemptCleanup(action: () -> Unit) {
      runCatching(action).exceptionOrNull()?.let(::recordCleanupFailure)
    }

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
        primaryFailure != null -> primaryFailure.addSuppressed(failure)
        readFailure.get() != null -> readFailure.get()!!.addSuppressed(failure)
        timedOut -> readFailure.compareAndSet(
          null,
          IOException("git process cleanup failed").also { it.addSuppressed(failure) },
        )
      }
    }
  }
  if (primaryFailure == null && !timedOut && readFailure.get() == null) {
    cleanupFailure?.let { failure -> throw failure }
  }
  return GitProcessResult(
    output = output.toString().trim(),
    readFailure = readFailure.get(),
    timedOut = timedOut,
    exitCode = if (timedOut) -1 else exitCode,
  )
}

internal fun destroyOwnedProcessTree(
  process: Process,
  knownDescendants: Set<ProcessHandle> = emptySet(),
) {
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

internal fun closeInputAndJoin(
  process: Process,
  outputThread: Thread,
  joinDeadlineNanos: Long,
): Boolean {
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
