package skillbill.infrastructure.skills.install.scaffold
import me.tatarka.inject.annotations.Inject
import skillbill.infrastructure.skills.install.plan.InstallContext
import skillbill.infrastructure.skills.install.plan.detectAgents
import skillbill.infrastructure.skills.install.plan.installSkill
import skillbill.infrastructure.skills.install.plan.uninstallTargets
import skillbill.infrastructure.skills.scaffold.platformpack.catalog.PlatformPackCatalogLoader
import skillbill.infrastructure.skills.scaffold.platformpack.catalog.PlatformPackDiscoveryContext
import skillbill.model.EnvironmentContext
import skillbill.ports.scaffold.install.ScaffoldInstallLinkPort
import skillbill.ports.scaffold.install.model.ScaffoldInstallLinkRequest
import skillbill.ports.scaffold.install.model.ScaffoldInstallLinkResult
import java.nio.file.Path

@Inject
class FileSystemScaffoldInstallLink(
  private val environmentContext: EnvironmentContext,
  private val catalogLoader: PlatformPackCatalogLoader,
) : ScaffoldInstallLinkPort {
  override fun applyInstallLinks(request: ScaffoldInstallLinkRequest): ScaffoldInstallLinkResult {
    val home = environmentContext.userHome
    val environment = environmentContext.environment
    val agents = detectAgents(home, environment)
    val manifests =
      catalogLoader.loadEffectiveManifests(
        PlatformPackDiscoveryContext(
          repoRoot = request.repoRoot,
          userHome = home,
          environment = environment,
          catalogLoader = catalogLoader,
        ),
      )
    val context =
      InstallContext(
        repoRoot = request.repoRoot,
        home = home,
        manifests = manifests,
        environment = environment,
        catalogLoader = catalogLoader,
      )
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
