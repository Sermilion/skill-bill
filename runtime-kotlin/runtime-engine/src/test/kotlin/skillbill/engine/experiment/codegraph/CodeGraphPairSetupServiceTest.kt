package skillbill.engine.experiment.codegraph

import skillbill.infrastructure.host.experiment.codegraph.InMemoryCodeGraphUsageLedger
import skillbill.ports.experiment.codegraph.CodeGraphToolInstallPort
import skillbill.ports.experiment.codegraph.model.CodeGraphInstalledTool
import skillbill.ports.experiment.codegraph.model.CodeGraphPairSetupRequest
import skillbill.ports.experiment.codegraph.model.CodeGraphToolInstallRequest
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CodeGraphPairSetupServiceTest {
  @Test
  fun `unselected experiment does not provision or record graph setup`() {
    var installCalled = false
    val ledger = InMemoryCodeGraphUsageLedger()
    val service = CodeGraphPairSetupService(
      toolInstallPort = object : CodeGraphToolInstallPort {
        override fun install(request: CodeGraphToolInstallRequest): CodeGraphInstalledTool {
          installCalled = true
          error("unselected CodeGraph must not install")
        }

        override fun resolveInstalledBinary(userHome: Path, releaseTag: String): Path? = null

        override fun removeOwnedVersion(userHome: Path, releaseTag: String, heldReleaseTags: Set<String>): Boolean =
          false

        override fun releaseOwnedProcesses(pairId: String) = Unit
      },
      usageLedger = ledger,
    )

    val result = service.provisionAfterConfirmation(
      CodeGraphPairSetupRequest(
        pairId = "pair-1",
        userHome = Path.of("/tmp"),
        selectedExperimentNames = listOf("other-experiment"),
      ),
    )

    assertFalse(result.provisioned)
    assertFalse(installCalled)
    assertTrue(ledger.snapshot("pair-1").notExercised)
  }

  @Test
  fun `selected setup forwards the pinned release and explicit override`() {
    var observed: CodeGraphToolInstallRequest? = null
    val ledger = InMemoryCodeGraphUsageLedger()
    val override = Path.of("/tmp/codegraph")
    val service = CodeGraphPairSetupService(
      toolInstallPort = object : CodeGraphToolInstallPort {
        override fun install(request: CodeGraphToolInstallRequest): CodeGraphInstalledTool {
          observed = request
          return CodeGraphInstalledTool("v1.6.0", override, "linux-x64", "a".repeat(64), "local_override")
        }

        override fun resolveInstalledBinary(userHome: Path, releaseTag: String): Path? = null

        override fun removeOwnedVersion(userHome: Path, releaseTag: String, heldReleaseTags: Set<String>): Boolean =
          false

        override fun releaseOwnedProcesses(pairId: String) = Unit
      },
      usageLedger = ledger,
    )

    val result = service.provisionAfterConfirmation(
      CodeGraphPairSetupRequest(
        pairId = "pair-2",
        userHome = Path.of("/tmp"),
        selectedExperimentNames = listOf("codegraph"),
        pinnedReleaseTag = "v1.6.0",
        localExecutableOverride = override,
      ),
    )

    assertTrue(result.provisioned)
    assertEquals("v1.6.0", observed?.pinnedReleaseTag)
    assertEquals(override, observed?.localExecutableOverride)
    assertEquals("local_override", result.installedTool?.provenance)
  }
}
