package skillbill.telemetry.model

enum class TelemetryDeliveryOutcome(val wireValue: String) {
  ACCEPTED("accepted"),
  REJECTED("rejected"),
  UNKNOWN("unknown"),
}

data class TelemetryDeliveryReport(
  val outcome: TelemetryDeliveryOutcome,
  val detail: String = "",
)
