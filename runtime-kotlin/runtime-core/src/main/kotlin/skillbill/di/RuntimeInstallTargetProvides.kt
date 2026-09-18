package skillbill.di

import me.tatarka.inject.annotations.Provides
import skillbill.infrastructure.skills.FileSystemBaselineManifestPersistence
import skillbill.infrastructure.skills.FileSystemInstallAgentTargets
import skillbill.infrastructure.skills.FileSystemInstallMcpRegistration
import skillbill.infrastructure.skills.FileSystemInstallNativeAgentLinks
import skillbill.infrastructure.skills.FileSystemInstallReconcile
import skillbill.infrastructure.skills.FileSystemInstallReconcileApply
import skillbill.infrastructure.skills.FileSystemInstallSkillLink
import skillbill.infrastructure.skills.FileSystemInstalledWorkspaceBaselineStatus
import skillbill.ports.install.agent.InstallAgentTargetPort
import skillbill.ports.install.baseline.BaselineManifestPersistencePort
import skillbill.ports.install.baseline.InstalledWorkspaceBaselineStatusPort
import skillbill.ports.install.link.InstallSkillLinkPort
import skillbill.ports.install.mcp.InstallMcpRegistrationPort
import skillbill.ports.install.nativeagent.InstallNativeAgentLinkPort
import skillbill.ports.install.reconcile.InstallReconcileApplyPort
import skillbill.ports.install.reconcile.InstallReconcilePort

internal interface RuntimeInstallTargetProvides {
  @Provides @JvmSynthetic
  fun installReconcilePort(adapter: FileSystemInstallReconcile): InstallReconcilePort = adapter

  @Provides @JvmSynthetic
  fun installReconcileApplyPort(adapter: FileSystemInstallReconcileApply): InstallReconcileApplyPort = adapter

  @Provides @JvmSynthetic
  fun baselineManifestPersistencePort(adapter: FileSystemBaselineManifestPersistence): BaselineManifestPersistencePort =
    adapter

  @Provides @JvmSynthetic
  fun installedWorkspaceBaselineStatusPort(
    adapter: FileSystemInstalledWorkspaceBaselineStatus,
  ): InstalledWorkspaceBaselineStatusPort = adapter

  @Provides @JvmSynthetic
  fun installSkillLinkPort(adapter: FileSystemInstallSkillLink): InstallSkillLinkPort = adapter

  @Provides @JvmSynthetic
  fun installAgentTargetPort(adapter: FileSystemInstallAgentTargets): InstallAgentTargetPort = adapter

  @Provides @JvmSynthetic
  fun installNativeAgentLinkPort(adapter: FileSystemInstallNativeAgentLinks): InstallNativeAgentLinkPort = adapter

  @Provides @JvmSynthetic
  fun installMcpRegistrationPort(adapter: FileSystemInstallMcpRegistration): InstallMcpRegistrationPort = adapter
}
