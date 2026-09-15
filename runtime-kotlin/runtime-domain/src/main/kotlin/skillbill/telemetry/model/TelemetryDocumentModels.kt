package skillbill.telemetry.model

import skillbill.workflow.engine.model.CustomFieldMap
import skillbill.workflow.engine.model.TelemetryOpenDocument

data class TelemetryConfigDocument(
  val payload: TelemetryOpenDocument,
)

data class TelemetryProxyCapabilities(
  val contractVersion: String,
  val source: String,
  val proxyUrl: String,
  val capabilitiesUrl: String,
  val supportsIngest: Boolean,
  val supportsStats: Boolean,
  val supportedWorkflows: List<String>,
  // Defaults to true because a relay forwards event properties verbatim unless it says otherwise;
  // only a relay that explicitly declares it cannot carry the deduplication property opts out.
  val supportsEventDeduplication: Boolean = true,
  val additionalFields: CustomFieldMap = CustomFieldMap.EMPTY,
)

data class TelemetryRemoteStatsResult(
  val workflow: String,
  val dateFrom: String,
  val dateTo: String,
  val source: String,
  val statsUrl: String,
  val groupBy: String?,
  val capabilities: TelemetryProxyCapabilities,
  val metrics: TelemetryOpenDocument,
)
