package skillbill.di

import me.tatarka.inject.annotations.Provides
import skillbill.infrastructure.skills.FileExternalAgentAddonSourceConfigStore
import skillbill.infrastructure.skills.FileSystemInstalledPlatformPackCatalog
import skillbill.infrastructure.skills.agentaddon.AgentAddonSelectionResolver
import skillbill.infrastructure.workflow.FileSystemDeclaredReviewSpecialists
import skillbill.ports.agentaddon.AgentAddonSelectionPort
import skillbill.ports.agentaddon.ExternalAgentAddonSourceConfigPort
import skillbill.ports.review.DeclaredReviewSpecialistsPort
import skillbill.ports.scaffold.install.InstalledPlatformPackCatalogPort

internal interface RuntimeReviewAddonCatalogProvides {
  @Provides @JvmSynthetic
  fun declaredReviewSpecialistsPort(adapter: FileSystemDeclaredReviewSpecialists): DeclaredReviewSpecialistsPort =
    adapter

  @Provides @JvmSynthetic
  fun installedPlatformPackCatalogPort(
    adapter: FileSystemInstalledPlatformPackCatalog,
  ): InstalledPlatformPackCatalogPort = adapter

  @Provides @JvmSynthetic
  fun agentAddonSelectionPort(): AgentAddonSelectionPort = AgentAddonSelectionResolver()

  @Provides @JvmSynthetic
  fun externalAgentAddonSourceConfigPort(
    store: FileExternalAgentAddonSourceConfigStore,
  ): ExternalAgentAddonSourceConfigPort = store
}
