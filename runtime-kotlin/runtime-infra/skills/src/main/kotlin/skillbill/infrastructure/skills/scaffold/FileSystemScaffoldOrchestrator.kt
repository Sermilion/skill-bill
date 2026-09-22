package skillbill.infrastructure.skills.scaffold
import me.tatarka.inject.annotations.Inject
import skillbill.infrastructure.skills.install.scaffold.performScaffoldInstall
import skillbill.infrastructure.skills.install.scaffold.rollbackScaffoldInstallTargets
import skillbill.infrastructure.skills.scaffold.adapters.FileSystemScaffoldRepoValidation
import skillbill.infrastructure.skills.scaffold.adapters.FileSystemScaffoldSourceLoader
import skillbill.infrastructure.skills.scaffold.payload.toRawScaffoldPayload
import skillbill.infrastructure.skills.scaffold.platformpack.catalog.PlatformPackCatalogLoader
import skillbill.infrastructure.skills.scaffold.runtime.service.ScaffoldAdapterSeams
import skillbill.infrastructure.skills.scaffold.runtime.service.ScaffoldRuntimeContext
import skillbill.infrastructure.skills.scaffold.runtime.service.scaffoldWithAdapters
import skillbill.infrastructure.skills.scaffold.runtime.service.standalone.scaffold
import skillbill.model.EnvironmentContext
import skillbill.ports.install.platformpack.ExternalPlatformPackSourceConfigPort
import skillbill.scaffold.model.ScaffoldResult
import skillbill.scaffold.model.command.ScaffoldCommandRequest

@Inject
class FileSystemScaffoldOrchestrator(
  private val repoValidation: FileSystemScaffoldRepoValidation,
  private val sourceLoader: FileSystemScaffoldSourceLoader,
  private val environmentContext: EnvironmentContext,
  private val catalogLoader: PlatformPackCatalogLoader,
  private val packSourceConfig: ExternalPlatformPackSourceConfigPort,
) {
  fun scaffold(payload: Map<String, Any?>, dryRun: Boolean): ScaffoldResult = scaffoldWithAdapters(
    payload,
    dryRun,
    adapterSeams(),
    runtime = ScaffoldRuntimeContext(
      userHome = environmentContext.userHome,
      environment = environmentContext.environment,
      catalogLoader = catalogLoader,
      packSourceConfig = packSourceConfig,
    ),
  )

  fun scaffold(request: ScaffoldCommandRequest, dryRun: Boolean): ScaffoldResult =
    scaffold(request.toRawScaffoldPayload(), dryRun)

  private fun adapterSeams(): ScaffoldAdapterSeams = ScaffoldAdapterSeams(
    validateScaffold = { plan, repoRoot -> repoValidation.validateScaffold(plan, repoRoot) },
    optionalBaselineLayers = { payload, repoRoot, newPlatform ->
      repoValidation.optionalBaselineLayers(payload, repoRoot, newPlatform)
    },
    resolveAddonConsumerSkillDirs = { payload, packRoot, pack ->
      sourceLoader.resolveAddonConsumerSkillDirs(payload, packRoot, pack)
    },
    performInstall = { txn, plan, repoRoot -> performScaffoldInstall(txn, plan, repoRoot) },
    rollbackInstallTargets = { txn, errors -> rollbackScaffoldInstallTargets(txn, errors) },
  )
}
