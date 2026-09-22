package skillbill.infrastructure.skills.install.nativeagent.install.agent
import skillbill.infrastructure.skills.install.nativeagent.install.native.NativeAgentLinkRequest
import skillbill.infrastructure.skills.install.nativeagent.install.native.ProviderMutationJournal
import skillbill.infrastructure.skills.nativeagent.composition.NativeAgentCompositionContext
import skillbill.infrastructure.skills.nativeagent.rendering.NativeAgentProvider
import skillbill.install.model.AgentTarget
import java.nio.file.Path

internal data class NativeAgentLinkProviderBodyArgs(
  val provider: NativeAgentProvider,
  val request: NativeAgentLinkRequest,
  val targets: List<AgentTarget>,
  val resolvedHome: Path,
  val cacheRoot: Path,
  val validationRoot: Path,
  val compositionContext: NativeAgentCompositionContext,
  val effectivePackRoots: List<Path>,
  val journal: ProviderMutationJournal,
)
