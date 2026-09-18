package skillbill.infrastructure.host.jvm

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

class GateJvmGuardPackagingTest {
  @Test
  fun `the runtime ships the single authored Java guard on its own classpath`() {
    val shipped = GateJvmResolver::class.java.classLoader
      .getResourceAsStream("skillbill/infrastructure/host/jvm/skill-bill-java-guard.sh")
      ?.use { stream -> stream.readBytes() }
    assertNotNull(
      shipped,
      "Gate JVM resolution has no rule unless the guard rides inside the runtime jar; a prebuilt-release " +
        "install never touches the checkout copy install.sh sources.",
    )
    assertContentEquals(Files.readAllBytes(authoredGuard()), shipped)
  }

  private fun authoredGuard(): Path {
    val anchor = Path.of("").toAbsolutePath()
    var current: Path? = anchor
    while (current != null) {
      val candidate = current.resolve(AUTHORED_GUARD_REPO_PATH)
      if (Files.isRegularFile(candidate)) return candidate
      current = current.parent
    }
    fail("Authored Java guard is missing at $AUTHORED_GUARD_REPO_PATH walked up from $anchor.")
  }

  private companion object {
    const val AUTHORED_GUARD_REPO_PATH =
      "runtime-kotlin/build-logic/convention/src/main/resources/skill-bill-java-guard.sh"
  }
}
