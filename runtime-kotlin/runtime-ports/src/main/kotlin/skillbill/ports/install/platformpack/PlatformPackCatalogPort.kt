package skillbill.ports.install.platformpack

import skillbill.ports.install.platformpack.model.ExternalPlatformPackRootRequest
import skillbill.ports.install.platformpack.model.ExternalPlatformPackRootResult
import skillbill.ports.install.platformpack.model.PlatformPackCatalogRequest
import skillbill.ports.install.platformpack.model.PlatformPackCatalogResult

interface PlatformPackCatalogPort {
  fun loadEffectiveCatalog(request: PlatformPackCatalogRequest): PlatformPackCatalogResult

  fun validateExternalPackRoot(request: ExternalPlatformPackRootRequest): ExternalPlatformPackRootResult

  fun assertRegistrableExternalPack(request: ExternalPlatformPackRootRequest): ExternalPlatformPackRootResult
}
