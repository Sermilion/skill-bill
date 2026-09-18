package skillbill.infrastructure.http

import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.telemetry.TelemetryProxyPayloadKeys
import skillbill.telemetry.TELEMETRY_PROXY_CONTRACT_VERSION
import skillbill.telemetry.model.CustomFieldMap
import skillbill.telemetry.model.TelemetryOpenDocument
import skillbill.telemetry.model.TelemetryProxyCapabilities
import skillbill.telemetry.model.TelemetryRemoteStatsResult
import skillbill.ports.diagnostics.RuntimeDiagnostics

internal fun Map<String, Any?>.toTelemetryProxyCapabilities(
  proxyUrl: String,
  capabilitiesUrl: String,
  diagnostics: RuntimeDiagnostics,
): TelemetryProxyCapabilities {
  val supportedWorkflows =
    (this[TelemetryProxyPayloadKeys.SUPPORTED_WORKFLOWS] as? List<*>)
      ?.mapNotNull { value ->
        if (value is String) {
          value
        } else {
          diagnostics.warning(
            "seam=telemetry.capabilities.supported_workflows expected=String used=${value ?: "null"}",
          )
          null
        }
      }
      .orEmpty()
  return TelemetryProxyCapabilities(
    contractVersion = this[SharedPayloadKeys.CONTRACT_VERSION]?.toString() ?: TELEMETRY_PROXY_CONTRACT_VERSION,
    source = this[TelemetryProxyPayloadKeys.SOURCE]?.toString() ?: "remote_proxy",
    proxyUrl = this[TelemetryProxyPayloadKeys.PROXY_URL]?.toString() ?: proxyUrl,
    capabilitiesUrl = this[TelemetryProxyPayloadKeys.CAPABILITIES_URL]?.toString() ?: capabilitiesUrl,
    supportsIngest = this[TelemetryProxyPayloadKeys.SUPPORTS_INGEST] as? Boolean
      ?: !containsKey(TelemetryProxyPayloadKeys.SUPPORTS_INGEST),
    supportsStats = this[TelemetryProxyPayloadKeys.SUPPORTS_STATS] as? Boolean ?: false,
    supportedWorkflows = supportedWorkflows,
    supportsEventDeduplication = this[TelemetryProxyPayloadKeys.SUPPORTS_EVENT_DEDUPLICATION] != false,
    additionalFields = CustomFieldMap.from(
      filterKeys { key ->
        key != SharedPayloadKeys.CONTRACT_VERSION &&
          key != TelemetryProxyPayloadKeys.SOURCE &&
          key != TelemetryProxyPayloadKeys.PROXY_URL &&
          key != TelemetryProxyPayloadKeys.CAPABILITIES_URL &&
          key != TelemetryProxyPayloadKeys.SUPPORTS_INGEST &&
          key != TelemetryProxyPayloadKeys.SUPPORTS_STATS &&
          key != TelemetryProxyPayloadKeys.SUPPORTED_WORKFLOWS &&
          key != TelemetryProxyPayloadKeys.SUPPORTS_EVENT_DEDUPLICATION
      },
    ),
  )
}

internal fun Map<String, Any?>.toTelemetryRemoteStatsResult(
  workflow: String,
  dateFrom: String,
  dateTo: String,
  groupBy: String,
  statsUrl: String,
  capabilities: TelemetryProxyCapabilities,
): TelemetryRemoteStatsResult {
  return TelemetryRemoteStatsResult(
    workflow = this[TelemetryProxyPayloadKeys.WORKFLOW]?.toString() ?: workflow,
    dateFrom = this[TelemetryProxyPayloadKeys.DATE_FROM]?.toString() ?: dateFrom,
    dateTo = this[TelemetryProxyPayloadKeys.DATE_TO]?.toString() ?: dateTo,
    source = this[TelemetryProxyPayloadKeys.SOURCE]?.toString() ?: "remote_proxy",
    statsUrl = this["stats_url"]?.toString() ?: statsUrl,
    groupBy = this[TelemetryProxyPayloadKeys.GROUP_BY]?.toString()
      ?: groupBy.takeIf(String::isNotBlank),
    capabilities = capabilities,
    metrics = TelemetryOpenDocument.from(
      filterKeys { key ->
        key != TelemetryProxyPayloadKeys.WORKFLOW &&
          key != TelemetryProxyPayloadKeys.DATE_FROM &&
          key != TelemetryProxyPayloadKeys.DATE_TO &&
          key != TelemetryProxyPayloadKeys.SOURCE &&
          key != "stats_url" &&
          key != TelemetryProxyPayloadKeys.GROUP_BY
      },
    ),
  )
}
