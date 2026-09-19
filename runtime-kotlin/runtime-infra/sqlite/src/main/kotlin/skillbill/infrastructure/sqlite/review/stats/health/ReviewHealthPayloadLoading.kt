package skillbill.infrastructure.sqlite.review.stats.health
import skillbill.contracts.JsonCodec
import skillbill.infrastructure.sqlite.review.accounting.List
import skillbill.infrastructure.sqlite.review.accounting.value
import skillbill.infrastructure.sqlite.review.core.value
import skillbill.infrastructure.sqlite.review.review.review
import skillbill.infrastructure.sqlite.review.stage.and.review
import skillbill.infrastructure.sqlite.review.stage.finished.rawJson
import skillbill.infrastructure.sqlite.review.stage.finished.review
import skillbill.infrastructure.sqlite.review.stage.finished.stats
import skillbill.infrastructure.sqlite.review.stage.lane.key
import skillbill.infrastructure.sqlite.review.stage.review
import skillbill.infrastructure.sqlite.review.stage.runtime.review
import skillbill.infrastructure.sqlite.review.stats.connection
import skillbill.infrastructure.sqlite.review.stats.health
import skillbill.infrastructure.sqlite.review.stats.recorded.review
import skillbill.infrastructure.sqlite.review.stats.recorded.stats
import skillbill.infrastructure.sqlite.review.stats.review
import skillbill.infrastructure.sqlite.review.stats.stats
import skillbill.infrastructure.sqlite.review.stats.task.stats
import skillbill.infrastructure.sqlite.review.stats.value
import skillbill.infrastructure.sqlite.review.stats.workflow.key
import skillbill.infrastructure.sqlite.review.stats.workflow.loadRows
import java.sql.Connection

internal fun loadStandaloneReviewPayloads(connection: Connection): List<ReviewHealthPayload> =
  connection.prepareStatement(
    """
      SELECT payload_json, event_uuid, delivery_attempts
      FROM telemetry_outbox
      WHERE event_name = 'skillbill_review_finished'
      ORDER BY id
    """.trimIndent(),
  ).use { statement ->
    statement.executeQuery().use { resultSet ->
      buildList {
        while (resultSet.next()) {
          val raw = resultSet.getString("payload_json")
          val identity = resultSet.getString("event_uuid")
          val attempts = resultSet.getInt("delivery_attempts")
          val parsed = parseHealthJsonObject(raw)
          if (parsed.isEmpty() && raw.trim() != "{}") {
            add(ReviewHealthPayload("malformed", emptyMap(), identity, attempts))
            continue
          }
          val materialized = materializeReviewFinishedPayload(connection, parsed)
          if (materialized.isEmpty() && parsed.isNotEmpty()) {
            add(ReviewHealthPayload("malformed", emptyMap(), identity, attempts))
          } else {
            add(ReviewHealthPayload("standalone", materialized, identity, attempts))
          }
        }
      }
    }
  }

internal fun loadEmbeddedReviewPayloads(connection: Connection): List<ReviewHealthPayload> =
  loadRows(connection, "feature_implement_sessions").flatMap(::embeddedReviewPayloads)

internal fun Map<String, Any?>.healthInt(key: String): Int = this[key].asHealthInt()

internal fun Map<String, Any?>.stringHealthValue(key: String): String = this[key]?.toString().orEmpty()

internal fun parseHealthJsonObject(rawJson: String): Map<String, Any?> = JsonCodec.parseObjectOrNull(rawJson)
  ?.let { JsonCodec.jsonElementToValue(it) as? Map<*, *> }
  ?.toHealthStringAnyMap()
  ?: emptyMap()

internal fun Map<*, *>.toHealthStringAnyMap(): Map<String, Any?> =
  entries.mapNotNull { (key, value) -> key?.toString()?.let { it to value } }.toMap()

private fun Any?.asHealthInt(): Int = when (this) {
  is Number -> toInt()
  is String -> toIntOrNull() ?: 0
  else -> 0
}
