package skillbill.infrastructure.skills.nativeagent.composition
import skillbill.infrastructure.skills.nativeagent.FileSystemNativeAgentPlatformPackLoader
import skillbill.infrastructure.skills.nativeagent.platformpack.NativeAgentPlatformPackLoader
import skillbill.infrastructure.skills.scaffold.authoring.renderAuthoredContentBody
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
    val budget =
      repoLocalConfigPort
        .readRepoLocalConfig(ReadRepoLocalConfigRequest(normalizedRoot))
        .config
        .reviewContextBudget
        .maxLaneLaunchBytes
    return composeNativeAgentSource(
      repoRoot = normalizedRoot,
      source = source,
      context =
        NativeAgentCompositionContext(
          reviewContextBudgetBytes = budget,
          renderGovernedBody = ::renderAuthoredContentBody,
          packLoader = packLoader,
        ),
    )
  }
}
