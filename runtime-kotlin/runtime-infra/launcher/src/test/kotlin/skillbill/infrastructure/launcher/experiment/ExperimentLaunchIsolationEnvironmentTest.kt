package skillbill.infrastructure.launcher.experiment

import skillbill.contracts.experiment.launcher.ExperimentLauncherCapabilityWire
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExperimentLaunchIsolationEnvironmentTest {
  @Test
  fun `control path isolation strips codegraph segments from path`() {
    val toolsBin = Path.of("/home/user/.skill-bill/tools/codegraph/v1.6.0")
    val result = ExperimentLaunchIsolationEnvironment.apply(
      ExperimentLaunchIsolationRequest(
        treatmentEnabled = false,
        treatmentCapabilities = setOf("codegraph"),
        treatmentCapabilitiesDenied = setOf("codegraph"),
        requiredLauncherCapabilities = setOf(
          ExperimentLauncherCapabilityWire.PATH_ISOLATION,
          ExperimentLauncherCapabilityWire.STRIP_INHERITED_MCP_SERVERS,
        ),
        repoRoot = Path.of("/repo"),
        managedToolsBin = toolsBin,
        inheritedPath = "/usr/bin:/home/user/.skill-bill/tools/codegraph/v1.6.0:/bin",
      ),
    )

    assertFalse(result.inheritEnvironment)
    assertTrue(result.stripInheritedMcpServers)
    assertFalse(result.environment["PATH"]!!.contains("codegraph", ignoreCase = true))
  }

  @Test
  fun `ordinary launch does not alter telemetry environment`() {
    val result = ExperimentLaunchIsolationEnvironment.apply(
      ExperimentLaunchIsolationRequest(
        treatmentEnabled = false,
        treatmentCapabilities = emptySet(),
        treatmentCapabilitiesDenied = emptySet(),
        requiredLauncherCapabilities = emptySet(),
        repoRoot = Path.of("/repo"),
        inheritedPath = "/usr/bin:/bin",
      ),
    )

    assertFalse(result.environment.containsKey("CODEGRAPH_TELEMETRY"))
    assertFalse(result.environment.containsKey("DO_NOT_TRACK"))
  }
}
