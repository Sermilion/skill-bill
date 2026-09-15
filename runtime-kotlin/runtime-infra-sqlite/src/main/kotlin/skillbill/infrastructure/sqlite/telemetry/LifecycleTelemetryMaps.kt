package skillbill.infrastructure.sqlite.telemetry

import skillbill.contracts.telemetry.TelemetryMeasurementAvailability

fun Map<String, Any?>.stringOrEmpty(name: String): String = this[name]?.toString().orEmpty()

fun Map<String, Any?>.intOrZero(name: String): Int = when (val value = this[name]) {
  is Number -> value.toInt()
  is String -> value.toIntOrNull() ?: 0
  else -> 0
}

fun Map<String, Any?>.longOrZero(name: String): Long = when (val value = this[name]) {
  is Number -> value.toLong()
  is String -> value.toLongOrNull() ?: 0L
  else -> 0L
}

fun Map<String, Any?>.booleanFromInt(name: String): Boolean = intOrZero(name) != 0

/** Null-preserving read: a column that was never written stays absent instead of collapsing to zero. */
fun Map<String, Any?>.nullableInt(name: String): Int? = when (val value = this[name]) {
  is Number -> value.toInt()
  is String -> value.toIntOrNull()
  else -> null
}

/**
 * The availability a row declares for [name], or [TelemetryMeasurementAvailability.UNKNOWN] when the
 * row predates the column. Unknown is never rewritten into a measured value.
 */
fun Map<String, Any?>.availability(name: String): TelemetryMeasurementAvailability =
  TelemetryMeasurementAvailability.fromWireOrUnknown(stringOrEmpty(name))
