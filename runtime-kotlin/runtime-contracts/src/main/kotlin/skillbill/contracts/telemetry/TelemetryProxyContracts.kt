package skillbill.contracts.telemetry

import skillbill.contracts.SharedPayloadKeys

data class TelemetryProxyBatchEvent(
  val event: String,
  val distinctId: String,
  val properties: Map<String, Any?>,
  val timestamp: String,
) {
  fun toPayload(): Map<String, Any?> = mapOf(
    "event" to event,
    "distinct_id" to distinctId,
    "properties" to properties,
    "timestamp" to timestamp,
  )
}

data class TelemetryProxyBatchPayload(
  val batch: List<TelemetryProxyBatchEvent>,
) {
  fun toPayload(): Map<String, Any?> = mapOf("batch" to batch.map { it.toPayload() })
}

data class RemoteStatsQueryPayload(
  val workflow: String,
  val dateFrom: String,
  val dateTo: String,
  val groupBy: String = "",
) {
  fun toPayload(): Map<String, Any?> = buildMap {
    put(TelemetryProxyPayloadKeys.WORKFLOW, workflow)
    put(TelemetryProxyPayloadKeys.DATE_FROM, dateFrom)
    put(TelemetryProxyPayloadKeys.DATE_TO, dateTo)
    if (groupBy.isNotBlank()) {
      put(TelemetryProxyPayloadKeys.GROUP_BY, groupBy)
    }
  }
}

fun defaultProxyCapabilities(proxyUrl: String, capabilitiesUrl: String): Map<String, Any?> = mapOf(
  SharedPayloadKeys.CONTRACT_VERSION to "0",
  TelemetryProxyPayloadKeys.SOURCE to "remote_proxy",
  TelemetryProxyPayloadKeys.PROXY_URL to proxyUrl,
  TelemetryProxyPayloadKeys.CAPABILITIES_URL to capabilitiesUrl,
  TelemetryProxyPayloadKeys.SUPPORTS_INGEST to true,
  TelemetryProxyPayloadKeys.SUPPORTS_STATS to false,
  TelemetryProxyPayloadKeys.SUPPORTED_WORKFLOWS to emptyList<String>(),
)
