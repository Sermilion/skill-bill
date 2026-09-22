package skillbill.infrastructure.skills.scaffold.platformpack.catalog
import me.tatarka.inject.annotations.Inject
import skillbill.error.core.AmbiguousExternalPlatformPackError
import skillbill.error.core.ExternalPlatformPackConfigError
import skillbill.infrastructure.skills.scaffold.platformpack.loader.childDirectories
import skillbill.infrastructure.skills.scaffold.platformpack.loader.loadPlatformManifest
import skillbill.infrastructure.skills.scaffold.platformpack.loader.loadPlatformPack
import skillbill.infrastructure.skills.scaffold.platformpack.loader.validatePlatformPack
import skillbill.infrastructure.skills.scaffold.platformpack.loader.validatePlatformPackCompositions
import skillbill.infrastructure.skills.scaffold.platformpack.loader.validatePlatformPackFallbacks
import skillbill.infrastructure.skills.scaffold.runtime.service.contract.SHELL_CONTRACT_VERSION
import skillbill.install.model.ExternalPlatformPackSource
import skillbill.model.toPath
import skillbill.ports.install.platformpack.ExternalPlatformPackSourceConfigPort
import skillbill.ports.install.platformpack.PlatformPackCatalogPort
import skillbill.ports.install.platformpack.model.ExternalPlatformPackRootRequest
import skillbill.ports.install.platformpack.model.ExternalPlatformPackRootResult
import skillbill.ports.install.platformpack.model.ExternalPlatformPackSourceConfigRequest
import skillbill.ports.install.platformpack.model.PlatformPackCatalogRequest
import skillbill.ports.install.platformpack.model.PlatformPackCatalogResult
import skillbill.scaffold.model.PlatformManifest
import skillbill.scaffold.policy.platformpack.buildEffectivePlatformPackCatalog
import skillbill.scaffold.policy.platformpack.model.EffectivePlatformPackCatalog
import skillbill.scaffold.policy.platformpack.model.LoadedPlatformPack
import skillbill.scaffold.policy.platformpack.model.PlatformPackSourceKind
import java.nio.file.Files
import java.nio.file.Path

data class PlatformPackDiscoveryContext(
  val repoRoot: Path,
  val userHome: Path,
  val environment: Map<String, String> = emptyMap(),
  val enforceContractVersion: Boolean = true,
  val catalogLoader: PlatformPackCatalogLoader? = null,
)

@Inject
class PlatformPackCatalogLoader(
  private val externalPlatformPackSourceConfigPort: ExternalPlatformPackSourceConfigPort,
) : PlatformPackCatalogPort {
  override fun loadEffectiveCatalog(request: PlatformPackCatalogRequest): PlatformPackCatalogResult =
    PlatformPackCatalogResult(
      catalog = loadEffectiveCatalog(
        PlatformPackDiscoveryContext(
          repoRoot = request.repoRoot,
          userHome = request.userHome,
          environment = request.environment,
          enforceContractVersion = request.enforceContractVersion,
        ),
      ),
    )

  fun loadEffectiveCatalog(context: PlatformPackDiscoveryContext): EffectivePlatformPackCatalog =
    loadEffectiveCatalogInternal(context)

  private fun loadEffectiveCatalogInternal(context: PlatformPackDiscoveryContext): EffectivePlatformPackCatalog {
    val bundled = loadBundledPacks(context)
    val external = loadExternalPacks(context)
    val catalog = buildEffectivePlatformPackCatalog(bundled, external)
    val manifests = catalog.manifests
    validatePlatformPackCompositions(manifests, catalog.manifestsBySlug)
    validatePlatformPackFallbacks(manifests)
    manifests.forEach { pack ->
      validatePlatformPack(pack, SHELL_CONTRACT_VERSION, context.enforceContractVersion)
    }
    return catalog
  }

  fun loadEffectiveManifests(context: PlatformPackDiscoveryContext): List<PlatformManifest> =
    loadEffectiveCatalog(context).manifests

  override fun validateExternalPackRoot(request: ExternalPlatformPackRootRequest): ExternalPlatformPackRootResult {
    val catalog = loadEffectiveCatalog(request.catalog).catalog
    val pack = loadPlatformPack(request.packRoot, catalog.manifestsBySlug)
    return ExternalPlatformPackRootResult(pack.slug)
  }

  override fun assertRegistrableExternalPack(
    request: ExternalPlatformPackRootRequest,
  ): ExternalPlatformPackRootResult {
    val normalized = request.packRoot.toAbsolutePath().normalize()
    if (!Files.isDirectory(normalized)) {
      throw ExternalPlatformPackConfigError(
        "Pack path '$normalized' does not resolve to an existing directory.",
      )
    }
    val catalog = loadEffectiveCatalog(request.catalog).catalog
    val incoming = loadPlatformPack(normalized, catalog.manifestsBySlug)
    val billSharedRoot = request.catalog.repoRoot.toAbsolutePath().normalize().resolve(".bill-shared")
    assertExternalPackContentPresent(incoming)
    assertExternalPlatformPackDeclaredReads(incoming, billSharedRoot)
    val conflict = catalog.entries.any { entry ->
      entry.loaded.sourceKind == PlatformPackSourceKind.EXTERNAL &&
        entry.loaded.manifest.slug == incoming.slug &&
        entry.loaded.canonicalRoot != normalized.toString()
    }
    if (conflict) {
      throw AmbiguousExternalPlatformPackError(
        "External platform pack slug '${incoming.slug}' is already registered at a different root.",
      )
    }
    return ExternalPlatformPackRootResult(incoming.slug)
  }

  private fun loadBundledPacks(context: PlatformPackDiscoveryContext): List<LoadedPlatformPack> {
    val packsRoot = context.repoRoot.toAbsolutePath().normalize().resolve("platform-packs")
    if (!Files.isDirectory(packsRoot)) {
      return emptyList()
    }
    return childDirectories(packsRoot).map { packRoot ->
      val manifest = loadPlatformManifest(packRoot, context.enforceContractVersion)
      LoadedPlatformPack(
        manifest = manifest,
        sourceKind = PlatformPackSourceKind.BUNDLED,
        canonicalRoot = packRoot.toAbsolutePath().normalize().toString(),
      )
    }
  }

  private fun loadExternalPacks(context: PlatformPackDiscoveryContext): List<LoadedPlatformPack> {
    val sources = externalPlatformPackSourceConfigPort.readExternalPlatformPackSources(
      ExternalPlatformPackSourceConfigRequest(
        userHome = context.userHome,
        environment = context.environment,
      ),
    ).sources
    return sources.map { source -> loadExternalPack(source, context) }
  }

  private fun loadExternalPack(
    source: ExternalPlatformPackSource,
    context: PlatformPackDiscoveryContext,
  ): LoadedPlatformPack {
    val canonicalRoot = source.path.toPath().toAbsolutePath().normalize()
    val manifest = loadPlatformManifest(canonicalRoot, context.enforceContractVersion)
    val billSharedRoot = context.repoRoot.toAbsolutePath().normalize().resolve(".bill-shared")
    assertExternalPackContentPresent(manifest)
    assertExternalPlatformPackDeclaredReads(manifest, billSharedRoot)
    return LoadedPlatformPack(
      manifest = manifest,
      sourceKind = PlatformPackSourceKind.EXTERNAL,
      canonicalRoot = canonicalRoot.toString(),
    )
  }
}
