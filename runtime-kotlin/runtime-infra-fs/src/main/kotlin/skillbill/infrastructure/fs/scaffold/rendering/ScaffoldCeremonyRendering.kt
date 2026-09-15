package skillbill.infrastructure.fs.scaffold.rendering

import skillbill.scaffold.model.SkillClassManifest
import skillbill.scaffold.model.SkillClassSection

internal fun renderCeremonySection(skillClass: SkillClassManifest?): String = buildString {
  appendLine("## Ceremony")
  skillClass?.ceremonyLines?.forEach { line ->
    appendLine()
    append(line)
    appendLine()
  }
}

internal fun renderClassSections(sections: List<SkillClassSection>): String = buildString {
  sections.forEach { section ->
    append("## ")
    appendLine(section.heading)
    appendLine()
    append(section.body.trimEnd())
    appendLine()
    appendLine()
  }
}
