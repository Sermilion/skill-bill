package skillbill.contracts.telemetry

enum class TelemetryMeasurementAvailability(val wireValue: String) {
  MEASURED("measured"),
  UNAVAILABLE_NO_DURABLE_STATE("unavailable_no_durable_state"),
  UNAVAILABLE_UNSUPPORTED("unavailable_unsupported"),
  UNAVAILABLE_INCOMPLETE("unavailable_incomplete"),
  UNKNOWN("unknown"),
  ;

  val measured: Boolean get() = this == MEASURED

  companion object {
    fun fromWireOrUnknown(value: String): TelemetryMeasurementAvailability =
      entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
  }
}
