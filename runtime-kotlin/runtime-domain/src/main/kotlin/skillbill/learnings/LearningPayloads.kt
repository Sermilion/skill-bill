package skillbill.learnings

import skillbill.contracts.learning.LearningAppliedSessionWire
import skillbill.contracts.learning.LearningEntryDto
import skillbill.contracts.learning.LearningSummaryWire
import skillbill.learnings.model.LearningRecord
import skillbill.learnings.model.LearningScope

fun learningReference(record: LearningRecord): String = "L-%03d".format(record.id)

fun learningEntryDto(record: LearningRecord): LearningEntryDto = LearningEntryDto(
  reference = learningReference(record),
  scope = record.scope,
  scopeKey = record.scopeKey,
  status = record.status,
  title = record.title,
  ruleText = record.ruleText,
  rationale = record.rationale,
  sourceReviewRunId = record.sourceReviewRunId,
  sourceFindingId = record.sourceFindingId,
)

fun learningSummaryWire(entry: LearningEntryDto): LearningSummaryWire = LearningSummaryWire.fromEntry(entry)

fun scopeCountsFromDtos(payloads: List<LearningEntryDto>): Map<String, Int> = LearningScope.emptyScopeCounts().apply {
  payloads.forEach { payload ->
    val scope = LearningScope.fromWireNameOrNull(payload.scope) ?: return@forEach
    put(scope.wireName, getValue(scope.wireName) + 1)
  }
}

fun learningAppliedSessionWire(skillName: String?, payloadEntries: List<LearningEntryDto>): LearningAppliedSessionWire =
  LearningAppliedSessionWire(
    skillName = skillName,
    entries = payloadEntries,
    scopeCounts = scopeCountsFromDtos(payloadEntries),
  )

fun summarizeLearningReferences(entries: List<LearningEntryDto>): String =
  if (entries.isEmpty()) "none" else entries.joinToString(", ") { it.reference }
