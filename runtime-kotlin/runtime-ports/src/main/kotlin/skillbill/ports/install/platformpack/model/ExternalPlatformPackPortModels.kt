package skillbill.ports.install.platformpack.model

import skillbill.install.model.ExternalPlatformPackSource
import skillbill.scaffold.policy.platformpack.model.EffectivePlatformPackCatalog
import java.nio.file.Path

data class ExternalPlatformPackSourceConfigRequest(
  val userHome: Path,
  val environment: Map<String, String> = emptyMap(),
)

data class ExternalPlatformPackSourceRegistrationRequest(
  val userHome: Path,
  val environment: Map<String, String> = emptyMap(),
  val source: ExternalPlatformPackSource,
)

data class ExternalPlatformPackSourceUnregisterRequest(
  val userHome: Path,
  val environment: Map<String, String> = emptyMap(),
  val source: ExternalPlatformPackSource,
)

data class ExternalPlatformPackSourceConfigResult(
  val sources: List<ExternalPlatformPackSource> = emptyList(),
)

data class ExternalPlatformPackPathResolveRequest(
  val userHome: Path,
  val rawPath: String,
)

data class ExternalPlatformPackPathResolveResult(
  val path: Path,
)

data class PlatformPackCatalogRequest(
  val repoRoot: Path,
  val userHome: Path,
  val environment: Map<String, String> = emptyMap(),
  val enforceContractVersion: Boolean = true,
)

data class PlatformPackCatalogResult(
  val catalog: EffectivePlatformPackCatalog,
)

data class ExternalPlatformPackRootRequest(
  val packRoot: Path,
  val catalog: PlatformPackCatalogRequest,
)

data class ExternalPlatformPackRootResult(
  val slug: String,
)
