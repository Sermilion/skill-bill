package skillbill.infrastructure.sqlite.review

import skillbill.error.ShellContentContractException

internal class InvalidGoalTelemetryRowError(
  internal val rowIdentity: String,
  internal val reason: String,
  cause: Throwable? = null,
) : ShellContentContractException(
  "Goal telemetry row $rowIdentity is malformed: $reason",
  cause,
)

private fun goalRowError(identity: String, reason: String): Nothing =
  throw InvalidGoalTelemetryRowError(identity, reason)

internal fun Map<String, Any?>.requirePresentString(column: String, identity: String): String =
  (this[column] ?: goalRowError(identity, "required column '$column' is null or missing")).toString()

internal fun Map<String, Any?>.requireNonBlankString(column: String, identity: String): String {
  val value = requirePresentString(column, identity)
  return if (value.isBlank()) goalRowError(identity, "required column '$column' is blank") else value
}

internal fun Map<String, Any?>.requireEnum(column: String, allowed: List<String>, identity: String): String {
  val value = requireNonBlankString(column, identity)
  return if (value in allowed) {
    value
  } else {
    goalRowError(
      identity,
      "column '$column' value '$value' is not one of $allowed",
    )
  }
}

internal fun Map<String, Any?>.requireInt(column: String, identity: String): Int = when (val value = this[column]) {
  null -> goalRowError(identity, "required column '$column' is null or missing")
  is Number -> value.toInt()
  is String -> value.toIntOrNull() ?: goalRowError(identity, "column '$column' value '$value' is not an integer")
  else -> goalRowError(identity, "column '$column' has non-numeric type ${value::class.simpleName}")
}

internal fun Map<String, Any?>.requireLong(column: String, identity: String): Long = when (val value = this[column]) {
  null -> goalRowError(identity, "required column '$column' is null or missing")
  is Number -> value.toLong()
  is String -> value.toLongOrNull() ?: goalRowError(identity, "column '$column' value '$value' is not an integer")
  else -> goalRowError(identity, "column '$column' has non-numeric type ${value::class.simpleName}")
}

internal fun Map<String, Any?>.requireNonNegativeInt(column: String, identity: String): Int {
  val value = requireInt(column, identity)
  return if (value < 0) goalRowError(identity, "column '$column' value '$value' is negative") else value
}

internal fun Map<String, Any?>.requirePositiveInt(column: String, identity: String): Int {
  val value = requireInt(column, identity)
  return if (value < 1) goalRowError(identity, "column '$column' value '$value' must be >= 1") else value
}

internal fun Map<String, Any?>.requireNonNegativeLong(column: String, identity: String): Long {
  val value = requireLong(column, identity)
  return if (value < 0L) goalRowError(identity, "column '$column' value '$value' is negative") else value
}

internal fun Map<String, Any?>.requireBooleanInt(column: String, identity: String): Boolean =
  when (val value = requireInt(column, identity)) {
    0 -> false
    1 -> true
    else -> goalRowError(identity, "column '$column' value '$value' is not a 0/1 boolean")
  }
