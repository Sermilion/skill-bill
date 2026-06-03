package skillbill.infrastructure.fs

import me.tatarka.inject.annotations.Inject
import skillbill.ports.taskruntime.FeatureTaskRuntimeRunInvariantsSource
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRunInvariants
import java.nio.file.Files
import java.nio.file.Path

/**
 * SKILL-65 Subtask 4 (AC1, AC2): the filesystem adapter that reads a governed
 * feature-task-runtime spec and extracts the run-invariants.
 *
 * This is the ONLY place the run-invariants spec read touches the filesystem; the
 * CLI delegates here so the CLI module stays free of raw file IO. The parse is
 * deliberately narrow and loud:
 *
 *  - [FeatureTaskRuntimeRunInvariants.specReference] is the normalized spec path.
 *  - The acceptance criteria are the numbered list items under the spec's
 *    `## Acceptance Criteria` heading (multi-line items are joined). The domain
 *    model loud-fails when this list is empty, so a spec with no acceptance
 *    criteria cannot start a run.
 *  - Mandates/overrides are the bullet items under a `## Mandates` /
 *    `## Mandates and Overrides` heading when present, else an empty list.
 */
@Inject
class FileSystemFeatureTaskRuntimeRunInvariantsSource : FeatureTaskRuntimeRunInvariantsSource {
  override fun read(specPath: Path): FeatureTaskRuntimeRunInvariants {
    val normalizedPath = specPath.toAbsolutePath().normalize()
    require(Files.isRegularFile(normalizedPath)) {
      "feature-task-runtime spec path '$normalizedPath' must point to a readable spec file."
    }
    val specText = Files.readString(normalizedPath)
    return FeatureTaskRuntimeRunInvariants(
      specReference = normalizedPath.toString(),
      acceptanceCriteria = parseNumberedSection(specText, ACCEPTANCE_CRITERIA_HEADINGS),
      mandatesAndOverrides = parseBulletSection(specText, MANDATES_HEADINGS),
    )
  }

  // Extracts the numbered-list items under the first matching heading, joining
  // continuation lines into one criterion each.
  private fun parseNumberedSection(specText: String, headings: Set<String>): List<String> {
    val body = sectionBody(specText, headings) ?: return emptyList()
    val items = mutableListOf<StringBuilder>()
    body.lineSequence().forEach { rawLine ->
      val line = rawLine.trimEnd()
      val numbered = NUMBERED_ITEM.find(line)
      when {
        numbered != null -> items += StringBuilder(numbered.groupValues[1].trim())
        line.isBlank() -> Unit
        items.isNotEmpty() -> items.last().append(' ').append(line.trim())
      }
    }
    return items.map { it.toString().trim() }.filter(String::isNotBlank)
  }

  // Extracts the bullet-list items under the first matching heading.
  private fun parseBulletSection(specText: String, headings: Set<String>): List<String> {
    val body = sectionBody(specText, headings) ?: return emptyList()
    val items = mutableListOf<StringBuilder>()
    body.lineSequence().forEach { rawLine ->
      val line = rawLine.trimEnd()
      val bullet = BULLET_ITEM.find(line)
      when {
        bullet != null -> items += StringBuilder(bullet.groupValues[1].trim())
        line.isBlank() -> Unit
        items.isNotEmpty() -> items.last().append(' ').append(line.trim())
      }
    }
    return items.map { it.toString().trim() }.filter(String::isNotBlank)
  }

  // Returns the text between the first matching `## <heading>` line and the next
  // `## ` heading (or end of file), or null when no heading matches.
  private fun sectionBody(specText: String, headings: Set<String>): String? {
    val lines = specText.lines()
    val startIndex = lines.indexOfFirst { line -> line.headingTitle()?.lowercase() in headings }
    if (startIndex < 0) {
      return null
    }
    val remaining = lines.drop(startIndex + 1)
    val endOffset = remaining.indexOfFirst { line -> line.headingTitle() != null }
    val sectionLines = if (endOffset < 0) remaining else remaining.take(endOffset)
    return sectionLines.joinToString(separator = "\n")
  }

  private fun String.headingTitle(): String? = HEADING.find(this)?.groupValues?.get(1)?.trim()

  private companion object {
    val ACCEPTANCE_CRITERIA_HEADINGS = setOf("acceptance criteria")
    val MANDATES_HEADINGS = setOf("mandates", "mandates and overrides", "mandates & overrides")
    val HEADING = Regex("""^#{2,6}\s+(.+)$""")
    val NUMBERED_ITEM = Regex("""^\s*\d+\.\s+(.*)$""")
    val BULLET_ITEM = Regex("""^\s*[-*]\s+(.*)$""")
  }
}
