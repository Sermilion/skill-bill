package skillbill.infrastructure.http

import skillbill.contracts.telemetry.TelemetryProxyPayloadKeys

data class TelemetryProxyBatchEvent(
  val event: String,
  val distinctId: String,
  val properties: Map<String, Any?>,
  val timestamp: String,
  val eventIdentity: String? = null,
) {
  fun toPayload(): Map<String, Any?> =
    buildMap {
      put(TelemetryProxyPayloadKeys.EVENT, event)
      put(TelemetryProxyPayloadKeys.DISTINCT_ID, distinctId)
      put(TelemetryProxyPayloadKeys.PROPERTIES, properties)
      put(TelemetryProxyPayloadKeys.TIMESTAMP, timestamp)
      eventIdentity?.let { put(TelemetryProxyPayloadKeys.EVENT_IDENTITY, it) }
    }
}

data class TelemetryProxyBatchPayload(
  val batch: List<TelemetryProxyBatchEvent>,
) {
  fun toPayload(): Map<String, Any?> = mapOf(TelemetryProxyPayloadKeys.BATCH to batch.map { it.toPayload() })
}

data class RemoteStatsQueryPayload(
  val workflow: String,
  val dateFrom: String,
  val dateTo: String,
  val groupBy: String = "",
) {
  fun toPayload(): Map<String, Any?> =
    buildMap {
      put(TelemetryProxyPayloadKeys.WORKFLOW, workflow)
      put(TelemetryProxyPayloadKeys.DATE_FROM, dateFrom)
      put(TelemetryProxyPayloadKeys.DATE_TO, dateTo)
      if (groupBy.isNotBlank()) {
        put(TelemetryProxyPayloadKeys.GROUP_BY, groupBy)
      }
    }
}
