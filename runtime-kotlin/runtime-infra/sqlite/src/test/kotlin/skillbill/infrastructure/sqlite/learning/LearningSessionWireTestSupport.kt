package skillbill.infrastructure.sqlite.learning

import skillbill.contracts.learning.LearningAppliedSessionWire
import skillbill.contracts.learning.LearningEntryDto
import skillbill.learnings.model.LearningRecord
import skillbill.learnings.model.LearningScope

/**
 * Session-learning payload shaping used as a fixture by the SQLite store tests. The application layer owns the
 * production mapping; these tests only need a durable payload to write and read back, so they build it here rather
 * than making the persistence module depend on runtime-application.
 */
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
