package skillbill.di.install

import me.tatarka.inject.annotations.Provides
import skillbill.infrastructure.http.GitHubReleaseCatalogAdapter
import skillbill.ports.process.ReleaseCatalogPort

internal interface RuntimeInstallerProvides {
  @Provides
  fun releaseCatalogPort(adapter: GitHubReleaseCatalogAdapter): ReleaseCatalogPort = adapter
}
