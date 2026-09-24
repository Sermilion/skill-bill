package skillbill.di.review
import me.tatarka.inject.annotations.Provides
import skillbill.infrastructure.skills.agentaddon.AgentAddonSelectionResolver
import skillbill.infrastructure.skills.file.FileExternalAgentAddonSourceConfigStore
import skillbill.infrastructure.skills.install.FileSystemInstalledPlatformPackCatalog
import skillbill.infrastructure.workflow.review.specialists.system.FileSystemDeclaredReviewSpecialists
import skillbill.ports.agentaddon.AgentAddonSelectionPort
import skillbill.ports.agentaddon.ExternalAgentAddonSourceConfigPort
import skillbill.ports.review.launch.DeclaredReviewSpecialistsPort
import skillbill.ports.scaffold.install.InstalledPlatformPackCatalogPort

internal interface RuntimeReviewAddonCatalogProvides {
  @Provides
  fun declaredReviewSpecialistsPort(adapter: FileSystemDeclaredReviewSpecialists): DeclaredReviewSpecialistsPort =
    adapter

  @Provides
  fun installedPlatformPackCatalogPort(
    adapter: FileSystemInstalledPlatformPackCatalog,
  ): InstalledPlatformPackCatalogPort = adapter

  @Provides
  fun agentAddonSelectionPort(): AgentAddonSelectionPort = AgentAddonSelectionResolver()

  @Provides
  fun externalAgentAddonSourceConfigPort(
    store: FileExternalAgentAddonSourceConfigStore,
  ): ExternalAgentAddonSourceConfigPort = store
}
