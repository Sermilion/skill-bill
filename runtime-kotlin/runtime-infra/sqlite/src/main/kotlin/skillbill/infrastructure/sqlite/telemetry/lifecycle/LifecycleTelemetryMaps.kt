package skillbill.infrastructure.sqlite.telemetry.lifecycle

import skillbill.contracts.telemetry.TelemetryMeasurementAvailability

internal fun Map<String, Any?>.stringOrEmpty(name: String): String = this[name]?.toString().orEmpty()

internal fun Map<String, Any?>.intOrZero(name: String): Int =
  when (val value = this[name]) {
    is Number -> value.toInt()
    is String -> value.toIntOrNull() ?: 0
    else -> 0
  }

internal fun Map<String, Any?>.longOrZero(name: String): Long =
  when (val value = this[name]) {
    is Number -> value.toLong()
    is String -> value.toLongOrNull() ?: 0L
    else -> 0L
  }

internal fun Map<String, Any?>.booleanFromInt(name: String): Boolean = intOrZero(name) != 0

internal fun Map<String, Any?>.nullableInt(name: String): Int? =
  when (val value = this[name]) {
    is Number -> value.toInt()
    is String -> value.toIntOrNull()
    else -> null
  }

internal fun Map<String, Any?>.availability(name: String): TelemetryMeasurementAvailability =
  TelemetryMeasurementAvailability.fromWireOrUnknown(stringOrEmpty(name))
