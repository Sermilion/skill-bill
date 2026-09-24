package skillbill.infrastructure.skills.scaffold.platformpack

import skillbill.install.model.InstallPlan
import skillbill.install.model.InstallPlanSkill
import skillbill.install.model.InstallPlanSkillKind
import skillbill.model.toPath
import skillbill.scaffold.model.PlatformManifest
import java.nio.file.Path

internal fun packRootsBySlug(platformManifests: List<PlatformManifest>): Map<String, Path> =
  platformManifests.associate { manifest ->
    manifest.slug to manifest.packRoot.toPath().toAbsolutePath().normalize()
  }

internal fun selectedPlatformManifests(
  plan: InstallPlan,
  platformManifests: List<PlatformManifest>,
): List<PlatformManifest> {
  val selected = selectedPlatformSlugs(plan.skills, platformManifests)
  return platformManifests.filter { manifest -> manifest.slug in selected }
}

internal fun selectedPlatformSlugs(
  skills: List<InstallPlanSkill>,
  platformManifests: List<PlatformManifest>,
): Set<String> =
  skills
    .filter { skill -> skill.kind == InstallPlanSkillKind.PLATFORM_PACK }
    .mapNotNull { skill ->
      platformManifests.firstOrNull { manifest -> skill.sourceDir.startsWith(manifest.packRoot) }?.slug
    }
    .toSet()
