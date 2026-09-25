package skillbill.infrastructure.sqlite

import skillbill.contracts.learning.LearningAppliedSessionWire
import skillbill.contracts.learning.LearningEntryDto
import skillbill.learnings.model.LearningRecord
import skillbill.learnings.model.LearningScope

fun testLearningEntryDto(record: LearningRecord): LearningEntryDto =
  LearningEntryDto(
    reference = "L-%03d".format(record.id),
    scope = LearningScope.fromWireName(record.scope).wireName,
    scopeKey = record.scopeKey,
    status = record.status,
    title = record.title,
    ruleText = record.ruleText,
    rationale = record.rationale,
    sourceReviewRunId = record.sourceReviewRunId,
    sourceFindingId = record.sourceFindingId,
  )

fun testLearningAppliedSessionWire(
  skillName: String?,
  payloadEntries: List<LearningEntryDto>,
): LearningAppliedSessionWire =
  LearningAppliedSessionWire(
    skillName = skillName,
    entries = payloadEntries,
    scopeCounts =
      linkedMapOf<String, Int>().apply {
        payloadEntries.forEach { payload ->
          val scope = LearningScope.fromWireNameOrNull(payload.scope) ?: return@forEach
          put(scope.wireName, getOrDefault(scope.wireName, 0) + 1)
        }
      },
  )
