package skillbill.infrastructure.workflow.validation

import org.junit.jupiter.api.Timeout
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileSystemPrCheckProcessRunnerTest {
  @Test
  @Timeout(value = 30, unit = TimeUnit.SECONDS)
  fun `a check that floods stdout still reports its exit code`() {
    val root = Files.createTempDirectory("skill-bill-pr-check-output-")
    try {
      val result =
        FileSystemPrCheckProcessRunner(Clock.systemUTC(), timeoutSeconds = 25L)
          .run("head -c 2097152 /dev/zero | tr '\\0' 'x'; exit 3", root)
      assertEquals(3, result.exitCode)
    } finally {
      root.toFile().deleteRecursively()
    }
  }

  @Test
  @Timeout(value = 60, unit = TimeUnit.SECONDS)
  fun `a timed out check leaves no descendant alive`() {
    val root = Files.createTempDirectory("skill-bill-pr-check-timeout-")
    val pidFile = root.resolve("grandchild.pid")
    try {
      val result =
        FileSystemPrCheckProcessRunner(Clock.systemUTC(), timeoutSeconds = 2L)
          .run("sh -c 'sleep 600 & echo \$! > \"$pidFile\"; wait'", root)
      assertEquals(124, result.exitCode)
      assertTrue(Files.exists(pidFile), "expected the command to spawn its grandchild before the timeout")
      assertTrue(awaitDead(grandchildHandle(pidFile)), "expected the grandchild to be gone after the timeout")
    } finally {
      grandchildHandle(pidFile)?.destroyForcibly()
      root.toFile().deleteRecursively()
    }
  }

  private fun grandchildHandle(pidFile: Path): ProcessHandle? =
    pidFile.takeIf(Files::exists)
      ?.let { Files.readString(it).trim() }
      ?.toLongOrNull()
      ?.let { pid -> ProcessHandle.of(pid).orElse(null) }

  private fun awaitDead(handle: ProcessHandle?): Boolean {
    if (handle == null) return true
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
    while (handle.isAlive && System.nanoTime() < deadline) {
      Thread.sleep(20)
    }
    return !handle.isAlive
  }
}
