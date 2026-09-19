
package skillbill.infrastructure.skills.scaffold.platformpack.manifest
import skillbill.infrastructure.skills.scaffold.platformpack.loader.manifest
import skillbill.infrastructure.skills.scaffold.platformpack.loader.skillclass.manifest
import java.nio.file.Path

object ReadmeCatalogEdits {

  private fun catalogRowPattern(skillName: String): Regex = Regex(
    "^\\|\\s*[`\"]?/?${Regex.escape(skillName)}[`\"]?\\s*\\|[^\\n]*\\n",
    RegexOption.MULTILINE,
  )

  private val SECTION_COUNT_PATTERN = Regex(
    "^(### Canonical Skills \\()(\\d+)( skills?\\))\\s*$",
    RegexOption.MULTILINE,
  )

  fun removeCatalogRow(readmePath: Path, skillName: String): ReadmeEditOutcome {
    val original = readmePath.toFile().readText()
    val pattern = catalogRowPattern(skillName)
    val match = pattern.find(original) ?: return ReadmeEditOutcome.LandmarksMissing(
      "No catalog row found for `/$skillName` in README.md.",
    )
    val updated = original.replaceRange(match.range, "")
    if (updated == original) {
      return ReadmeEditOutcome.LandmarksMissing("Catalog row for `/$skillName` did not change.")
    }
    readmePath.toFile().writeText(updated)
    return ReadmeEditOutcome.Applied
  }

  fun decrementSectionCount(readmePath: Path): ReadmeEditOutcome {
    val original = readmePath.toFile().readText()
    val match = SECTION_COUNT_PATTERN.find(original) ?: return ReadmeEditOutcome.LandmarksMissing(
      "Canonical Skills heading with count badge not found in README.md.",
    )
    val current = match.groupValues[2].toIntOrNull()
    val outcome = when {
      current == null -> ReadmeEditOutcome.LandmarksMissing("Canonical Skills heading count is not an integer.")
      current <= 0 -> ReadmeEditOutcome.LandmarksMissing("Canonical Skills heading count is already 0.")
      else -> {
        val next = current - 1
        val suffix = if (next == 1) " skill)" else " skills)"
        val replacement = "${match.groupValues[1]}$next$suffix"
        val updated = original.replaceRange(match.range, replacement)
        if (updated == original) {
          ReadmeEditOutcome.LandmarksMissing("Canonical Skills heading count did not change.")
        } else {
          readmePath.toFile().writeText(updated)
          ReadmeEditOutcome.Applied
        }
      }
    }
    return outcome
  }
}

sealed class ReadmeEditOutcome {
  object Applied : ReadmeEditOutcome()

  data class LandmarksMissing(val reason: String) : ReadmeEditOutcome()
}
