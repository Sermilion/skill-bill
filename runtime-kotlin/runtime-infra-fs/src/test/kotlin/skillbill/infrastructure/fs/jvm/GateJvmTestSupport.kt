package skillbill.infrastructure.fs.jvm

import skillbill.ports.diagnostics.NoopRuntimeDiagnostics
import java.nio.file.Files
import java.nio.file.Path

internal fun testGateJvmResolver(): GateJvmResolver = GateJvmResolver(NoopRuntimeDiagnostics)

internal fun hostPath(): String = System.getenv(GateJvmEnvironmentKeys.PATH) ?: "/usr/bin:/bin"

internal fun writeJdkShapedHome(root: Path, javaVersion: String = "21.0.2"): Path {
  Files.createDirectories(root.resolve("bin"))
  Files.writeString(root.resolve("release"), "JAVA_VERSION=\"$javaVersion\"\n")
  val java = root.resolve("bin/java")
  Files.writeString(java, "#!/bin/sh\nexit 0\n")
  java.toFile().setExecutable(true)
  return root
}
