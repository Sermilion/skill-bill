package skillbill.di.install
import me.tatarka.inject.annotations.Provides
import skillbill.infrastructure.skills.externalplatformpack.FileExternalPlatformPackSourceConfigStore
import skillbill.infrastructure.skills.scaffold.platformpack.catalog.PlatformPackCatalogLoader
import skillbill.ports.install.platformpack.ExternalPlatformPackSourceConfigPort
import skillbill.ports.install.platformpack.PlatformPackCatalogPort

internal interface RuntimeExternalPlatformPackProvides {
  @Provides @JvmSynthetic
  fun externalPlatformPackSourceConfigPort(
    store: FileExternalPlatformPackSourceConfigStore,
  ): ExternalPlatformPackSourceConfigPort = store

  @Provides @JvmSynthetic
  fun platformPackCatalogPort(loader: PlatformPackCatalogLoader): PlatformPackCatalogPort = loader
}
