package skillbill.contracts.telemetry

/**
 * Why a telemetry measurement carries a value or does not. A consumer reads the availability before
 * the value, so an absent measurement can never be mistaken for a measured zero or a measured false.
 * [UNKNOWN] is what a row written before its availability column existed reports; it is never written
 * by a current producer.
 */
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
