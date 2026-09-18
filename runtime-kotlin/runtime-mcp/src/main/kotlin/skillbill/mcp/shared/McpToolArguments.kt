package skillbill.mcp.shared

import skillbill.contracts.JsonCodec
import skillbill.error.InvalidMcpToolArgumentError

internal fun Map<String, Any?>.string(name: String): String =
  optionalString(name) ?: throw InvalidMcpToolArgumentError("<unknown>", name, "is required")

internal fun Map<String, Any?>.optionalString(name: String): String? =
  when (val value = this[name]) {
    null -> null
    is String -> value
    else -> throw InvalidMcpToolArgumentError("<unknown>", name, "must be a string")
  }

internal fun Map<String, Any?>.boolean(name: String): Boolean = when (val value = this[name]) {
  null -> false
  is Boolean -> value
  else -> throw InvalidMcpToolArgumentError("<unknown>", name, "must be a boolean")
}

internal fun Map<String, Any?>.int(name: String, default: Int): Int = when (val value = this[name]) {
  null -> default
  is Int -> value
  is Long -> value.toInt()
  is Double -> value.toInt()
  else -> throw InvalidMcpToolArgumentError("<unknown>", name, "must be an integer")
}

internal fun Map<String, Any?>.optionalInt(name: String): Int? = when (val value = this[name]) {
  null -> null
  is Int -> value
  is Long -> {
    if (value < Int.MIN_VALUE.toLong() || value > Int.MAX_VALUE.toLong()) {
      throw InvalidMcpToolArgumentError("<unknown>", name, "must fit in a 32-bit integer")
    }
    value.toInt()
  }
  is Double -> {
    if (value % 1.0 != 0.0 || value < Int.MIN_VALUE || value > Int.MAX_VALUE) {
      throw InvalidMcpToolArgumentError("<unknown>", name, "must be an integer")
    }
    value.toInt()
  }
  else -> throw InvalidMcpToolArgumentError("<unknown>", name, "must be an integer")
}

internal fun Map<String, Any?>.stringList(name: String): List<String> =
  when (val value = this[name]) {
    null -> emptyList()
    is List<*> -> value.map {
      it as? String ?: throw InvalidMcpToolArgumentError("<unknown>", name, "must contain strings")
    }
    else -> throw InvalidMcpToolArgumentError("<unknown>", name, "must be an array")
  }

internal fun Map<String, Any?>.map(name: String): Map<String, Any?> = optionalMap(name).orEmpty()

internal fun Map<String, Any?>.optionalMap(name: String): Map<String, Any?>? =
  when {
    !containsKey(name) -> null
    else -> JsonCodec.anyToStringAnyMap(this[name])
      ?: throw InvalidMcpToolArgumentError("<unknown>", name, "must be an object")
  }

internal fun Map<String, Any?>.optionalListMap(name: String): List<Map<String, Any?>>? =
  when (val value = this[name]) {
    null -> null
    is List<*> -> value.map {
      JsonCodec.anyToStringAnyMap(it)
        ?: throw InvalidMcpToolArgumentError("<unknown>", name, "must contain objects")
    }
    else -> throw InvalidMcpToolArgumentError("<unknown>", name, "must be an array")
  }

internal fun validateDeclaredArguments(
  toolName: String,
  arguments: Map<String, Any?>,
  schema: Map<String, Any?>,
) {
  val required = (schema["required"] as? List<*>)?.filterIsInstance<String>().orEmpty()
  required.filterNot(arguments::containsKey).firstOrNull()?.let { key ->
    throw InvalidMcpToolArgumentError(toolName, key, "is required")
  }
  val properties = JsonCodec.anyToStringAnyMap(schema["properties"]).orEmpty()
  arguments.forEach { (key, value) ->
    val propertySchema = JsonCodec.anyToStringAnyMap(properties[key]) ?: return@forEach
    val expected = propertySchema["type"] as? String ?: return@forEach
    val valid = when (expected) {
      "string" -> value is String
      "boolean" -> value is Boolean
      "integer" -> value is Int || value is Long || value is Double && value % 1.0 == 0.0
      "array" -> value is List<*>
      "object" -> JsonCodec.anyToStringAnyMap(value) != null
      else -> true
    }
    if (!valid) {
      throw InvalidMcpToolArgumentError(toolName, key, "must be a $expected")
    }
  }
}
