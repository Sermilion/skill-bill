package skillbill.infrastructure.sqlite.review.stats.health
import skillbill.contracts.review.ReviewFindingPayloadKeys
import skillbill.contracts.review.SqliteReviewTelemetryPayloadKeys
import skillbill.review.model.FindingOutcomeType

private val reviewHealthSources = listOf("standalone", "embedded", "malformed", UNKNOWN_REVIEW_HEALTH_SOURCE)
private val reviewHealthOutcomes = FindingOutcomeType.entries.map { it.wireValue }

internal fun aggregateLatestOutcomeCounts(payloads: List<ReviewHealthPayload>): Map<String, Int> {
  val counts = reviewHealthOutcomes.associateWith { 0 }.toMutableMap()
  payloads.forEach { payload ->
    val latestOutcomeCounts = payload.payload[SqliteReviewTelemetryPayloadKeys.LATEST_OUTCOME_COUNTS] as? Map<*, *>
    if (latestOutcomeCounts == null) {
      reviewFindingDetails(payload.payload).forEach { detail ->
        addOutcomeCount(counts, detail[SqliteReviewTelemetryPayloadKeys.OUTCOME_TYPE]?.toString().orEmpty(), 1)
      }
    } else {
      latestOutcomeCounts.forEach { (key, value) -> addOutcomeCount(counts, key?.toString().orEmpty(), value.asInt()) }
    }
  }
  return counts.toMap()
}

internal fun aggregateFindingDetailCounts(
  payloads: List<ReviewHealthPayload>,
  fieldName: String,
  expectedValues: List<String>,
): Map<String, Int> {
  val counts = expectedValues.associateWith { 0 }.toMutableMap()
  payloads.forEach { payload ->
    reviewFindingDetails(payload.payload).forEach { detail ->
      val value = normalizeFindingDetailValue(fieldName, detail[fieldName]?.toString().orEmpty())
      if (value.isNotBlank()) {
        counts[value] = counts.getOrDefault(value, 0) + 1
      }
    }
  }
  return counts.toMap()
}

internal fun aggregatePayloadValueCounts(
  payloads: List<ReviewHealthPayload>,
  fieldName: String,
  expectedValues: List<String>,
  defaultValue: String,
): Map<String, Int> {
  val counts = expectedValues.associateWith { 0 }.toMutableMap()
  payloads.forEach { payload ->
    val value = payload.payload.stringHealthValue(fieldName).ifBlank { defaultValue }
    counts[value] = counts.getOrDefault(value, 0) + 1
  }
  return counts.toMap()
}

internal fun countReviewHealthSources(payloads: List<ReviewHealthPayload>, malformedRecords: Int): Map<String, Int> {
  val counts = reviewHealthSources.associateWith { 0 }.toMutableMap()
  payloads.forEach { payload ->
    val source = payload.source.takeIf(counts::containsKey) ?: UNKNOWN_REVIEW_HEALTH_SOURCE
    counts[source] = counts.getValue(source) + 1
  }
  counts[SqliteReviewTelemetryPayloadKeys.MALFORMED] = malformedRecords
  return counts.toMap()
}

internal const val UNKNOWN_REVIEW_HEALTH_SOURCE: String = "unknown"

internal fun aggregateCategorySeverityCrossTab(payloads: List<ReviewHealthPayload>): Map<String, Map<String, Int>> {
  val crossTab = mutableMapOf<String, MutableMap<String, Int>>()
  payloads.forEach { payload ->
    reviewFindingDetails(payload.payload).forEach { detail ->
      val category = detail[ReviewFindingPayloadKeys.ISSUE_CATEGORY]?.toString().orEmpty()
      val severity = normalizeFindingDetailValue(
        "severity",
        detail[SqliteReviewTelemetryPayloadKeys.SEVERITY]?.toString().orEmpty(),
      )
      if (category.isNotBlank() && severity.isNotBlank()) {
        crossTab.getOrPut(category) { mutableMapOf() }[severity] =
          crossTab.getValue(category).getOrDefault(severity, 0) + 1
      }
    }
  }
  return crossTab.mapValues { it.value.toMap() }
}

private fun addOutcomeCount(counts: MutableMap<String, Int>, key: String, count: Int) {
  if (key in counts) {
    counts[key] = counts.getValue(key) + count
  }
}

private fun reviewFindingDetails(payload: Map<String, Any?>): List<Map<*, *>> {
  val accepted =
    (payload[SqliteReviewTelemetryPayloadKeys.ACCEPTED_FINDING_DETAILS] as? List<*>)
      .orEmpty()
      .filterIsInstance<Map<*, *>>()
  val rejected =
    (payload[SqliteReviewTelemetryPayloadKeys.REJECTED_FINDING_DETAILS] as? List<*>)
      .orEmpty()
      .filterIsInstance<Map<*, *>>()
  return accepted + rejected
}

private fun normalizeFindingDetailValue(fieldName: String, value: String): String = when (fieldName) {
  "confidence" -> when (value.lowercase()) {
    "high" -> "High"
    "medium" -> "Medium"
    "low" -> "Low"
    else -> value
  }
  else -> value
}

private fun Any?.asInt(): Int = when (this) {
  is Number -> toInt()
  is String -> toIntOrNull() ?: 0
  else -> 0
}
