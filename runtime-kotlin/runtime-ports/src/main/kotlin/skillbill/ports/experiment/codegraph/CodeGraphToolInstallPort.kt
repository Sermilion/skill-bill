package skillbill.ports.experiment.codegraph
import skillbill.ports.experiment.codegraph.model.CodeGraphInstalledTool
import skillbill.ports.experiment.codegraph.model.CodeGraphToolInstallRequest
import java.nio.file.Path

interface CodeGraphToolInstallPort {
  fun install(request: CodeGraphToolInstallRequest): CodeGraphInstalledTool

  fun resolveInstalledBinary(userHome: Path, releaseTag: String): Path?

  fun removeOwnedVersion(userHome: Path, releaseTag: String, heldReleaseTags: Set<String>): Boolean

  fun releaseOwnedProcesses(pairId: String)
}
