package skillbill.infrastructure.fs

import skillbill.infrastructure.fs.launcher.process.GIT_PROCESS_CLEANUP_BUDGET_SECONDS
import skillbill.infrastructure.fs.launcher.process.GIT_TIMEOUT_SECONDS
import skillbill.infrastructure.fs.launcher.process.GitProcessResult
import skillbill.infrastructure.fs.launcher.process.gitTimeoutSeconds
import skillbill.infrastructure.fs.launcher.process.invokeGitProcessWithBoundedLines
import skillbill.infrastructure.fs.launcher.process.runGitCommandWithStdin
import skillbill.infrastructure.fs.launcher.process.runGitProcess
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GitProcessLifetimeBehaviorTest {
  @Test
  fun `runGitProcess completes ordinary status in a temp repo`() {
    val root = createTempGitRepo()
    try {
      val result = runGitProcess(root, listOf("status", "--porcelain"))
      assertEquals(0, result.exitCode)
      assertFalse(result.timedOut)
      assertEquals(null, result.readFailure)
    } finally {
      root.toFile().deleteRecursively()
    }
  }

  @Test
  fun `runGitCommandWithStdin survives concurrent stdout fill`() {
    val root = createTempGitRepo()
    try {
      val line = "tracked\n"
      val repeats = 256 * 1024
      val payload = buildString(repeats * line.length) {
        repeat(repeats) {
          append(line)
          append('\u0000')
        }
      }.toByteArray()
      val started = System.nanoTime()
      val outcome = runGitCommandWithStdin(
        root,
        listOf(
          "-c",
          "alias.fill=!while read -r line; do printf '%s\\n' \"\$line\"; done",
          "fill",
        ),
        payload,
      )
      val elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started)
      assertTrue(
        elapsedSeconds <= gitTimeoutSeconds(listOf("fill")) + GIT_PROCESS_CLEANUP_BUDGET_SECONDS + 5L,
        "pipe backpressure exceeded git timeout budget: ${elapsedSeconds}s",
      )
      when (outcome) {
        is WorkflowGitOperationResult.Ok -> assertContains(outcome.value.orEmpty(), "tracked")
        is WorkflowGitOperationResult.Failed -> assertTrue(false, outcome.error)
      }
    } finally {
      root.toFile().deleteRecursively()
    }
  }

  @Test
  fun `runGitProcess reports timedOut for a blocking alias`() {
    val root = createTempGitRepo()
    val pidFile = root.resolve("timeout.pid")
    var child: ProcessHandle? = null
    try {
      val started = System.nanoTime()
      val captured = runGitProcessWithCapturedChild(
        root,
        listOf("-c", "alias.block=!echo \$\$ > $pidFile; exec sleep 120", "block"),
      )
      val result = captured.result
      child = captured.child
      val elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started)
      assertTrue(result.timedOut)
      assertTrue(Files.exists(pidFile))
      assertFalse(awaitDead(requireNotNull(child)), "timed-out git child still alive")
      assertTrue(
        elapsedSeconds <= gitTimeoutSeconds(listOf("block")) + GIT_PROCESS_CLEANUP_BUDGET_SECONDS + 5L,
      )
    } finally {
      child?.destroyForcibly()
      destroyProcessFrom(pidFile)
      root.toFile().deleteRecursively()
    }
  }

  @Test
  fun `interrupting runGitProcess tears down owned git and hook child`() {
    val root = Files.createTempDirectory("skill-bill-248-git-life-")
    val pidFile = root.resolve("child.pid")
    val failure = AtomicReference<Throwable?>()
    val finished = CountDownLatch(1)
    val worker = thread(name = "skill-bill-248-git-interrupt") {
      try {
        runGitProcess(
          root,
          listOf("-c", "alias.probe=!echo \$\$ > $pidFile; exec sleep 120", "probe"),
        )
      } catch (error: Throwable) {
        failure.set(error)
      } finally {
        finished.countDown()
      }
    }
    var child: ProcessHandle? = null
    var git: ProcessHandle? = null
    try {
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
      while (!Files.exists(pidFile) && System.nanoTime() < deadline) {
        Thread.sleep(10)
      }
      assertTrue(Files.exists(pidFile), "expected hook child pid file")
      val ownedChild = requireNotNull(processHandleFrom(pidFile))
      val gitProcess = ownedChild.parent().orElseThrow()
      child = ownedChild
      git = gitProcess
      worker.interrupt()
      assertTrue(finished.await(5, TimeUnit.SECONDS))
      assertTrue(failure.get() is InterruptedException)
      val cleanupDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(GIT_PROCESS_CLEANUP_BUDGET_SECONDS + 2)
      var cleanedUp = false
      while (System.nanoTime() < cleanupDeadline) {
        if (!ownedChild.isAlive && !gitProcess.isAlive) {
          cleanedUp = true
          break
        }
        Thread.sleep(20)
      }
      assertTrue(cleanedUp, "owned git or hook child still alive after cleanup budget")
      assertFalse(
        Thread.getAllStackTraces().keys.any { thread ->
          thread.name in setOf("skill-bill-git-output", "skill-bill-git-input") && thread.isAlive
        },
        "git process worker still alive after interruption cleanup",
      )
    } finally {
      child?.destroyForcibly()
      git?.destroyForcibly()
      destroyProcessFrom(pidFile)
      worker.interrupt()
      worker.join(TimeUnit.SECONDS.toMillis(GIT_PROCESS_CLEANUP_BUDGET_SECONDS + 2))
      root.toFile().deleteRecursively()
    }
  }

  @Test
  fun `runGitProcess does not succeed when stdout pipe outlives git exit`() {
    val root = createTempGitRepo()
    val pidFile = root.resolve("inherited.pid")
    var descendant: ProcessHandle? = null
    try {
      val started = System.nanoTime()
      val captured = runGitProcessWithCapturedChild(
        root,
        listOf(
          "-c",
          "alias.leak=!perl -e 'my \$pid=fork; if (\$pid == 0) { " +
            "open(F, \">\", \"$pidFile\"); print F \"\$\$\"; close(F); sleep 3600; } sleep 1; exit 0'",
          "leak",
        ),
        pidFile = pidFile,
      )
      val result = captured.result
      val elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started)
      descendant = captured.child
      assertTrue(
        elapsedSeconds <= gitTimeoutSeconds(listOf("leak")) + GIT_PROCESS_CLEANUP_BUDGET_SECONDS + 5L,
        "inherited pipe join exceeded bounded deadline: ${elapsedSeconds}s",
      )
      assertTrue(result.readFailure != null || result.timedOut || result.exitCode != 0)
      assertFalse(result.exitCode == 0 && result.readFailure == null && !result.timedOut)
      assertFalse(awaitDead(requireNotNull(descendant)), "inherited pipe child still alive after git exit")
    } finally {
      descendant?.destroyForcibly()
      destroyProcessFrom(pidFile)
      root.toFile().deleteRecursively()
    }
  }

  @Test
  fun `short lived git descendants can finish within the cleanup budget`() {
    val root = createTempGitRepo()
    val pidFile = root.resolve("settling.pid")
    var child: ProcessHandle? = null
    try {
      val captured = runGitProcessWithCapturedChild(
        root,
        listOf(
          "-c",
          "alias.settle=!perl -e 'my \$pid=fork; if (\$pid == 0) { " +
            "open(F, \">\", \"$pidFile\"); print F \"\$\$\"; close(F); sleep 2; } else { sleep 1; } exit 0'",
          "settle",
        ),
        pidFile = pidFile,
      )
      child = captured.child

      assertEquals(0, captured.result.exitCode)
      assertEquals(null, captured.result.readFailure)
      assertFalse(captured.result.timedOut)
      assertFalse(awaitDead(requireNotNull(child)))
    } finally {
      child?.destroyForcibly()
      destroyProcessFrom(pidFile)
      root.toFile().deleteRecursively()
    }
  }

  @Test
  fun `stdin write failure keeps IOException as readFailure`() {
    val root = createTempGitRepo()
    val pidFile = root.resolve("stdin-failure.pid")
    val releaseFile = root.resolve("stdin-failure.release")
    var child: ProcessHandle? = null
    try {
      val payload = ByteArray(8 * 1024 * 1024) { 'x'.code.toByte() }
      val captured = runGitProcessWithCapturedChild(
        root,
        listOf(
          "-c",
          "alias.fast=!echo \$\$ > $pidFile; while [ ! -f $releaseFile ]; do " +
            "sleep 0.01; done; exec 0<&-; exec sleep 120",
          "fast",
        ),
        payload,
        releaseFile,
        pidFile,
      )
      val result = captured.result
      child = captured.child
      assertNotNull(result.readFailure)
      assertFalse(awaitDead(requireNotNull(child)), "stdin failure left the owned child alive")
    } finally {
      child?.destroyForcibly()
      destroyProcessFrom(pidFile)
      root.toFile().deleteRecursively()
    }
  }

  @Test
  fun `bounded line git capture reports timedOut for a blocking alias`() {
    val root = createTempGitRepo()
    val pidFile = root.resolve("bounded-lines-timeout.pid")
    var child: ProcessHandle? = null
    try {
      val started = System.nanoTime()
      val result = invokeGitProcessWithBoundedLines(
        repoRoot = root,
        args = listOf("-c", "alias.block=!echo \$\$ > $pidFile; exec sleep 120", "block"),
        readLineMaxBytes = 4_096,
        shouldStopReading = { false },
        onLine = {},
      )
      if (Files.exists(pidFile)) {
        child = readProcessHandle(pidFile)
      }
      val elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started)
      assertTrue(result.timedOut)
      assertTrue(
        elapsedSeconds <= gitTimeoutSeconds(listOf("block")) + GIT_PROCESS_CLEANUP_BUDGET_SECONDS + 5L,
        "bounded line capture exceeded git timeout budget: ${elapsedSeconds}s",
      )
      child?.let { handle -> assertFalse(awaitDead(handle), "timed out bounded line git left child alive") }
    } finally {
      child?.destroyForcibly()
      destroyProcessFrom(pidFile)
      root.toFile().deleteRecursively()
    }
  }

  @Test
  fun `binary diff output remains a failed git result with captured patch text`() {
    val root = createTempGitRepo()
    try {
      Files.write(root.resolve("binary.bin"), byteArrayOf(0, 1, 2, 3, 0, 255.toByte()))

      val result = runGitProcess(root, listOf("diff", "--binary", "--no-index", "/dev/null", "binary.bin"))

      assertEquals(1, result.exitCode)
      assertEquals(null, result.readFailure)
      assertContains(result.output, "GIT binary patch")
    } finally {
      root.toFile().deleteRecursively()
    }
  }

  private fun createTempGitRepo(): Path {
    val root = Files.createTempDirectory("skill-bill-git-life-")
    return try {
      assertEquals(0, runGitProcess(root, listOf("init", "-q")).exitCode)
      assertEquals(0, runGitProcess(root, listOf("config", "user.email", "git-life@test")).exitCode)
      assertEquals(0, runGitProcess(root, listOf("config", "user.name", "git-life")).exitCode)
      root
    } catch (error: Throwable) {
      root.toFile().deleteRecursively()
      throw error
    }
  }

  private fun processHandleFrom(pidFile: Path): ProcessHandle? {
    assertTrue(Files.exists(pidFile), "expected process pid file")
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(GIT_PROCESS_CLEANUP_SECONDS_FOR_TEST)
    while (System.nanoTime() < deadline) {
      readProcessHandle(pidFile)?.let { return it }
      Thread.sleep(10)
    }
    return readProcessHandle(pidFile)
  }

  private fun readProcessHandle(pidFile: Path): ProcessHandle? {
    if (!Files.exists(pidFile)) return null
    return Files.readString(pidFile).trim().toLongOrNull()?.let { pid ->
      ProcessHandle.of(pid).orElse(null)
    }
  }

  private fun runGitProcessWithCapturedChild(
    root: Path,
    args: List<String>,
    stdin: ByteArray? = null,
    releaseFile: Path? = null,
    pidFile: Path = root.resolve("timeout.pid"),
  ): CapturedGitProcess {
    val result = AtomicReference<GitProcessResult?>()
    val failure = AtomicReference<Throwable?>()
    val worker = thread(name = "skill-bill-git-capture") {
      try {
        result.set(runGitProcess(root, args, stdin))
      } catch (error: Throwable) {
        failure.set(error)
      }
    }
    try {
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
      var child: ProcessHandle? = null
      while (child == null && System.nanoTime() < deadline) {
        if (Files.exists(pidFile)) {
          child = processHandleFrom(pidFile)
        } else {
          Thread.sleep(10)
        }
      }
      assertNotNull(child, "expected a live child before runGitProcess returned")
      releaseFile?.let { Files.writeString(it, "release") }
      worker.join(TimeUnit.SECONDS.toMillis(GIT_TIMEOUT_SECONDS + GIT_PROCESS_CLEANUP_BUDGET_SECONDS + 5))
      assertFalse(worker.isAlive, "runGitProcess worker did not settle")
      failure.get()?.let { throw it }
      return CapturedGitProcess(requireNotNull(result.get()), requireNotNull(child))
    } catch (error: Throwable) {
      worker.interrupt()
      worker.join(TimeUnit.SECONDS.toMillis(GIT_PROCESS_CLEANUP_SECONDS_FOR_TEST))
      throw error
    }
  }

  private fun destroyProcessFrom(pidFile: Path) {
    readProcessHandle(pidFile)?.destroyForcibly()
  }

  private fun awaitDead(handle: ProcessHandle?): Boolean {
    if (handle == null) return true
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(GIT_PROCESS_CLEANUP_BUDGET_SECONDS + 2)
    while (System.nanoTime() < deadline && handle.isAlive) {
      Thread.sleep(20)
    }
    return handle.isAlive
  }

  private data class CapturedGitProcess(
    val result: GitProcessResult,
    val child: ProcessHandle,
  )

  private companion object {
    const val GIT_PROCESS_CLEANUP_SECONDS_FOR_TEST = GIT_PROCESS_CLEANUP_BUDGET_SECONDS + 2
  }
}
