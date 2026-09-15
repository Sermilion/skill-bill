package skillbill.infrastructure.sqlite.review

import skillbill.contracts.JsonCodec
import skillbill.infrastructure.sqlite.telemetry.durationSeconds
import skillbill.review.model.FeatureTaskRuntimeWorkflowStats
import skillbill.review.model.FeatureVerifyWorkflowStats
import java.sql.Connection

private val featureVerifyCompletionStatuses =
  listOf("completed", "abandoned_at_review", "abandoned_at_audit", "error", "stale")
private val auditResults = listOf("all_pass", "had_gaps", "skipped", "not_reached")
private val historySignalValues = listOf("none", "irrelevant", "low", "medium", "high")
private val featureSizes = listOf("SMALL", "MEDIUM", "LARGE")
private val featureTaskRuntimeCompletionStatuses =
  listOf("completed", "blocked", "decomposed_at_planning", "error", "stale")
private val featureTaskRuntimePhaseOutcomes = listOf("completed", "blocked", "running")

/**
 * Every rate here is over the runs that were actually observed reaching a terminal. A stale row is a
 * run the reconciler closed after it stopped reporting, never one that ended in a known way, so
 * including it in the denominator quietly reported a lower completion rate than the runs support.
 * Its count is published as [FeatureTaskRuntimeWorkflowStats.reconcilerClosedRuns] instead of being
 * folded into a rate.
 */
fun buildFeatureTaskRuntimeStats(rows: List<Map<String, Any?>>): FeatureTaskRuntimeWorkflowStats {
  val finishedRows = finishedRows(rows)
  val observedRows = finishedRows.filterNot { it.stringValue("completion_status") == STALE_COMPLETION_STATUS }
  val completedRuns = observedRows.count { it.stringValue("completion_status") == "completed" }
  val blockedRuns = observedRows.count { it.stringValue("completion_status") == "blocked" }
  val decomposedRuns = observedRows.count { it.stringValue("completion_status") == "decomposed_at_planning" }
  val errorRuns = observedRows.count { it.stringValue("completion_status") == "error" }
  val completedPhaseCounts = observedRows.map { parseJsonList(it["completed_phase_ids"]).size }
  val tokenValues = observedRows.mapNotNull { it.nullableIntValue("estimated_total_tokens") }
  return FeatureTaskRuntimeWorkflowStats(
    totalRuns = rows.size,
    finishedRuns = finishedRows.size,
    inProgressRuns = rows.size - finishedRows.size,
    featureSizeCounts = countValues(rows, "feature_size", featureSizes),
    completionStatusCounts = countValues(finishedRows, "completion_status", featureTaskRuntimeCompletionStatuses),
    phaseOutcomeCounts = phaseOutcomeCounts(observedRows),
    completedRuns = completedRuns,
    completedRate = rate(completedRuns, observedRows.size),
    blockedRuns = blockedRuns,
    blockedRate = rate(blockedRuns, observedRows.size),
    decomposedRuns = decomposedRuns,
    decomposedRate = rate(decomposedRuns, observedRows.size),
    errorRuns = errorRuns,
    errorRate = rate(errorRuns, observedRows.size),
    averageCompletedPhaseCount = average(completedPhaseCounts),
    estimatedTokenRunsWithValue = tokenValues.size,
    averageEstimatedTotalTokens = average(tokenValues),
    observedRuns = observedRows.size,
    reconcilerClosedRuns = finishedRows.size - observedRows.size,
  )
}

const val STALE_COMPLETION_STATUS: String = "stale"

private fun phaseOutcomeCounts(rows: List<Map<String, Any?>>): Map<String, Int> {
  val counts = featureTaskRuntimePhaseOutcomes.associateWith { 0 }.toMutableMap()
  rows.forEach { row ->
    val outcomes = JsonCodec.parseObjectOrNull(row.stringValue("phase_outcomes"))
      ?.let { JsonCodec.jsonElementToValue(it) as? Map<*, *> }
      .orEmpty()
    outcomes.values.forEach { status ->
      val key = status?.toString().orEmpty()
      if (key in counts) {
        counts[key] = counts.getValue(key) + 1
      }
    }
  }
  return counts
}

