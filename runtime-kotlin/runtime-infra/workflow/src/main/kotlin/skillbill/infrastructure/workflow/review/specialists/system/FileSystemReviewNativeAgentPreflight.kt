
package skillbill.infrastructure.workflow.review.specialists.system
import me.tatarka.inject.annotations.Inject
import skillbill.error.shellcontent.MissingInstalledNativeAgentError
import skillbill.infrastructure.contracts.sha256HexOfFile
import skillbill.infrastructure.host.jvm.resolveEnvironmentMap
import skillbill.infrastructure.skills.install.nativeagent.install.agent.parseEmbeddedLogicalName
import skillbill.infrastructure.skills.install.nativeagent.inventory.NativeAgentLinkInventory
import skillbill.infrastructure.skills.install.nativeagent.inventory.NativeAgentLinkInventoryEntry
import skillbill.infrastructure.skills.nativeagent.rendering.NativeAgentProvider
import skillbill.infrastructure.workflow.decomposition.entry
import skillbill.infrastructure.workflow.decomposition.name
import skillbill.infrastructure.workflow.decomposition.read
import skillbill.infrastructure.workflow.feature.request
import skillbill.infrastructure.workflow.featuretask.request
import skillbill.infrastructure.workflow.filesystem.path
import skillbill.infrastructure.workflow.git.goal.first
import skillbill.infrastructure.workflow.git.goal.reason
import skillbill.infrastructure.workflow.git.scoped.first
import skillbill.infrastructure.workflow.git.scoped.read
import skillbill.infrastructure.workflow.git.standard.path
import skillbill.infrastructure.workflow.git.suppression.path
import skillbill.infrastructure.workflow.git.workflow.path
import skillbill.infrastructure.workflow.git.workflow.read
import skillbill.infrastructure.workflow.review.broker.assignment
import skillbill.infrastructure.workflow.review.broker.first
import skillbill.infrastructure.workflow.review.broker.path
import skillbill.infrastructure.workflow.review.broker.request
import skillbill.infrastructure.workflow.review.specialists.coordinate.entry
import skillbill.infrastructure.workflow.review.specialists.review.entry
import skillbill.install.model.SupportedAgent
import skillbill.model.EnvironmentContext
import skillbill.ports.review.launch.ReviewNativeAgentPreflightPort
import skillbill.ports.review.model.ReviewNativeAgentPreflightRequest
import java.nio.file.Files
import java.nio.file.Path

@Inject
class FileSystemReviewNativeAgentPreflight(
  private val environment: EnvironmentContext,
) : ReviewNativeAgentPreflightPort {
  override fun verify(request: ReviewNativeAgentPreflightRequest) {
    val home = environment.userHome

    val inventory = NativeAgentLinkInventory.read(home, emptyList())
    request.assignments.distinct().forEach { assignment ->
      val agentId = assignment.agentId
      val logicalName = assignment.logicalName
      val provider = provider(agentId) ?: throw MissingInstalledNativeAgentError(
        logicalName,
        agentId,
        environment.userHome.toString(),
        "provider does not support native-agent selection",
        REPAIR_COMMAND,
      )
      val entries = inventory.filter { it.provider == provider.name.lowercase() && it.logicalName == logicalName }
      if (entries.isEmpty()) {
        fail(
          logicalName,
          provider,
          provider.homeAgentDirs(home).first().resolve(provider.fileName(logicalName)),
          "managed inventory entry is missing",
        )
      }
      val activePaths = activeProviderDirs(provider, home)
        .map { it.resolve(provider.fileName(logicalName)).toAbsolutePath().normalize() }.toSet()
      if (activePaths.isEmpty()) {
        fail(
          logicalName,
          provider,
          provider.homeAgentDirs(home).first().resolve(provider.fileName(logicalName)),
          "active provider directory is missing",
        )
      }
      val applicable = entries.filter { it.installedPath.normalize() in activePaths }
      if (applicable.map { it.installedPath.normalize() }.toSet() != activePaths) {
        fail(
          logicalName,
          provider,
          provider.homeAgentDirs(home).first().resolve(provider.fileName(logicalName)),
          "managed inventory must contain one entry per applicable provider path",
        )
      }
      applicable.forEach { verifyEntry(it, provider) }
    }
  }

  private fun verifyEntry(entry: NativeAgentLinkInventoryEntry, provider: NativeAgentProvider) {
    val installed = entry.installedPath
    if (!Files.isSymbolicLink(installed)) fail(entry.logicalName, provider, installed, "managed link is missing")
    val resolved = runCatching { installed.toRealPath() }
      .getOrElse { fail(entry.logicalName, provider, installed, "managed link is dangling or unreadable", it) }
    val target = runCatching { entry.cacheTargetPath.toRealPath() }
      .getOrElse { fail(entry.logicalName, provider, installed, "managed cache artifact is missing", it) }
    if (resolved != target) {
      fail(
        entry.logicalName,
        provider,
        installed,
        "managed link does not resolve to the recorded cache artifact",
      )
    }
    if (!Files.isReadable(
        resolved,
      )
    ) {
      fail(entry.logicalName, provider, installed, "managed cache artifact is unreadable")
    }
    if (parseEmbeddedLogicalName(resolved, provider.supportedAgent) != entry.logicalName) {
      fail(entry.logicalName, provider, installed, "artifact logical name does not match the planned worker")
    }
    if (sha256HexOfFile(resolved) != entry.contentDigest
    ) {
      fail(entry.logicalName, provider, installed, "artifact content digest is stale")
    }
  }

  private fun provider(agentId: String): NativeAgentProvider? =
    runCatching { NativeAgentProvider.forSupportedAgent(SupportedAgent.fromWire(agentId)) }.getOrNull()

  private fun activeProviderDirs(provider: NativeAgentProvider, home: Path): List<Path> {
    val env = resolveEnvironmentMap(environment.environment)
    return provider.activeHomeAgentDirs(home, env)
  }

  private fun fail(
    logicalName: String,
    provider: NativeAgentProvider,
    path: Path,
    reason: String,
    cause: Throwable? = null,
  ): Nothing = throw MissingInstalledNativeAgentError(
    logicalName,
    provider.name.lowercase(),
    path.toString(),
    reason,
    REPAIR_COMMAND,
    cause,
  )

  private companion object {
    const val REPAIR_COMMAND = "skill-bill install apply"
  }
}
