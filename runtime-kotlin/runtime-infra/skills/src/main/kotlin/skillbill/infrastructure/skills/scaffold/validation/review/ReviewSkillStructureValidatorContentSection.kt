package skillbill.infrastructure.skills.scaffold.validation.review
import java.nio.file.Files
import java.nio.file.Path

internal fun baselineViolations(
  file: Path,
  packRootsBySlug: Map<String, Path> = emptyMap(),
): List<ReviewSkillStructureViolation> {
  val required = listOf(
    "Classification Rules",
    "Diff-Signal Routing Table",
    "Mixed Diffs",
    "Finding Discipline",
  )
  val content = Files.readString(file)
  val classification = h2Section(content, "Classification Rules")
  val routing = h2Section(content, "Diff-Signal Routing Table")
  val mixedDiffs = h2Section(content, "Mixed Diffs")
  val discipline = h2Section(content, "Finding Discipline")
  val composedMixedDiffs = composedBaselineSections(file, "Mixed Diffs", packRootsBySlug)
  val composedDiscipline = composedBaselineSections(file, "Finding Discipline", packRootsBySlug)
  return listOfNotNull(
    baselineHeadingSequenceViolation(file, required),
    baselineClassificationViolation(file, classification),
    baselineRoutingViolation(file, routing),
    baselineMixedDiffRetentionViolation(file, mixedDiffs),
    baselineScopingExclusionsViolation(file, mixedDiffs),
    baselineFindingDisciplineViolation(file, discipline),
    baselineSubagentOrderingViolation(file, composedMixedDiffs),
    baselineResultRetentionViolation(file, composedMixedDiffs),
    baselineAttributedMergeViolation(file, composedDiscipline),
    baselineDeduplicationViolation(file, composedDiscipline),
  )
}
