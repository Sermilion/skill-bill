package skillbill.di

import me.tatarka.inject.annotations.Provides
import skillbill.infrastructure.skills.FileSystemScaffoldCatalogGateway
import skillbill.infrastructure.skills.FileSystemScaffoldGateway
import skillbill.infrastructure.skills.FileSystemScaffoldGeneratedStaging
import skillbill.infrastructure.skills.FileSystemScaffoldInstallLink
import skillbill.infrastructure.skills.FileSystemScaffoldManifestPersistence
import skillbill.infrastructure.skills.FileSystemUnsupportedScaffoldGateway
import skillbill.infrastructure.skills.scaffold.adapters.FileSystemScaffoldSourceLoader
import skillbill.ports.scaffold.ScaffoldCatalogGateway
import skillbill.ports.scaffold.ScaffoldGateway
import skillbill.ports.scaffold.UnsupportedScaffoldGateway
import skillbill.ports.scaffold.install.ScaffoldInstallLinkPort
import skillbill.ports.scaffold.manifest.ScaffoldManifestPersistencePort
import skillbill.ports.scaffold.source.ScaffoldSourceLoaderPort
import skillbill.ports.scaffold.staging.ScaffoldGeneratedStagingPort

internal interface RuntimeScaffoldProvides {
  @Provides @JvmSynthetic
  fun scaffoldGateway(gateway: FileSystemScaffoldGateway): ScaffoldGateway = gateway

  @Provides @JvmSynthetic
  fun unsupportedScaffoldGateway(gateway: FileSystemUnsupportedScaffoldGateway): UnsupportedScaffoldGateway = gateway

  @Provides @JvmSynthetic
  fun scaffoldCatalogGateway(gateway: FileSystemScaffoldCatalogGateway): ScaffoldCatalogGateway = gateway

  @Provides @JvmSynthetic
  fun scaffoldSourceLoaderPort(adapter: FileSystemScaffoldSourceLoader): ScaffoldSourceLoaderPort = adapter

  @Provides @JvmSynthetic
  fun scaffoldManifestPersistencePort(adapter: FileSystemScaffoldManifestPersistence): ScaffoldManifestPersistencePort =
    adapter

  @Provides @JvmSynthetic
  fun scaffoldGeneratedStagingPort(adapter: FileSystemScaffoldGeneratedStaging): ScaffoldGeneratedStagingPort = adapter

  @Provides @JvmSynthetic
  fun scaffoldInstallLinkPort(adapter: FileSystemScaffoldInstallLink): ScaffoldInstallLinkPort = adapter
}
