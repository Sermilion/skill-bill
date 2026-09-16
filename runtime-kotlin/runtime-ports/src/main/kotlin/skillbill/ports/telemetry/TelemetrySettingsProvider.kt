package skillbill.ports.telemetry

import skillbill.telemetry.model.TelemetrySettings
import kotlin.coroutines.cancellation.CancellationException

interface TelemetrySettingsProvider {
  fun load(materialize: Boolean = false): TelemetrySettings

  fun loadOrNull(materialize: Boolean = false): TelemetrySettings? = try {
    load(materialize)
  } catch (error: CancellationException) {
    throw error
  } catch (error: InterruptedException) {
    throw error
  } catch (_: Exception) {
    null
  }
}
