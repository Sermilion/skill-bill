package skillbill.infrastructure.sqlite.review.stage.telemetry
import skillbill.contracts.JsonCodec
import skillbill.infrastructure.sqlite.core.ops.bindAll
import skillbill.infrastructure.sqlite.review.stage.runtime.ReviewRuntime
import skillbill.infrastructure.sqlite.telemetry.outbox.TelemetryOutboxStore
import skillbill.ports.telemetry.model.toReviewFinishedTelemetryPayload
import skillbill.review.model.ReviewExecutionMode
import skillbill.review.model.ReviewFinishedTelemetry
import skillbill.review.model.ReviewSummary
import java.sql.Connection

internal data class ReviewTelemetryState(
  internal val enabled: Boolean,
  internal val level: String,
)

internal fun resolveTelemetryState(enabled: Boolean?, level: String?): ReviewTelemetryState {
  return ReviewTelemetryState(
    enabled = enabled ?: false,
    level = level ?: "off",
  )
}

internal fun reviewAlreadyEmittedForSession(connection: Connection, sessionId: String, reviewRunId: String): Boolean {
  if (sessionId.isEmpty()) {
    return false
  }
  return connection.prepareStatement(
    """
    SELECT 1 FROM review_runs
    WHERE review_session_id = ?
      AND review_run_id != ?
      AND review_finished_event_emitted_at IS NOT NULL
    """.trimIndent(),
  ).use { statement ->
    statement.bindAll(sessionId, reviewRunId)
    statement.executeQuery().use { resultSet -> resultSet.next() }
  }
}

internal fun ensureReviewFinishedTimestamp(
  connection: Connection,
  reviewRunId: String,
  reviewSummary: ReviewSummary,
): ReviewSummary {
  if (!reviewSummary.reviewFinishedAt.isNullOrEmpty()) {
    return reviewSummary
  }
  connection.prepareStatement(
    """
    UPDATE review_runs
    SET review_finished_at = CURRENT_TIMESTAMP
    WHERE review_run_id = ? AND review_finished_at IS NULL
    """.trimIndent(),
  ).use { statement ->
    statement.bindAll(reviewRunId)
    statement.executeUpdate()
  }
  return ReviewRuntime.fetchReviewSummary(connection, reviewRunId)
}

internal fun ensureTerminalReviewState(
  connection: Connection,
  reviewRunId: String,
  executionMode: ReviewExecutionMode?,
) {
  connection.prepareStatement(
    """
    UPDATE review_runs
    SET review_finished_at = COALESCE(NULLIF(review_finished_at, ''), CURRENT_TIMESTAMP),
        execution_mode = COALESCE(NULLIF(execution_mode, ''), NULLIF(?, ''), 'unresolved')
    WHERE review_run_id = ?
    """.trimIndent(),
  ).use { statement ->
    statement.bindAll(executionMode?.wireValue, reviewRunId)
    statement.executeUpdate()
  }
}

internal fun finalizeReviewFinishedTelemetry(
  connection: Connection,
  reviewRunId: String,
  reviewSummary: ReviewSummary,
  payload: ReviewFinishedTelemetry,
  telemetryEnabled: Boolean,
): ReviewFinishedTelemetry? = when {
  reviewSummary.orchestratedRun -> payload
  !reviewSummary.reviewFinishedEventEmittedAt.isNullOrEmpty() -> {
    if (telemetryEnabled) {
      updatePendingReviewFinishedEvent(connection, reviewSummary.reviewSessionId.orEmpty(), payload)
    }
    payload
  }
  else -> {
    enqueueTelemetryEvent(connection, "skillbill_review_finished", payload, telemetryEnabled)
    if (telemetryEnabled) {
      markReviewFinishedEventEmitted(connection, reviewRunId)
    }
    payload
  }
}

internal fun enqueueTelemetryEvent(
  connection: Connection,
  eventName: String,
  payload: ReviewFinishedTelemetry,
  enabled: Boolean,
) {
  if (enabled) {
    TelemetryOutboxStore(connection).enqueue(
      eventName,
      JsonCodec.mapToJsonString(payload.toReviewFinishedTelemetryPayload().toPayload()),
    )
  }
}

internal fun updatePendingReviewFinishedEvent(
  connection: Connection,
  reviewSessionId: String,
  payload: ReviewFinishedTelemetry,
) {
  connection.prepareStatement(
    """
    UPDATE telemetry_outbox
    SET payload_json = ?
    WHERE event_name = 'skillbill_review_finished'
      AND synced_at IS NULL
      AND json_extract(payload_json, '$.review_session_id') = ?
    """.trimIndent(),
  ).use { statement ->
    statement.bindAll(
      JsonCodec.mapToJsonString(payload.toReviewFinishedTelemetryPayload().toPayload()),
      reviewSessionId,
    )
    statement.executeUpdate()
  }
}

internal fun markReviewFinishedEventEmitted(connection: Connection, reviewRunId: String) {
  connection.prepareStatement(
    """
    UPDATE review_runs
    SET review_finished_event_emitted_at = CURRENT_TIMESTAMP
    WHERE review_run_id = ?
    """.trimIndent(),
  ).use { statement ->
    statement.bindAll(reviewRunId)
    statement.executeUpdate()
  }
}
