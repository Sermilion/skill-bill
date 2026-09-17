package skillbill.telemetry.model

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
