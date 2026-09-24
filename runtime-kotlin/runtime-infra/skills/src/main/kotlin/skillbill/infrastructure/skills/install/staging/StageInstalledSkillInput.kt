package skillbill.infrastructure.skills.install.staging

import skillbill.infrastructure.skills.scaffold.platformpack.catalog.PlatformPackCatalogLoader
import skillbill.install.model.InstallPlanSkill
import skillbill.install.model.RenderedSkill
import skillbill.scaffold.model.PlatformManifest
import java.nio.file.Path

internal data class StageInstalledSkillInput(
  val repoRoot: Path,
  val sourceSkillDir: Path,
  val home: Path,
  val manifests: List<PlatformManifest>? = null,
  val skillsRoot: Path? = null,
  val selectedPackSkills: List<InstallPlanSkill> = emptyList(),
  val selectedPlatformSlugs: Set<String> = emptySet(),
  val suppliedCompactIdentity: String? = null,
  val environment: Map<String, String> = emptyMap(),
  val catalogLoader: PlatformPackCatalogLoader? = null,
)

internal fun stageInstalledSkill(
  repoRoot: Path,
  sourceSkillDir: Path,
  home: Path,
): RenderedSkill = stageInstalledSkill(StageInstalledSkillInput(repoRoot, sourceSkillDir, home))
