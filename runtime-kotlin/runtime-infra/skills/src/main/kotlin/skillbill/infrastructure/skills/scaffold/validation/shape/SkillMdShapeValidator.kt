package skillbill.infrastructure.skills.scaffold.validation.shape
import java.nio.file.Files
import java.nio.file.Path

internal fun validateSkillMdShape(
  path: Path,
  validateBodyShape: Boolean = false,
) {
  val text = Files.readString(path)
  val fileName = path.fileName?.toString() ?: path.toString()
  val bodyStartOffset = validateSkillMdFrontmatter(path, fileName, text)
  if (!validateBodyShape) {
    return
  }
  validateSkillMdBodyShape(path, fileName, text, bodyStartOffset)
}

internal fun parseSkillFrontmatter(text: String): Map<String, String> =
  SKILL_MD_FRONTMATTER_PATTERN.find(text)?.let { match -> parseSkillMdFrontmatter(match.groupValues[1]) }.orEmpty()

internal fun markdownBodyAfterFrontmatter(text: String): String =
  SKILL_MD_FRONTMATTER_PATTERN.find(text)?.let { match -> text.substring(match.range.last + 1) } ?: text
