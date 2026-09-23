package skillbill.learnings

import skillbill.contracts.learning.summarizeAppliedLearnings
import skillbill.learnings.model.LearningEntry
import skillbill.learnings.model.LearningRecord
import skillbill.learnings.model.LearningScope

fun learningReference(record: LearningRecord): String = "L-%03d".format(record.id)

fun summarizeAppliedLearningEntries(entries: List<LearningEntry>): String =
  summarizeAppliedLearnings(entries.map(LearningEntry::reference))

fun scopedLearningLabel(entry: LearningEntry): String =
  if (entry.scopeKey.isBlank()) entry.scope.wireName else "${entry.scope.wireName}:${entry.scopeKey}"

fun learningEntry(record: LearningRecord): LearningEntry =
  LearningEntry(
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
