package skillbill.infrastructure.skills.skillremove

import skillbill.ports.skillremove.SkillRemoveFileSystem
import skillbill.skillremove.model.AgentSymlinkUnlink
import skillbill.skillremove.model.AppliedCascade
import skillbill.skillremove.model.ManifestEdit
import skillbill.skillremove.model.ReadmeCatalogEdit
import skillbill.skillremove.model.SkillRemovalPreview
import skillbill.skillremove.model.SkillRemovalRequest
import java.nio.file.Path

class SkillRemoveJvmFileSystem(
  private val home: Path? = null,
) : SkillRemoveFileSystem {
  private val planning = SkillRemoveJvmFileSystemPlanning(home)
  private val apply = SkillRemoveJvmFileSystemApply(home)

  override fun discoverCascadedSkillNames(request: SkillRemovalRequest): List<String> =
    planning.discoverCascadedSkillNames(request)

  override fun targetExists(request: SkillRemovalRequest): Boolean = planning.targetExists(request)

  override fun resolveCascadeFilesystemPaths(
    request: SkillRemovalRequest,
    cascadedSkillNames: List<String>,
  ): List<String> = planning.resolveCascadeFilesystemPaths(request, cascadedSkillNames)

  override fun planManifestEdits(
    request: SkillRemovalRequest,
    cascadedSkillNames: List<String>,
  ): List<ManifestEdit> = planning.planManifestEdits(request, cascadedSkillNames)

  override fun planAgentSymlinkUnlinks(
    request: SkillRemovalRequest,
    cascadedSkillNames: List<String>,
  ): List<AgentSymlinkUnlink> = planning.planAgentSymlinkUnlinks(request, cascadedSkillNames)

  override fun planReadmeCatalogEdits(request: SkillRemovalRequest): List<ReadmeCatalogEdit> =
    planning.planReadmeCatalogEdits(request)

  override fun applyCascade(
    request: SkillRemovalRequest,
    preview: SkillRemovalPreview,
  ): AppliedCascade = apply.applyCascade(request, preview)
}