fun buildFeatureVerifyStats(rows: List<Map<String, Any?>>): FeatureVerifyWorkflowStats {
  val finishedRows = finishedRows(rows)
  val rolloutRelevantRuns = rows.count { it.booleanValue("rollout_relevant") }
  val auditPerformedRuns = finishedRows.count { it.booleanValue("feature_flag_audit_performed") }
  val historyReadRuns = finishedRows.count(::historySignalsPresent)
  val historyRelevantRuns = finishedRows.count { it.stringValue("history_relevance") in setOf("medium", "high") }
  val historyHelpfulRuns = finishedRows.count { it.stringValue("history_helpfulness") in setOf("medium", "high") }
  val runsWithGapsFound = finishedRows.count { parseJsonList(it["gaps_found"]).isNotEmpty() }
  val reviewIterations = finishedRows.mapNotNull { it.intValue("review_iterations") }
  val durations = finishedRows.map(::durationSeconds).filter { it > 0 }
  val acceptanceCriteriaCounts = rows.mapNotNull { it.intValue("acceptance_criteria_count") }
  return FeatureVerifyWorkflowStats(
    totalRuns = rows.size,
    finishedRuns = finishedRows.size,
    inProgressRuns = rows.size - finishedRows.size,
    completionStatusCounts = countValues(finishedRows, "completion_status", featureVerifyCompletionStatuses),
    auditResultCounts = countValues(finishedRows, "audit_result", auditResults),
    rolloutRelevantRuns = rolloutRelevantRuns,
    rolloutRelevantRate = rate(rolloutRelevantRuns, rows.size),
    featureFlagAuditPerformedRuns = auditPerformedRuns,
    featureFlagAuditPerformedRate = rate(auditPerformedRuns, finishedRows.size),
    historyReadRuns = historyReadRuns,
    historyReadRate = rate(historyReadRuns, finishedRows.size),
    historyRelevantRuns = historyRelevantRuns,
    historyRelevantRate = rate(historyRelevantRuns, finishedRows.size),
    historyHelpfulRuns = historyHelpfulRuns,
    historyHelpfulRate = rate(historyHelpfulRuns, finishedRows.size),
    historyRelevanceCounts = countValues(finishedRows, "history_relevance", historySignalValues),
    historyHelpfulnessCounts = countValues(finishedRows, "history_helpfulness", historySignalValues),
    runsWithGapsFound = runsWithGapsFound,
    averageAcceptanceCriteriaCount = average(acceptanceCriteriaCounts),
    averageReviewIterations = average(reviewIterations),
    averageDurationSeconds = average(durations),
  )
}

fun loadRows(connection: Connection, tableName: String): List<Map<String, Any?>> =
  connection.prepareStatement("SELECT * FROM $tableName ORDER BY started_at, session_id").use { statement ->
    statement.executeQuery().use(::collectRows)
  }

fun finishedRows(rows: List<Map<String, Any?>>): List<Map<String, Any?>> =
  rows.filter { it["finished_at"]?.toString()?.isNotBlank() == true }

fun historySignalsPresent(row: Map<String, Any?>): Boolean =
  row.stringValue("history_relevance") != "none" || row.stringValue("history_helpfulness") != "none"

fun countValues(rows: List<Map<String, Any?>>, columnName: String, expectedValues: List<String>): Map<String, Int> {
  val counts = expectedValues.associateWith { 0 }.toMutableMap()
  rows.forEach { row ->
    val rawValue = row.stringValue(columnName)
    if (rawValue in counts) {
      counts[rawValue] = counts.getValue(rawValue) + 1
    }
  }
  return counts
}
