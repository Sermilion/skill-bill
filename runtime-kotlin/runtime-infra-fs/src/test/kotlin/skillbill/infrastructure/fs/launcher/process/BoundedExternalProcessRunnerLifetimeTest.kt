package skillbill.infrastructure.fs.launcher.process

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoundedExternalProcessRunnerLifetimeTest {
  @Test
  fun `file capture drains stderr without leaving it on a pipe`() {
    val output = Files.createTempFile("skill-bill-bounded-process-stderr-", ".out")
    try {
      val result = BoundedExternalProcessRunner.run(
        BoundedExternalProcessRequest(
          argv = listOf("sh", "-c", "printf stderr >&2"),
          redirectOutputFile = output,
          deadlineSeconds = 1,
          outputCapBytes = null,
        ),
      )
      assertEquals(0, result.exitCode)
      assertEquals("stderr", result.output)
    } finally {
      Files.deleteIfExists(output)
    }
  }

  @Test
  fun `deadline timeout removes the owned child before publishing the result`() {
    val root = Files.createTempDirectory("skill-bill-bounded-process-timeout-")
    val pidFile = root.resolve("child.pid")
    try {
      val result = BoundedExternalProcessRunner.run(
        BoundedExternalProcessRequest(
          argv = listOf("sh", "-c", "echo \$\$ > '$pidFile'; exec sleep 120"),
          deadlineSeconds = 1,
        ),
      )
      assertTrue(result.timedOut)
      assertTrue(awaitDead(processHandleFrom(pidFile)))
    } finally {
      destroyProcessFrom(pidFile)
      root.toFile().deleteRecursively()
    }
  }

  @Test
  fun `interruption removes the owned child and does not settle a capture`() {
    val root = Files.createTempDirectory("skill-bill-bounded-process-interrupt-")
    val pidFile = root.resolve("child.pid")
    val failure = AtomicReference<Throwable?>()
    val finished = CountDownLatch(1)
    val worker = thread(start = true, isDaemon = true, name = "skill-bill-bounded-process-test") {
      try {
        BoundedExternalProcessRunner.run(
          BoundedExternalProcessRequest(
            argv = listOf("sh", "-c", "echo \$\$ > '$pidFile'; exec sleep 120"),
            deadlineSeconds = 60,
          ),
        )
      } catch (error: Throwable) {
        failure.set(error)
      } finally {
        finished.countDown()
      }
    }
    try {
      awaitPid(pidFile)
      worker.interrupt()
      assertTrue(finished.await(10, TimeUnit.SECONDS))
      assertTrue(failure.get() is InterruptedException)
      assertTrue(awaitDead(processHandleFrom(pidFile)))
    } finally {
      worker.interrupt()
      worker.join(TimeUnit.SECONDS.toMillis(2))
      destroyProcessFrom(pidFile)
      root.toFile().deleteRecursively()
    }
  }

  private fun awaitPid(pidFile: Path) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while ((!Files.exists(pidFile) || Files.size(pidFile) == 0L) && System.nanoTime() < deadline) {
      Thread.sleep(10)
    }
    assertTrue(Files.exists(pidFile), "expected bounded process pid file")
  }

  private fun processHandleFrom(pidFile: Path): ProcessHandle? =
    ProcessHandle.of(Files.readString(pidFile).trim().toLong()).orElse(null)

  private fun destroyProcessFrom(pidFile: Path) {
    if (Files.exists(pidFile)) {
      processHandleFrom(pidFile)?.destroyForcibly()
    }
  }

  private fun awaitDead(handle: ProcessHandle?): Boolean {
    if (handle == null) return true
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(7)
    while (handle.isAlive && System.nanoTime() < deadline) {
      Thread.sleep(20)
    }
    return !handle.isAlive
  }
}
