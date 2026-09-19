package skillbill.infrastructure.skills.install.staging.staging.support
import skillbill.infrastructure.skills.install.staging.staging.content.repoRoot
import skillbill.infrastructure.skills.install.staging.staging.content.sourceSkillDir
import skillbill.infrastructure.skills.install.staging.staging.installed.repoRoot
import skillbill.infrastructure.skills.install.staging.staging.installed.skillsRoot
import skillbill.infrastructure.skills.install.staging.staging.installed.sourceSkillDir
import skillbill.infrastructure.skills.install.staging.staging.repoRoot
import skillbill.infrastructure.skills.install.staging.staging.sidecar.repoRoot
import skillbill.infrastructure.skills.install.staging.staging.sidecar.selectedPlatformManifests
import skillbill.infrastructure.skills.install.staging.staging.sidecar.skillName
import skillbill.infrastructure.skills.install.staging.staging.sidecar.skillsRoot
import skillbill.infrastructure.skills.install.staging.staging.skillName
import skillbill.infrastructure.skills.install.staging.staging.sourceSkillDir
import skillbill.infrastructure.skills.install.staging.staging.staging
import skillbill.infrastructure.skills.scaffold.runtime.service.contract.requireSupportingFileTarget
import skillbill.infrastructure.skills.scaffold.runtime.service.support.requiredSupportingFilesForSkill
import skillbill.scaffold.model.PlatformManifest
import java.nio.file.Path

internal data class GeneratedSupportPointer(
  val name: String,
  val target: Path,
)

internal fun generatedSupportPointersFor(
  repoRoot: Path,
  sourceSkillDir: Path,
  skillName: String,
  skillsRoot: Path = repoRoot.resolve("skills"),
  selectedPlatformManifests: List<PlatformManifest> = emptyList(),
): List<GeneratedSupportPointer> {
  val root = repoRoot.toAbsolutePath().normalize()
  val resolvedSkillsRoot = skillsRoot.toAbsolutePath().normalize()
  val resolvedSource = sourceSkillDir.toAbsolutePath().normalize()
  if (!resolvedSource.startsWith(resolvedSkillsRoot)) {
    return emptyList()
  }
  return requiredSupportingFilesForSkill(skillName, root, selectedPlatformManifests).mapNotNull { fileName ->
    val target = requireSupportingFileTarget(skillName, fileName, root, selectedPlatformManifests)
      .toAbsolutePath()
      .normalize()
    val sourceSidecar = resolvedSource.resolve(fileName).normalize()
    if (target == sourceSidecar) {
      null
    } else {
      GeneratedSupportPointer(fileName, target)
    }
  }
}
