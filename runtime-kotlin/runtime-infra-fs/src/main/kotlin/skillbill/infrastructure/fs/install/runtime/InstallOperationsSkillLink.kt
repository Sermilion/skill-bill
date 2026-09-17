package skillbill.infrastructure.fs.install.runtime

import skillbill.infrastructure.fs.install.plan.InstallContext
import skillbill.infrastructure.fs.install.plan.installSkill
import skillbill.install.model.AgentTarget
import skillbill.infrastructure.fs.install.plan.resolveInstallHome
import skillbill.ports.repository.toFileLocation
import skillbill.ports.system.HostPlatformPort
import java.nio.file.Files
import java.nio.file.Path

internal fun linkInstalledSkill(
  source: Path,
  targetDir: Path,
  agent: String,
  repoRoot: Path?,
  home: Path?,
  hostPlatform: HostPlatformPort,
): List<Path> {
  val resolvedTargetDir = targetDir.toAbsolutePath().normalize()
  Files.createDirectories(resolvedTargetDir)
  val resolvedHome = resolveInstallHome(home, hostPlatform)
  return installSkill(
    skillPath = source,
    agentTargets = listOf(AgentTarget(agent.ifBlank { "manual" }, resolvedTargetDir.toFileLocation())),
    context = InstallContext(
      repoRoot = repoRoot?.toAbsolutePath()?.normalize(),
      home = resolvedHome,
    ),
  ).linkPaths
}
