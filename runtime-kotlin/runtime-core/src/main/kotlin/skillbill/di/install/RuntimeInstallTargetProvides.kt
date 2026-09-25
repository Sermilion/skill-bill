package skillbill.di.install

import me.tatarka.inject.annotations.Provides
import skillbill.infrastructure.skills.install.FileSystemBaselineManifestPersistence
import skillbill.infrastructure.skills.install.FileSystemInstallAgentTargets
import skillbill.infrastructure.skills.install.FileSystemInstallMcpRegistration
import skillbill.infrastructure.skills.install.FileSystemInstallNativeAgentLinks
import skillbill.infrastructure.skills.install.FileSystemInstallReconcile
import skillbill.infrastructure.skills.install.FileSystemInstallReconcileApply
import skillbill.infrastructure.skills.install.FileSystemInstallSkillLink
import skillbill.ports.install.agent.InstallAgentTargetPort
import skillbill.ports.install.baseline.BaselineManifestPersistencePort
import skillbill.ports.install.link.InstallSkillLinkPort
import skillbill.ports.install.mcp.InstallMcpRegistrationPort
import skillbill.ports.install.nativeagent.InstallNativeAgentLinkPort
import skillbill.ports.install.reconcile.InstallReconcileApplyPort
import skillbill.ports.install.reconcile.InstallReconcilePort

internal interface RuntimeInstallTargetProvides {
  @Provides
  fun installReconcilePort(adapter: FileSystemInstallReconcile): InstallReconcilePort = adapter

  @Provides
  fun installReconcileApplyPort(adapter: FileSystemInstallReconcileApply): InstallReconcileApplyPort = adapter

  @Provides
  fun baselineManifestPersistencePort(adapter: FileSystemBaselineManifestPersistence): BaselineManifestPersistencePort =
    adapter

  @Provides
  fun installSkillLinkPort(adapter: FileSystemInstallSkillLink): InstallSkillLinkPort = adapter

  @Provides
  fun installAgentTargetPort(adapter: FileSystemInstallAgentTargets): InstallAgentTargetPort = adapter

  @Provides
  fun installNativeAgentLinkPort(adapter: FileSystemInstallNativeAgentLinks): InstallNativeAgentLinkPort = adapter

  @Provides
  fun installMcpRegistrationPort(adapter: FileSystemInstallMcpRegistration): InstallMcpRegistrationPort = adapter
}
