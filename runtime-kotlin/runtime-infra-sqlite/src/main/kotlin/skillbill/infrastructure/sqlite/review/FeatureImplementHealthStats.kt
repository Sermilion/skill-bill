package skillbill.infrastructure.sqlite.review

private val featureImplementSources = listOf("production", "test", "synthetic", "unknown")
private val validFeatureImplementSources = setOf("production", "test", "synthetic")
private val featureImplementSessionIdPattern = Regex("""^fis-[A-Za-z0-9][A-Za-z0-9_-]*$""")
private const val LONG_RUNNING_DURATION_SECONDS = 86_400

data class FeatureImplementHealthStats(
  val sourceCounts: Map<String, Int>,
  val validHealthDenominatorRuns: Int,
  val dataQualityDebtRuns: Int,
  val malformedSessionIdRuns: Int,
  val unknownSourceRuns: Int,
  val duplicateTerminalFinishedEvents: Int,
  val openRuns: Int,
  val completedRuns: Int,
  val completedRate: Double,
  val abandonedAtPlanningRuns: Int,
  val abandonedAtImplementationRuns: Int,
  val abandonedAtReviewRuns: Int,
  val errorRuns: Int,
  val normalDurationRuns: Int,
  val syntheticZeroDurationRuns: Int,
  val longRunningDurationRuns: Int,
  val invalidDurationRuns: Int,
  val normalDurations: List<Int>,
)

fun buildFeatureImplementHealthStats(
  rows: List<Map<String, Any?>>,
  finishedRows: List<Map<String, Any?>>,
): FeatureImplementHealthStats {
  val validRows = rows.filter(::hasValidFeatureImplementSessionId)
  val productionRows = validRows.filter { it.featureImplementSource() == "production" }
  val productionFinishedRows = finishedRows(productionRows)
  val malformedSessionIdRuns = rows.count { !hasValidFeatureImplementSessionId(it) }
  val unknownSourceRuns = rows.count { it.featureImplementSource() !in validFeatureImplementSources }
  val duplicateTerminalEvents = rows.sumOf { it.intValue("duplicate_terminal_finished_events") }
  val invalidDurationRuns = productionFinishedRows.count(::hasInvalidFeatureImplementDuration)
  val normalDurations = productionFinishedRows.map(::durationSeconds).filter(::isNormalFeatureImplementDuration)
  return FeatureImplementHealthStats(
    sourceCounts = countValues(rows, "source", featureImplementSources),
    validHealthDenominatorRuns = productionRows.size,
    dataQualityDebtRuns =
    malformedSessionIdRuns + unknownSourceRuns + duplicateTerminalEvents + invalidDurationRuns,
    malformedSessionIdRuns = malformedSessionIdRuns,
    unknownSourceRuns = unknownSourceRuns,
    duplicateTerminalFinishedEvents = duplicateTerminalEvents,
    openRuns = productionRows.size - productionFinishedRows.size,
    completedRuns = productionFinishedRows.countStatus("completed"),
    completedRate = rate(productionFinishedRows.countStatus("completed"), productionRows.size),
    abandonedAtPlanningRuns = productionFinishedRows.countStatus("abandoned_at_planning"),
    abandonedAtImplementationRuns = productionFinishedRows.countStatus("abandoned_at_implementation"),
    abandonedAtReviewRuns = productionFinishedRows.countStatus("abandoned_at_review"),
    errorRuns = productionFinishedRows.countStatus("error"),
    normalDurationRuns = normalDurations.size,
    syntheticZeroDurationRuns = finishedRows.count(::isSyntheticZeroDuration),
    longRunningDurationRuns = productionFinishedRows.count(::isLongRunningFeatureImplementDuration),
    invalidDurationRuns = invalidDurationRuns,
    normalDurations = normalDurations,
  )
}

private fun hasValidFeatureImplementSessionId(row: Map<String, Any?>): Boolean =
  row.stringValue("session_id").matches(featureImplementSessionIdPattern)

private fun hasInvalidFeatureImplementDuration(row: Map<String, Any?>): Boolean =
  durationSeconds(row) == 0 && row.featureImplementSource() != "synthetic"

private fun isNormalFeatureImplementDuration(durationSeconds: Int): Boolean =
  durationSeconds in 1 until LONG_RUNNING_DURATION_SECONDS

private fun isSyntheticZeroDuration(row: Map<String, Any?>): Boolean =
  row.featureImplementSource() == "synthetic" && durationSeconds(row) == 0

private fun isLongRunningFeatureImplementDuration(row: Map<String, Any?>): Boolean =
  durationSeconds(row) >= LONG_RUNNING_DURATION_SECONDS

private fun List<Map<String, Any?>>.countStatus(status: String): Int =
  count { it.stringValue("completion_status") == status }

private fun Map<String, Any?>.featureImplementSource(): String = stringValue("source").ifBlank { "production" }
