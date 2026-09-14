package skillbill.learnings

import skillbill.contracts.JsonCodec
import skillbill.contracts.learning.LearningEntryDto
import skillbill.learnings.model.LearningEntry
import skillbill.learnings.model.LearningRecord
import skillbill.learnings.model.LearningScope

fun learningEntry(record: LearningRecord): LearningEntry = LearningEntry(
  id = record.id,
  reference = learningReference(record),
  scope = LearningScope.fromWireName(record.scope),
  scopeKey = record.scopeKey,
  status = record.status,
  title = record.title,
  ruleText = record.ruleText,
  rationale = record.rationale,
  sourceReviewRunId = record.sourceReviewRunId,
  sourceFindingId = record.sourceFindingId,
)

fun learningEntryDto(entry: LearningEntry): LearningEntryDto = LearningEntryDto(
  reference = entry.reference,
  scope = entry.scope.wireName,
  scopeKey = entry.scopeKey,
  status = entry.status,
  title = entry.title,
  ruleText = entry.ruleText,
  rationale = entry.rationale,
  sourceReviewRunId = entry.sourceReviewRunId,
  sourceFindingId = entry.sourceFindingId,
)

fun learningEntrySessionJson(skillName: String?, entries: List<LearningEntry>): String = JsonCodec.mapToJsonString(
  learningAppliedSessionWire(skillName, entries.map(::learningEntryDto)).toPayload(),
)
