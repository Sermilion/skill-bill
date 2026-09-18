package skillbill.infrastructure.sqlite.review
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.review.ReviewVerificationSignalKeys
import skillbill.contracts.review.SqliteReviewTelemetryPayloadKeys
import skillbill.contracts.telemetry.SqliteLifecycleTelemetryMaterializationPayloadKeys
import skillbill.infrastructure.sqlite.core.bindAll
import skillbill.infrastructure.sqlite.telemetry.enqueueTelemetry
import skillbill.ports.telemetry.model.toReviewFinishedTelemetryPayload
import skillbill.review.model.REVIEW_FINISHED_LEGACY_CONTRACT_VERSION
import skillbill.review.model.REVIEW_FINISHED_LEGACY_REGENERATED_EVENT_NAME
import skillbill.review.model.REVIEW_STAGE_DEGRADATION_CONTRACT_VERSION
import java.sql.Connection

internal fun migrateLegacyTelemetryOutboxLedger(connection: Connection) {
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
  connection.prepareStatement(
    """
    SELECT id, payload_json
    FROM telemetry_outbox
    WHERE event_name = 'skillbill_review_finished'
      AND synced_at IS NULL
    ORDER BY id
    """.trimIndent(),
  ).use { statement ->
    statement.executeQuery().use { resultSet ->
      while (resultSet.next()) {
        migrateLegacyReviewFinishedRow(connection, resultSet.getLong("id"), resultSet.getString("payload_json"))
      }
    }
  }
}

internal fun materializeReviewFinishedPayload(connection: Connection, payload: Map<String, Any?>): Map<String, Any?> {
  if (payload.isEmpty() || !isLegacyReviewFinished(payload)) return payload
  val reviewRunId = payload.stringHealthValue("review_run_id")
  if (reviewRunId.isBlank()) return payload
  if (reviewRunRowExists(connection, reviewRunId)) {
    return regenerateReviewFinishedPayload(connection, payload, reviewRunId)
  }
  return if (payload[SharedPayloadKeys.CONTRACT_VERSION]?.toString() == REVIEW_FINISHED_LEGACY_CONTRACT_VERSION) {
    emptyMap()
  } else {
    payload
  }
}

private fun migrateLegacyReviewFinishedRow(connection: Connection, outboxId: Long, raw: String) {
  val payload = parseHealthJsonObject(raw)
  if (payload.isEmpty() || !isLegacyReviewFinished(payload)) return
  val reviewRunId = payload.stringHealthValue("review_run_id")
  if (reviewRunId.isBlank() || !reviewRunRowExists(connection, reviewRunId)) return
  val rewritten = regenerateReviewFinishedPayload(connection, payload, reviewRunId)
  rewriteOutboxPayload(connection, outboxId, rewritten)
  enqueueTelemetry(
    connection,
    REVIEW_FINISHED_LEGACY_REGENERATED_EVENT_NAME,
    linkedMapOf(
      SqliteLifecycleTelemetryMaterializationPayloadKeys.EVENT_NAME to REVIEW_FINISHED_LEGACY_REGENERATED_EVENT_NAME,
      SharedPayloadKeys.CONTRACT_VERSION to REVIEW_STAGE_DEGRADATION_CONTRACT_VERSION,
      ReviewVerificationSignalKeys.REVIEW_RUN_ID to reviewRunId,
      SqliteReviewTelemetryPayloadKeys.FROM_VERSION to (
        payload[SharedPayloadKeys.CONTRACT_VERSION]?.toString() ?: REVIEW_FINISHED_LEGACY_CONTRACT_VERSION
        ),
      SqliteReviewTelemetryPayloadKeys.TO_VERSION to REVIEW_STAGE_DEGRADATION_CONTRACT_VERSION,
    ),
  )
}

private fun isLegacyReviewFinished(payload: Map<String, Any?>): Boolean {
  val version = payload[SharedPayloadKeys.CONTRACT_VERSION]?.toString()
  return version == REVIEW_FINISHED_LEGACY_CONTRACT_VERSION ||
    !payload.containsKey("verification") ||
    !payload.containsKey("adjudication") ||
    !payload.containsKey("refutation_rate_by_stage") ||
    !payload.containsKey("rejected_verdict_counts") ||
    !payload.containsKey("severity_adjustment_counts") ||
    !payload.containsKey("resolved_tier")
}

private fun regenerateReviewFinishedPayload(
  connection: Connection,
  payload: Map<String, Any?>,
  reviewRunId: String,
): Map<String, Any?> {
  val regenerated = ReviewStatsRuntime.buildReviewFinishedPayload(
    ReviewFinishedPayloadBuildRequest(connection = connection, reviewRunId = reviewRunId),
  )
    .toReviewFinishedTelemetryPayload()
    .toPayload()
  return LinkedHashMap(payload).apply {
    putAll(regenerated)
    put(SharedPayloadKeys.CONTRACT_VERSION, REVIEW_STAGE_DEGRADATION_CONTRACT_VERSION)
  }
}

private fun reviewRunRowExists(connection: Connection, reviewRunId: String): Boolean =
  connection.prepareStatement("SELECT 1 FROM review_runs WHERE review_run_id = ?").use { statement ->
    statement.bindAll(reviewRunId)
    statement.executeQuery().use { resultSet -> resultSet.next() }
  }

private fun rewriteOutboxPayload(connection: Connection, outboxId: Long, payload: Map<String, Any?>) {
  connection.prepareStatement(
    "UPDATE telemetry_outbox SET payload_json = ? WHERE id = ?",
  ).use { statement ->
    statement.bindAll(JsonCodec.mapToJsonString(payload), outboxId)
    statement.executeUpdate()
  }
}
