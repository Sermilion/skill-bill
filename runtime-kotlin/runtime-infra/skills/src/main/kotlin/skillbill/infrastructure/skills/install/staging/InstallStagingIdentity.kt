package skillbill.infrastructure.skills.install.staging

import skillbill.agentaddon.model.AgentAddonConsumer
import skillbill.error.AgentAddonPointerCollisionError
import skillbill.infrastructure.skills.agentaddon.AgentAddonDeliveryResolver
import skillbill.infrastructure.skills.agentaddon.AgentAddonPointer
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

private const val INSTALL_SLUG_MAX_CHARS = 32

internal fun isContentManagedSkill(sourceSkillDir: Path): Boolean {
  val contentMd = sourceSkillDir.resolve(AUTHORED_SKILL_CONTENT_FILENAME)
  return Files.exists(contentMd, LinkOption.NOFOLLOW_LINKS) &&
    Files.isRegularFile(contentMd, LinkOption.NOFOLLOW_LINKS)
}

internal fun installedSkillSlug(sourceSkillDir: Path): String {
  val raw = sourceSkillDir.fileName?.toString().orEmpty()
  if (raw.isEmpty()) {
    return ""
  }
  val collapsed = raw.lowercase()
    .replace(Regex("[^a-z0-9-]+"), "-")
    .trim('-')

  return collapsed.take(INSTALL_SLUG_MAX_CHARS).trim('-')
}

internal fun agentAddonPointersForSkill(repoRoot: Path, skillName: String): List<AgentAddonPointer> =
  if (skillName == AgentAddonConsumer.BILL_FEATURE.id) {
    AgentAddonDeliveryResolver().resolve(repoRoot.toAbsolutePath().normalize(), AgentAddonConsumer.BILL_FEATURE)
  } else {
    emptyList()
  }

internal fun validateAgentAddonPointerNamespace(
  skillName: String,
  reservedNames: Collection<String>,
  pointers: List<AgentAddonPointer>,
) {
  val claimed = reservedNames.map(::portableFileName).toMutableSet()
  pointers.forEach { pointer ->
    if (!claimed.add(portableFileName(pointer.name))) {
      throw AgentAddonPointerCollisionError("$skillName/${pointer.name}")
    }
  }
}

internal fun authoredStagingNames(sourceSkillDir: Path, authored: Collection<Path>): List<String> =
  authored.map { path ->
    sourceSkillDir.toAbsolutePath().normalize()
      .relativize(path.toAbsolutePath().normalize())
      .toString()
      .replace(File.separatorChar, '/')
  }.filter { rel -> rel != AUTHORED_SKILL_CONTENT_FILENAME }
