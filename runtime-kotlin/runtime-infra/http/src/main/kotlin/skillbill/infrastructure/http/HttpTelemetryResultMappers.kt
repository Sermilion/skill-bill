package skillbill.infrastructure.http

import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.telemetry.TelemetryProxyPayloadKeys
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.telemetry.TELEMETRY_PROXY_CONTRACT_VERSION
import skillbill.telemetry.model.CustomFieldMap
import skillbill.telemetry.model.TelemetryOpenDocument
import skillbill.telemetry.model.TelemetryProxyCapabilities
import skillbill.telemetry.model.TelemetryRemoteStatsResult

internal fun Map<String, Any?>.toTelemetryProxyCapabilities(
  proxyUrl: String,
  capabilitiesUrl: String,
  diagnostics: RuntimeDiagnostics,
): TelemetryProxyCapabilities {
  val supportedWorkflows = readSupportedWorkflows(diagnostics)
  return TelemetryProxyCapabilities(
    contractVersion =
    stringValue(SharedPayloadKeys.CONTRACT_VERSION, TELEMETRY_PROXY_CONTRACT_VERSION),
    source = stringValue(TelemetryProxyPayloadKeys.SOURCE, TelemetryProxyPayloadKeys.REMOTE_PROXY),
    proxyUrl = stringValue(TelemetryProxyPayloadKeys.PROXY_URL, proxyUrl),
    capabilitiesUrl = stringValue(TelemetryProxyPayloadKeys.CAPABILITIES_URL, capabilitiesUrl),
    supportsIngest =
    booleanValue(TelemetryProxyPayloadKeys.SUPPORTS_INGEST)
      ?: !containsKey(TelemetryProxyPayloadKeys.SUPPORTS_INGEST),
    supportsStats = booleanValue(TelemetryProxyPayloadKeys.SUPPORTS_STATS) ?: false,
    supportedWorkflows = supportedWorkflows,
    supportsEventDeduplication =
    booleanValue(TelemetryProxyPayloadKeys.SUPPORTS_EVENT_DEDUPLICATION) ?: true,
    additionalFields = CustomFieldMap.from(
      filterKeys { key -> key !in TELEMETRY_PROXY_CAPABILITY_OWNED_KEYS },
    ),
  )
}

internal fun Map<String, Any?>.toTelemetryRemoteStatsResult(
  context: RemoteStatsResultContext,
): TelemetryRemoteStatsResult {
  return TelemetryRemoteStatsResult(
    workflow = stringValue(TelemetryProxyPayloadKeys.WORKFLOW, context.workflow),
    dateFrom = stringValue(TelemetryProxyPayloadKeys.DATE_FROM, context.dateFrom),
    dateTo = stringValue(TelemetryProxyPayloadKeys.DATE_TO, context.dateTo),
    source = stringValue(TelemetryProxyPayloadKeys.SOURCE, TelemetryProxyPayloadKeys.REMOTE_PROXY),
    statsUrl = stringValue(TelemetryProxyPayloadKeys.STATS_URL, context.statsUrl),
    groupBy = stringValue(TelemetryProxyPayloadKeys.GROUP_BY, context.groupBy)
      .takeIf(String::isNotBlank),
    capabilities = context.capabilities,
    metrics = TelemetryOpenDocument.from(
      filterKeys { key -> key !in TELEMETRY_REMOTE_STATS_OWNED_KEYS },
    ),
  )
}

private val TELEMETRY_PROXY_CAPABILITY_OWNED_KEYS =
  setOf(
    SharedPayloadKeys.CONTRACT_VERSION,
    TelemetryProxyPayloadKeys.SOURCE,
    TelemetryProxyPayloadKeys.PROXY_URL,
    TelemetryProxyPayloadKeys.CAPABILITIES_URL,
    TelemetryProxyPayloadKeys.SUPPORTS_INGEST,
    TelemetryProxyPayloadKeys.SUPPORTS_STATS,
    TelemetryProxyPayloadKeys.SUPPORTED_WORKFLOWS,
    TelemetryProxyPayloadKeys.SUPPORTS_EVENT_DEDUPLICATION,
  )

private val TELEMETRY_REMOTE_STATS_OWNED_KEYS =
  setOf(
    TelemetryProxyPayloadKeys.WORKFLOW,
    TelemetryProxyPayloadKeys.DATE_FROM,
    TelemetryProxyPayloadKeys.DATE_TO,
    TelemetryProxyPayloadKeys.SOURCE,
    TelemetryProxyPayloadKeys.STATS_URL,
    TelemetryProxyPayloadKeys.GROUP_BY,
  )

private fun Map<String, Any?>.readSupportedWorkflows(diagnostics: RuntimeDiagnostics): List<String> =
  (this[TelemetryProxyPayloadKeys.SUPPORTED_WORKFLOWS] as? List<*>)
    ?.mapNotNull { value ->
      value as? String
        ?: run {
          diagnostics.warning(
            "seam=telemetry.capabilities.supported_workflows expected=String used=${value ?: "null"}",
          )
          null
        }
    }
    .orEmpty()

private fun Map<String, Any?>.stringValue(key: String, fallback: String): String = this[key]?.toString() ?: fallback

private fun Map<String, Any?>.booleanValue(key: String): Boolean? = this[key] as? Boolean
