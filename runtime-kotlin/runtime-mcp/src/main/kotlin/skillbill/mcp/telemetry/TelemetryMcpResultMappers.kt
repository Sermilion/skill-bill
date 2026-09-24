package skillbill.mcp.telemetry

import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.telemetry.TelemetryProxyPayloadKeys
import skillbill.telemetry.model.TelemetryProxyCapabilities
import skillbill.telemetry.model.TelemetryRemoteStatsResult

internal fun TelemetryProxyCapabilities.toMcpMap(): Map<String, Any?> =
  linkedMapOf<String, Any?>(
    SharedPayloadKeys.CONTRACT_VERSION to contractVersion,
    TelemetryProxyPayloadKeys.SOURCE to source,
    TelemetryProxyPayloadKeys.PROXY_URL to proxyUrl,
    TelemetryProxyPayloadKeys.CAPABILITIES_URL to capabilitiesUrl,
    TelemetryProxyPayloadKeys.SUPPORTS_INGEST to supportsIngest,
    TelemetryProxyPayloadKeys.SUPPORTS_STATS to supportsStats,
    TelemetryProxyPayloadKeys.SUPPORTED_WORKFLOWS to supportedWorkflows,
    TelemetryProxyPayloadKeys.SUPPORTS_EVENT_DEDUPLICATION to supportsEventDeduplication,
  ).apply {
    additionalFields.forEach { (key, value) -> putIfAbsent(key, value) }
  }

internal fun TelemetryRemoteStatsResult.toMcpMap(): Map<String, Any?> =
  LinkedHashMap(metrics).apply {
    putIfAbsent(TelemetryProxyPayloadKeys.WORKFLOW, workflow)
    putIfAbsent(TelemetryProxyPayloadKeys.DATE_FROM, dateFrom)
    putIfAbsent(TelemetryProxyPayloadKeys.DATE_TO, dateTo)
    putIfAbsent(TelemetryProxyPayloadKeys.SOURCE, source)
    putIfAbsent(TelemetryProxyPayloadKeys.STATS_URL, statsUrl)
    if (!containsKey(TelemetryProxyPayloadKeys.CAPABILITIES)) {
      put(TelemetryProxyPayloadKeys.CAPABILITIES, capabilities.toMcpMap())
    }
    groupBy?.takeIf(String::isNotBlank)?.let { putIfAbsent(TelemetryProxyPayloadKeys.GROUP_BY, it) }
  }
