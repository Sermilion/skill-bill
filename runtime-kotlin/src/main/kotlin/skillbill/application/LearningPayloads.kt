package skillbill.application

import skillbill.contracts.JsonSupport
import skillbill.learnings.LearningScope
import skillbill.learnings.learningSummaryPayload
import skillbill.learnings.scopeCounts
import skillbill.review.LearningRecord

internal fun learningRecordPayload(dbPath: String, record: LearningRecord): Map<String, Any?> =
  linkedMapOf<String, Any?>().apply {
    putAll(skillbill.learnings.learningPayload(record))
    put("db_path", dbPath)
  }

internal fun learningsResolvePayload(
  dbPath: String,
  repoScopeKey: String?,
  skillName: String?,
  reviewSessionId: String?,
  payloadEntries: List<Map<String, Any?>>,
): LinkedHashMap<String, Any?> = linkedMapOf<String, Any?>(
  "db_path" to dbPath,
  "repo_scope_key" to repoScopeKey,
  "skill_name" to skillName,
  "scope_precedence" to LearningScope.precedenceWireNames(),
  "applied_learnings" to summarizeAppliedLearnings(payloadEntries),
  "learnings" to payloadEntries,
).also { payload ->
  reviewSessionId?.takeIf(String::isNotBlank)?.let { payload["review_session_id"] = it }
}

internal fun learningsSessionJson(skillName: String?, payloadEntries: List<Map<String, Any?>>): String =
  JsonSupport.mapToJsonString(
    linkedMapOf(
      "skill_name" to skillName,
      "applied_learning_count" to payloadEntries.size,
      "applied_learning_references" to payloadEntries.map { it["reference"] },
      "applied_learnings" to summarizeAppliedLearnings(payloadEntries),
      "scope_counts" to scopeCounts(payloadEntries),
      "learnings" to payloadEntries.map(::learningSummaryPayload),
    ),
  )

internal fun summarizeAppliedLearnings(entries: List<Map<String, Any?>>): String =
  if (entries.isEmpty()) "none" else entries.joinToString(", ") { it["reference"].toString() }
