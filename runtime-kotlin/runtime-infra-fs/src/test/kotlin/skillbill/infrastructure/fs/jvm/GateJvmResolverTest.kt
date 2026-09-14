package skillbill.infrastructure.fs.jvm

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GateJvmResolverTest {
  @Test
  fun `a qualifying JDK inside the runtime image root is dropped before the guard decides`() {
    val imageRoot = Files.createTempDirectory("gate-jvm-image")
    val outsideImage = Files.createTempDirectory("gate-jvm-host")
    try {
      val leaked = writeJdkShapedHome(Files.createDirectories(imageRoot.resolve("jdk")))
      val explicit = writeJdkShapedHome(outsideImage)

      val sanitized = withoutRuntimeImageJavaHomes(
        mapOf(
          GateJvmEnvironmentKeys.JAVA_HOME to leaked.toString(),
          GateJvmEnvironmentKeys.SKILL_BILL_JAVA_HOME to explicit.toString(),
          GateJvmEnvironmentKeys.PATH to hostPath(),
        ),
        imageRoot.toRealPath(),
      )

      assertNull(sanitized[GateJvmEnvironmentKeys.JAVA_HOME])
      assertEquals(explicit.toString(), sanitized[GateJvmEnvironmentKeys.SKILL_BILL_JAVA_HOME])
      assertEquals(hostPath(), sanitized[GateJvmEnvironmentKeys.PATH])
    } finally {
      imageRoot.toFile().deleteRecursively()
      outsideImage.toFile().deleteRecursively()
    }
  }

  @Test
  fun `SKILL_BILL_JAVA_HOME selects the gate JVM only while the guard accepts it`() {
    val accepted = Files.createTempDirectory("gate-jvm-explicit")
    val inherited = Files.createTempDirectory("gate-jvm-inherited")
    val rejected = Files.createTempDirectory("gate-jvm-rejected")
    try {
      writeJdkShapedHome(accepted)
      writeJdkShapedHome(inherited)
      val resolver = testGateJvmResolver()

      assertEquals(
        GateJvmDisposition.Export(accepted.toString()),
        resolver.resolve(guardInput(accepted, inherited)),
      )
      assertEquals(
        GateJvmDisposition.Export(inherited.toString()),
        resolver.resolve(guardInput(rejected, inherited)),
      )
    } finally {
      listOf(accepted, inherited, rejected).forEach { it.toFile().deleteRecursively() }
    }
  }

  @Test
  fun `an unresolved gate JVM clears JAVA_HOME at the launch surface instead of failing the launch`() {
    val environment = mutableMapOf(
      GateJvmEnvironmentKeys.JAVA_HOME to "/opt/skill-bill/runtime",
      GateJvmEnvironmentKeys.PATH to hostPath(),
    )

    GateJvmDisposition.Unresolved(rejectedCandidate = "/opt/skill-bill/runtime", requiredMajor = "21")
      .applyTo(environment)

    assertNull(environment[GateJvmEnvironmentKeys.JAVA_HOME])
    assertEquals(hostPath(), environment[GateJvmEnvironmentKeys.PATH])
  }

  private fun guardInput(skillBillJavaHome: Path, javaHome: Path): Map<String, String> = mapOf(
    GateJvmEnvironmentKeys.SKILL_BILL_JAVA_HOME to skillBillJavaHome.toString(),
    GateJvmEnvironmentKeys.JAVA_HOME to javaHome.toString(),
    GateJvmEnvironmentKeys.PATH to hostPath(),
  )
}
