package skillbill.infrastructure.skills.install.runtime

import skillbill.infrastructure.skills.install.plan.InstallContext
import skillbill.infrastructure.skills.install.plan.installSkill
import skillbill.infrastructure.skills.install.plan.resolveInstallHome
import skillbill.install.model.AgentTarget
import skillbill.ports.install.link.model.InstallSkillLinkRequest
import skillbill.ports.repository.toFileLocation
import skillbill.ports.system.HostPlatformPort
import java.nio.file.Files
import java.nio.file.Path

internal fun linkInstalledSkill(request: InstallSkillLinkRequest, hostPlatform: HostPlatformPort): List<Path> {
  val resolvedTargetDir = request.targetDir.toAbsolutePath().normalize()
  Files.createDirectories(resolvedTargetDir)
  val resolvedHome = resolveInstallHome(request.home, hostPlatform)
  return installSkill(
    skillPath = request.source,
    agentTargets = listOf(AgentTarget(request.agent.ifBlank { "manual" }, resolvedTargetDir.toFileLocation())),
    context = InstallContext(
      repoRoot = request.repoRoot?.toAbsolutePath()?.normalize(),
      home = resolvedHome,
    ),
  ).linkPaths
}
