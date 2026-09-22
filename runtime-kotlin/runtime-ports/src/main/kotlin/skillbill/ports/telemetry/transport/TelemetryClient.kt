package skillbill.ports.telemetry.transport
import skillbill.ports.telemetry.model.TelemetryOutboxRecord
import skillbill.telemetry.model.RemoteStatsRequest
import skillbill.telemetry.model.TelemetryDeliveryReport
import skillbill.telemetry.model.TelemetryProxyCapabilities
import skillbill.telemetry.model.TelemetryRemoteStatsResult
import skillbill.telemetry.model.TelemetrySettings

interface TelemetryClient {
  fun sendBatch(
    settings: TelemetrySettings,
    rows: List<TelemetryOutboxRecord>,
  ): TelemetryDeliveryReport

  fun fetchProxyCapabilities(settings: TelemetrySettings): TelemetryProxyCapabilities

  fun fetchRemoteStats(
    settings: TelemetrySettings,
    request: RemoteStatsRequest,
  ): TelemetryRemoteStatsResult
}
