package skillbill.mcp.scaffold
import skillbill.error.shellcontent.InvalidScaffoldPayloadError

internal fun parseStringList(
  args: Map<String, Any?>,
  key: String,
): List<String> {
  val raw =
    args[key]
      ?: throw InvalidScaffoldPayloadError(
        "Scaffold payload field '$key' must be a list of strings.",
      )
  if (raw !is List<*>) {
    throw InvalidScaffoldPayloadError(
      "Scaffold payload field '$key' must be a list of strings.",
    )
  }
  return parseStringListValue(raw, key)
}

internal fun parseStringListOrEmpty(
  args: Map<String, Any?>,
  key: String,
): List<String> {
  val raw = args[key] ?: return emptyList()
  if (raw !is List<*>) {
    throw InvalidScaffoldPayloadError(
      "Scaffold payload field '$key' must be a list of strings.",
    )
  }
  return parseStringListValue(raw, key)
}

internal fun parseRoutingSignalList(
  routing: Map<*, *>,
  key: String,
  fieldName: String,
): List<String>? {
  if (!routing.containsKey(key)) return null
  val raw =
    routing[key]
      ?: throw InvalidScaffoldPayloadError(
        "Scaffold payload field '$fieldName' must be a list of strings.",
      )
  if (raw !is List<*>) {
    throw InvalidScaffoldPayloadError(
      "Scaffold payload field '$fieldName' must be a list of strings.",
    )
  }
  return parseStringListValue(raw, fieldName)
}

internal fun parseStringListValue(
  raw: List<*>,
  fieldName: String,
): List<String> {
  val mapped =
    raw.map { value ->
      value as? String
        ?: throw InvalidScaffoldPayloadError(
          "Scaffold payload field '$fieldName' must contain only non-empty strings.",
        )
    }
  if (mapped.any(String::isBlank)) {
    throw InvalidScaffoldPayloadError(
      "Scaffold payload field '$fieldName' must contain only non-empty strings.",
    )
  }
  return mapped
}

internal fun parseBooleanOrFalse(
  args: Map<String, Any?>,
  key: String,
): Boolean {
  if (!args.containsKey(key)) return false
  return args[key] as? Boolean
    ?: throw InvalidScaffoldPayloadError(
      "Scaffold payload field '$key' must be a boolean when provided.",
    )
}

internal fun requireOptionalNonBlank(
  args: Map<String, Any?>,
  key: String,
): String? {
  if (!args.containsKey(key)) return null
  val value =
    args[key] as? String
      ?: throw InvalidScaffoldPayloadError(
        "Scaffold payload field '$key' must be a non-empty string when provided.",
      )
  if (value.isBlank()) {
    throw InvalidScaffoldPayloadError(
      "Scaffold payload field '$key' must be a non-empty string when provided.",
    )
  }
  return value
}

internal fun requireStringInMap(
  layer: Map<*, *>,
  fieldLabel: String,
  key: String,
): String {
  val value =
    layer[key] as? String
      ?: throw InvalidScaffoldPayloadError(
        "Scaffold payload field '$fieldLabel' must be a non-empty string.",
      )
  if (value.isBlank()) {
    throw InvalidScaffoldPayloadError(
      "Scaffold payload field '$fieldLabel' must be a non-empty string.",
    )
  }
  return value
}
