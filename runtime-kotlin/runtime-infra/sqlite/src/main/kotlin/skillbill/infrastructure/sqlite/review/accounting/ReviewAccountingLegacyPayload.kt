package skillbill.infrastructure.sqlite.review.accounting
import skillbill.contracts.review.SqliteReviewTelemetryPayloadKeys
import skillbill.infrastructure.sqlite.review.core.value
import skillbill.infrastructure.sqlite.review.review.review
import skillbill.infrastructure.sqlite.review.stage.and.review
import skillbill.infrastructure.sqlite.review.stage.finished.payload
import skillbill.infrastructure.sqlite.review.stage.finished.review
import skillbill.infrastructure.sqlite.review.stage.payload
import skillbill.infrastructure.sqlite.review.stage.review
import skillbill.infrastructure.sqlite.review.stage.runtime.review
import skillbill.infrastructure.sqlite.review.stats.health.payload
import skillbill.infrastructure.sqlite.review.stats.health.value
import skillbill.infrastructure.sqlite.review.stats.payload
import skillbill.infrastructure.sqlite.review.stats.recorded.review
import skillbill.infrastructure.sqlite.review.stats.review
import skillbill.infrastructure.sqlite.review.stats.value

private const val LEGACY_EVIDENCE_UNREVIEWABLE_SEGMENT_ID: String = "evidence-unreviewable"

internal fun payloadCarriesLegacyEvidenceUnreviewableSegment(payload: Map<String, Any?>): Boolean {
  fun Map<*, *>.segmentIds(): List<String> {
    val ids = this[SqliteReviewTelemetryPayloadKeys.UNREVIEWED_SEGMENT_IDS] as? List<*> ?: return emptyList()
    return ids.filterIsInstance<String>()
  }
  fun Any?.walkNodes(): Sequence<Map<*, *>> = sequence {
    when (this@walkNodes) {
      is Map<*, *> -> {
        yield(this@walkNodes)
        this@walkNodes.values.forEach { value -> yieldAll(value.walkNodes()) }
      }
      is List<*> -> this@walkNodes.forEach { item -> yieldAll(item.walkNodes()) }
    }
  }
  return payload.walkNodes().any { node ->
    LEGACY_EVIDENCE_UNREVIEWABLE_SEGMENT_ID in node.segmentIds()
  }
}
