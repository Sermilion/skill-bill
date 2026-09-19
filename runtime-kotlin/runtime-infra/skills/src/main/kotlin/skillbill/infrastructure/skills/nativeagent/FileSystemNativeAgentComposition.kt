package skillbill.infrastructure.skills.nativeagent
import skillbill.infrastructure.skills.externaladdon.config
import skillbill.infrastructure.skills.nativeagent.composition.NativeAgentSource
import skillbill.infrastructure.skills.nativeagent.composition.composeNativeAgentSource
import skillbill.infrastructure.skills.nativeagent.platformpack.NativeAgentPlatformPackLoader
import skillbill.infrastructure.skills.scaffold.authoring.renderAuthoredContentBody
import skillbill.infrastructure.skills.scaffold.skills
import skillbill.ports.config.RepoLocalConfigPort
import skillbill.ports.config.model.ReadRepoLocalConfigRequest
import java.nio.file.Path

internal object FileSystemNativeAgentComposition {
  fun composeNativeAgentSource(
    repoRoot: Path,
    source: NativeAgentSource,
    repoLocalConfigPort: RepoLocalConfigPort,
    packLoader: NativeAgentPlatformPackLoader = FileSystemNativeAgentPlatformPackLoader,
  ): NativeAgentSource {
    val normalizedRoot = repoRoot.toAbsolutePath().normalize()
    val budget = repoLocalConfigPort
      .readRepoLocalConfig(ReadRepoLocalConfigRequest(normalizedRoot))
      .config
      .reviewContextBudget
      .maxLaneLaunchBytes
    return composeNativeAgentSource(
      repoRoot = normalizedRoot,
      source = source,
      reviewContextBudgetBytes = budget,
      renderGovernedBody = ::renderAuthoredContentBody,
      packLoader = packLoader,
    )
  }
}
