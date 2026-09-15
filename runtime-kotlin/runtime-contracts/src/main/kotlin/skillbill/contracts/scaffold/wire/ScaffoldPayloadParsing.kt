package skillbill.contracts.scaffold.wire

import skillbill.error.InvalidScaffoldPayloadError

inline fun <reified T : Any> requireScalar(map: Map<String, Any?>, key: String): T {
  val value = map[key]
    ?: throw InvalidScaffoldPayloadError(
      "Scaffold payload field '$key' is missing (expected ${T::class.simpleName}).",
    )
  if (value !is T) {
    throw InvalidScaffoldPayloadError(
      "Scaffold payload field '$key' has wrong type " +
        "(expected ${T::class.simpleName}, got ${value::class.simpleName}).",
    )
  }
  return value
}

fun requireString(map: Map<String, Any?>, key: String): String {
  val value = map[key] as? String
    ?: throw InvalidScaffoldPayloadError(
      "Scaffold payload field '$key' must be a non-empty string.",
    )
  if (value.isBlank()) {
    throw InvalidScaffoldPayloadError(
      "Scaffold payload field '$key' must be a non-empty string.",
    )
  }
  return value
}

fun requireStringOrDefault(map: Map<String, Any?>, key: String, default: String): String =
  (map[key] as? String)?.takeIf { it.isNotBlank() } ?: default

fun requireInt(map: Map<String, Any?>, key: String): Int {
  val value = map[key]
    ?: throw InvalidScaffoldPayloadError(
      "Scaffold payload field '$key' is missing (expected Int).",
    )
  if (value !is Number) {
    throw InvalidScaffoldPayloadError(
      "Scaffold payload field '$key' has wrong type " +
        "(expected Number-convertible Int, got ${value::class.simpleName}).",
    )
  }
  return value.toInt()
}

fun optionalString(map: Map<String, Any?>, key: String): String? = (map[key] as? String)?.takeIf { it.isNotBlank() }

fun optionalList(map: Map<String, Any?>, key: String): List<*>? {
  val raw = map[key] ?: return null
  if (raw !is List<*>) {
    throw InvalidScaffoldPayloadError(
      "Scaffold payload field '$key' must be a list when provided " +
        "(got ${raw::class.simpleName}).",
    )
  }
  return raw
}
