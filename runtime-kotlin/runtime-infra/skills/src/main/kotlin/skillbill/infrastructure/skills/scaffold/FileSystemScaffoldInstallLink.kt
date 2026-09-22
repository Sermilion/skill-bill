package skillbill.infrastructure.skills.scaffold
import me.tatarka.inject.annotations.Inject
import skillbill.infrastructure.skills.install.plan.InstallContext
import skillbill.infrastructure.skills.install.plan.detectAgents
import skillbill.infrastructure.skills.install.plan.installSkill
import skillbill.infrastructure.skills.install.plan.resolveInstallHome
import skillbill.infrastructure.skills.install.plan.uninstallTargets
import skillbill.infrastructure.skills.scaffold.platformpack.loader.discoverPlatformPackManifests
import skillbill.ports.scaffold.install.ScaffoldInstallLinkPort
import skillbill.ports.scaffold.install.model.ScaffoldInstallLinkRequest
import skillbill.ports.scaffold.install.model.ScaffoldInstallLinkResult
import skillbill.ports.system.HostPlatformPort
import java.nio.file.Files
import java.nio.file.Path

@Inject
class FileSystemScaffoldInstallLink(
  private val hostPlatform: HostPlatformPort,
) : ScaffoldInstallLinkPort {
  override fun applyInstallLinks(request: ScaffoldInstallLinkRequest): ScaffoldInstallLinkResult {
    val home = resolveInstallHome(null, hostPlatform)
    val agents = detectAgents(home, hostPlatform.resolveEnvironment())
    val packsRoot = request.repoRoot.resolve("platform-packs")
    val manifests = if (Files.isDirectory(packsRoot)) discoverPlatformPackManifests(packsRoot) else emptyList()
    val context = InstallContext(repoRoot = request.repoRoot, home = home, manifests = manifests)
    val targets =
      request.installPaths.flatMap { installPath ->
        installSkill(installPath, agents, context = context).linkPaths
      }
    return ScaffoldInstallLinkResult(installTargets = targets)
  }

  override fun rollbackInstallTargets(installTargets: List<Path>) {
    uninstallTargets(installTargets)
  }
}
