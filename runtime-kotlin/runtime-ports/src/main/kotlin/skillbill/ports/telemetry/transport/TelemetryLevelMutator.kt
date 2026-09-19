package skillbill.ports.telemetry.transport
import skillbill.ports.telemetry.model.TelemetryLevelMutationResult

fun interface TelemetryLevelMutator {
  fun setLevel(level: String): TelemetryLevelMutationResult
}
