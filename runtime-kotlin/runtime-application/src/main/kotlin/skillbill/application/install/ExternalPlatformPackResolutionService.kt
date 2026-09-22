package skillbill.application.install

import me.tatarka.inject.annotations.Inject
import skillbill.install.model.ExternalPlatformPackSource
import skillbill.ports.install.platformpack.ExternalPlatformPackSourceConfigPort
import skillbill.ports.install.platformpack.PlatformPackCatalogPort
import skillbill.ports.install.platformpack.model.ExternalPlatformPackPathResolveRequest
import skillbill.ports.install.platformpack.model.ExternalPlatformPackRootRequest
import skillbill.ports.install.platformpack.model.ExternalPlatformPackSourceConfigRequest
import skillbill.ports.install.platformpack.model.ExternalPlatformPackSourceRegistrationRequest
import skillbill.ports.install.platformpack.model.ExternalPlatformPackSourceUnregisterRequest
import skillbill.ports.install.platformpack.model.PlatformPackCatalogRequest
import skillbill.scaffold.policy.platformpack.model.EffectivePlatformPackCatalog
import java.nio.file.Path

@Inject
class ExternalPlatformPackResolutionService(
  private val configPort: ExternalPlatformPackSourceConfigPort,
  private val catalogPort: PlatformPackCatalogPort,
) {
  fun resolveSources(home: Path, environment: Map<String, String> = emptyMap()): List<ExternalPlatformPackSource> =
    configPort.readExternalPlatformPackSources(ExternalPlatformPackSourceConfigRequest(home, environment)).sources

  fun canonicalPackRoot(home: Path, rawPath: String): Path = configPort.resolveExternalPlatformPackPath(
    ExternalPlatformPackPathResolveRequest(userHome = home, rawPath = rawPath),
  ).path

  fun prepareExternalRegistration(
    repoRoot: Path,
    home: Path,
    packRoot: Path,
    environment: Map<String, String> = emptyMap(),
  ): String = catalogPort.assertRegistrableExternalPack(
    ExternalPlatformPackRootRequest(
      packRoot = packRoot,
      catalog = PlatformPackCatalogRequest(
        repoRoot = repoRoot,
        userHome = home,
        environment = environment,
      ),
    ),
  ).slug

  fun registerSource(
    home: Path,
    source: ExternalPlatformPackSource,
    environment: Map<String, String> = emptyMap(),
  ): List<ExternalPlatformPackSource> = configPort.registerExternalPlatformPackSource(
    ExternalPlatformPackSourceRegistrationRequest(
      userHome = home,
      environment = environment,
      source = source,
    ),
  ).sources

  fun unregisterSource(
    home: Path,
    source: ExternalPlatformPackSource,
    environment: Map<String, String> = emptyMap(),
  ): List<ExternalPlatformPackSource> = configPort.unregisterExternalPlatformPackSource(
    ExternalPlatformPackSourceUnregisterRequest(
      userHome = home,
      environment = environment,
      source = source,
    ),
  ).sources

  fun resolveEffectiveCatalog(
    repoRoot: Path,
    home: Path,
    environment: Map<String, String> = emptyMap(),
  ): EffectivePlatformPackCatalog = catalogPort.loadEffectiveCatalog(
    PlatformPackCatalogRequest(
      repoRoot = repoRoot,
      userHome = home,
      environment = environment,
    ),
  ).catalog

  fun validateExternalPackRoot(repoRoot: Path, home: Path, environment: Map<String, String>, packRoot: Path): String =
    catalogPort.validateExternalPackRoot(
      ExternalPlatformPackRootRequest(
        packRoot = packRoot,
        catalog = PlatformPackCatalogRequest(
          repoRoot = repoRoot,
          userHome = home,
          environment = environment,
        ),
      ),
    ).slug
}
