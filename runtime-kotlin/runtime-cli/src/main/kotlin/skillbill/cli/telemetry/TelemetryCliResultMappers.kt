package skillbill.cli.telemetry

import skillbill.application.telemetry.model.TelemetryMutationResult
import skillbill.application.telemetry.model.TelemetryStatusResult
import skillbill.application.telemetry.model.TelemetrySyncStatusResult
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.telemetry.TelemetryProxyPayloadKeys
import skillbill.telemetry.model.TelemetryProxyCapabilities
import skillbill.telemetry.model.TelemetryRemoteStatsResult

internal fun TelemetryStatusResult.toCliMap(): Map<String, Any?> = linkedMapOf<String, Any?>(
  "config_path" to configPath,
  "db_path" to dbPath,
  "telemetry_enabled" to telemetryEnabled,
  "telemetry_level" to telemetryLevel,
  "sync_target" to syncTarget,
  "remote_configured" to remoteConfigured,
  "proxy_configured" to proxyConfigured,
  TelemetryProxyPayloadKeys.PROXY_URL to proxyUrl,
  "custom_proxy_url" to customProxyUrl,
  "pending_events" to pendingEvents,
  "last_synced_at" to lastSyncedAt,

  "last_sync_state" to if (lastSyncedAt == null) "never_synced" else "synced",
).apply {
  installId?.let { put(TelemetryProxyPayloadKeys.INSTALL_ID, it) }
  batchSize?.let { put("batch_size", it) }
  latestError?.let { put("latest_error", it) }
  if (blockedEvents > 0) {
    put("blocked_events", blockedEvents)
  }
}

internal fun TelemetrySyncStatusResult.toCliMap(): Map<String, Any?> = linkedMapOf<String, Any?>(
  "config_path" to configPath,
  "telemetry_enabled" to telemetryEnabled,
  "telemetry_level" to telemetryLevel,
  "sync_target" to syncTarget,
  "remote_configured" to remoteConfigured,
  "proxy_configured" to proxyConfigured,
  TelemetryProxyPayloadKeys.PROXY_URL to proxyUrl,
  "custom_proxy_url" to customProxyUrl,
  "sync_status" to syncStatus,
  "synced_events" to syncedEvents,
  "pending_events" to pendingEvents,
).apply {
  message?.let { put("message", it) }
}

internal fun TelemetryMutationResult.toCliMap(): Map<String, Any?> = linkedMapOf(
  "config_path" to configPath,
  "telemetry_enabled" to telemetryEnabled,
  "telemetry_level" to telemetryLevel,
  "sync_target" to syncTarget,
  "remote_configured" to remoteConfigured,
  "proxy_configured" to proxyConfigured,
  TelemetryProxyPayloadKeys.PROXY_URL to proxyUrl,
  "custom_proxy_url" to customProxyUrl,
  TelemetryProxyPayloadKeys.INSTALL_ID to installId,
  "cleared_events" to clearedEvents,
)

internal fun TelemetryProxyCapabilities.toCliMap(): Map<String, Any?> = linkedMapOf<String, Any?>(
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

internal fun TelemetryRemoteStatsResult.toCliMap(): Map<String, Any?> = LinkedHashMap(metrics).apply {
  putIfAbsent(TelemetryProxyPayloadKeys.WORKFLOW, workflow)
  putIfAbsent(TelemetryProxyPayloadKeys.DATE_FROM, dateFrom)
  putIfAbsent(TelemetryProxyPayloadKeys.DATE_TO, dateTo)
  putIfAbsent(TelemetryProxyPayloadKeys.SOURCE, source)
  putIfAbsent(TelemetryProxyPayloadKeys.STATS_URL, statsUrl)
  if (!containsKey(TelemetryProxyPayloadKeys.CAPABILITIES)) {
    put(TelemetryProxyPayloadKeys.CAPABILITIES, capabilities.toCliMap())
  }
  groupBy?.takeIf(String::isNotBlank)?.let { putIfAbsent(TelemetryProxyPayloadKeys.GROUP_BY, it) }
}
