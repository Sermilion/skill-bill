package skillbill.infrastructure.host.jvm

import skillbill.ports.diagnostics.NoopRuntimeDiagnostics
import java.nio.file.Files
import java.nio.file.Path

fun testGateJvmResolver(): GateJvmResolver = GateJvmResolver(NoopRuntimeDiagnostics, JdkHostPlatformPort)

fun hostPath(): String = System.getenv(GateJvmEnvironmentKeys.PATH) ?: "/usr/bin:/bin"

fun writeJdkShapedHome(
  root: Path,
  javaVersion: String = "21.0.2",
): Path {
  Files.createDirectories(root.resolve("bin"))
  Files.writeString(root.resolve("release"), "JAVA_VERSION=\"$javaVersion\"\n")
  val java = root.resolve("bin/java")
  Files.writeString(java, "#!/bin/sh\nexit 0\n")
  java.toFile().setExecutable(true)
  return root
}
