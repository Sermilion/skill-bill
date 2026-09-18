package skillbill.infrastructure.sqlite.review
import skillbill.contracts.JsonCodec
import skillbill.review.model.REVIEW_FINISHED_LEGACY_CONTRACT_VERSION
import skillbill.review.model.REVIEW_STAGE_DEGRADATION_CONTRACT_VERSION
import java.sql.Connection
import skillbill.infrastructure.sqlite.core.bindAll

internal fun loadStandaloneReviewPayloads(connection: Connection): List<ReviewHealthPayload> = connection.prepareStatement(
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

internal fun restampUnsyncedLegacyTelemetry(connection: Connection) {
  connection.prepareStatement(
    """
    UPDATE telemetry_outbox
    SET payload_json = json_set(payload_json, '$.contract_version', ?)
    WHERE synced_at IS NULL
      AND event_name != 'skillbill_review_finished'
      AND json_extract(payload_json, '$.contract_version') = ?
    """.trimIndent(),
  ).use { statement ->
    statement.bindAll(REVIEW_STAGE_DEGRADATION_CONTRACT_VERSION, REVIEW_FINISHED_LEGACY_CONTRACT_VERSION)
    statement.executeUpdate()
  }
}

internal fun Map<String, Any?>.healthInt(key: String): Int = this[key].asHealthInt()

internal fun Map<String, Any?>.stringHealthValue(key: String): String = this[key]?.toString().orEmpty()

internal fun persistLegacyTelemetryRewrites(connection: Connection) {
  restampUnsyncedLegacyTelemetry(connection)
  val unsyncedReviewFinished = connection.prepareStatement(
    """
    SELECT id, payload_json
    FROM telemetry_outbox
    WHERE event_name = 'skillbill_review_finished'
      AND synced_at IS NULL
    ORDER BY id
    """.trimIndent(),
  ).use { statement ->
    statement.executeQuery().use { resultSet ->
      buildList {
        while (resultSet.next()) {
          add(resultSet.getLong("id") to resultSet.getString("payload_json"))
        }
      }
    }
  }
  unsyncedReviewFinished.forEach { (outboxId, raw) ->
    persistLegacyReviewFinishedRow(connection, outboxId, raw)
  }
}

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
