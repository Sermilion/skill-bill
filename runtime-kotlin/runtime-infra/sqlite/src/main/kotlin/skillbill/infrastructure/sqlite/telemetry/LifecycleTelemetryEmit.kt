package skillbill.infrastructure.sqlite.telemetry
import skillbill.contracts.JsonCodec
import skillbill.contracts.telemetry.LifecycleTelemetryPayloadKeys
import skillbill.infrastructure.sqlite.core.bindAll
import skillbill.infrastructure.sqlite.core.sqliteDiagnostics
import skillbill.review.model.REVIEW_STAGE_DEGRADATION_EVENT_NAME
import skillbill.review.model.ReviewStageDegradationMeasurement
import java.sql.Connection

internal fun emitFeatureTaskRuntimeStarted(connection: Connection, sessionId: String, level: String) {
  val row = lifecycleRow(connection, "feature_task_runtime_sessions", sessionId) ?: return
  emitOnce(
    LifecycleEmitRequest(connection, row, "feature_task_runtime_sessions", "started_event_emitted_at"),
    "skillbill_feature_task_runtime_started",
  ) { featureTaskRuntimeStartedPayload(row, level, telemetryRedactionSalt(connection)) }
}

internal fun emitFeatureTaskRuntimeFinished(connection: Connection, sessionId: String, level: String) {
  val row = lifecycleRow(connection, "feature_task_runtime_sessions", sessionId) ?: return
  emitOnce(
    LifecycleEmitRequest(connection, row, "feature_task_runtime_sessions", "finished_event_emitted_at"),
    "skillbill_feature_task_runtime_finished",
  ) {
    featureTaskRuntimeFinishedPayload(
      row,
      level,
      telemetryRedactionSalt(connection),
      connection.sqliteDiagnostics(),
    )
  }
}

internal fun emitQualityCheckStarted(connection: Connection, sessionId: String) {
  val row = lifecycleRow(connection, "quality_check_sessions", sessionId) ?: return
  emitOnce(
    LifecycleEmitRequest(connection, row, "quality_check_sessions", "started_event_emitted_at"),
    "skillbill_quality_check_started",
  ) { qualityCheckStartedPayload(row) }
}

internal fun emitQualityCheckFinished(connection: Connection, sessionId: String, level: String) {
  val row = lifecycleRow(connection, "quality_check_sessions", sessionId) ?: return
  emitOnce(
    LifecycleEmitRequest(connection, row, "quality_check_sessions", "finished_event_emitted_at"),
    "skillbill_quality_check_finished",
  ) { qualityCheckFinishedPayload(row, level, connection.sqliteDiagnostics()) }
}

internal fun emitFeatureVerifyStarted(connection: Connection, sessionId: String, level: String) {
  val row = lifecycleRow(connection, "feature_verify_sessions", sessionId) ?: return
  emitOnce(
    LifecycleEmitRequest(connection, row, "feature_verify_sessions", "started_event_emitted_at"),
    "skillbill_feature_verify_started",
  ) { featureVerifyStartedPayload(row, level) }
}

internal fun emitFeatureVerifyFinished(connection: Connection, sessionId: String, level: String) {
  val row = lifecycleRow(connection, "feature_verify_sessions", sessionId) ?: return
  emitOnce(
    LifecycleEmitRequest(connection, row, "feature_verify_sessions", "finished_event_emitted_at"),
    "skillbill_feature_verify_finished",
  ) { featureVerifyFinishedPayload(row, level, connection.sqliteDiagnostics()) }
}

internal fun enqueueTelemetry(connection: Connection, eventName: String, payload: Map<String, Any?>) {
  TelemetryOutboxStore(connection).enqueue(eventName, JsonCodec.mapToJsonString(payload))
}

internal fun reviewStageDegradationExists(connection: Connection, record: ReviewStageDegradationMeasurement): Boolean =
  connection.prepareStatement(
    """
  SELECT 1 FROM telemetry_outbox
  WHERE event_name = ?
    AND json_extract(payload_json, '$.review_run_id') = ?
    AND json_extract(payload_json, '$.seam') = ?
    AND json_extract(payload_json, '$.reason') = ?
  LIMIT 1
    """.trimIndent(),
  ).use { statement ->
    statement.bindAll(REVIEW_STAGE_DEGRADATION_EVENT_NAME, record.reviewRunId, record.seam, record.reason.wireValue)
    statement.executeQuery().use { it.next() }
  }

private data class LifecycleEmitRequest(
  internal val connection: Connection,
  internal val row: Map<String, Any?>,
  internal val tableName: String,
  internal val emittedColumn: String,
)

private fun emitOnce(request: LifecycleEmitRequest, eventName: String, payload: () -> Map<String, Any?>) {
  if (request.row.stringOrEmpty(request.emittedColumn).isNotBlank()) {
    return
  }
  enqueueTelemetry(request.connection, eventName, payload())
  markLifecycleEmitted(
    request.connection,
    request.tableName,
    request.emittedColumn,
    request.row.stringOrEmpty(LifecycleTelemetryPayloadKeys.SESSION_ID),
  )
}
