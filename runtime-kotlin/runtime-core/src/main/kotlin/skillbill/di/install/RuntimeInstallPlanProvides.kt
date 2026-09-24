package skillbill.di.install

import me.tatarka.inject.annotations.Provides
import skillbill.infrastructure.contracts.install.InstallPlanSchemaValidator
import skillbill.infrastructure.skills.externaladdon.FileExternalAddonSourceConfigStore
import skillbill.infrastructure.skills.externaladdon.FileSystemExternalAddonOverlay
import skillbill.infrastructure.skills.install.FileSystemInstallApplyExecution
import skillbill.infrastructure.skills.install.FileSystemInstallPlanningFacts
import skillbill.infrastructure.skills.install.FileSystemInstallPlatformSkillMaterialization
import skillbill.infrastructure.skills.install.FileSystemInstallSelectionPersistence
import skillbill.infrastructure.skills.install.FileSystemInstallStagingIntent
import skillbill.infrastructure.skills.install.FileSystemUninstallFileSystemGateway
import skillbill.infrastructure.skills.skillremove.FileSystemSkillRemoveFileSystem
import skillbill.ports.install.InstallPlanWireValidator
import skillbill.ports.install.addon.ExternalAddonOverlayPort
import skillbill.ports.install.addon.ExternalAddonSourceConfigPort
import skillbill.ports.install.apply.InstallApplyExecutionPort
import skillbill.ports.install.plan.InstallPlanningFactsPort
import skillbill.ports.install.plan.InstallPlatformSkillMaterializationPort
import skillbill.ports.install.plan.InstallStagingIntentPort
import skillbill.ports.install.selection.InstallSelectionPersistencePort
import skillbill.ports.skillremove.SkillRemoveFileSystem
import skillbill.ports.system.UninstallPathsPort

internal interface RuntimeInstallPlanProvides {
  @Provides
  fun installPlanningFactsPort(adapter: FileSystemInstallPlanningFacts): InstallPlanningFactsPort = adapter

  @Provides
  fun installPlatformSkillMaterializationPort(
    adapter: FileSystemInstallPlatformSkillMaterialization,
  ): InstallPlatformSkillMaterializationPort = adapter

  @Provides
  fun installStagingIntentPort(adapter: FileSystemInstallStagingIntent): InstallStagingIntentPort = adapter

  @Provides
  fun installApplyExecutionPort(adapter: FileSystemInstallApplyExecution): InstallApplyExecutionPort = adapter

  @Provides
  fun installSelectionPersistencePort(adapter: FileSystemInstallSelectionPersistence): InstallSelectionPersistencePort =
    adapter

  @Provides
  fun installPlanWireValidator(validator: InstallPlanSchemaValidator): InstallPlanWireValidator = validator

  @Provides
  fun externalAddonOverlayPort(adapter: FileSystemExternalAddonOverlay): ExternalAddonOverlayPort = adapter

  @Provides
  fun externalAddonSourceConfigPort(store: FileExternalAddonSourceConfigStore): ExternalAddonSourceConfigPort = store

  @Provides
  fun uninstallPathsPort(gateway: FileSystemUninstallFileSystemGateway): UninstallPathsPort = gateway

  @Provides
  fun skillRemoveFileSystem(fileSystem: FileSystemSkillRemoveFileSystem): SkillRemoveFileSystem = fileSystem
}
