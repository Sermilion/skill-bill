package skillbill.ports.telemetry.transport

import skillbill.telemetry.model.TelemetrySettings

interface TelemetrySettingsProvider {
  fun load(materialize: Boolean = false): TelemetrySettings
}
