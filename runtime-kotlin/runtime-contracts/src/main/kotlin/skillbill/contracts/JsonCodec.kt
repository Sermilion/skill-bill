package skillbill.contracts

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import skillbill.error.JsonWrongRootTypeError
import skillbill.error.MalformedJsonTextError
import skillbill.error.UnsupportedJsonValueError
import java.math.BigDecimal
import java.math.BigInteger

object JsonCodec {
  val json: Json =
    Json {
      ignoreUnknownKeys = true
      explicitNulls = false
    }

  fun parseObjectOrNull(rawValue: String): JsonObject? {
    val parsed =
      try {
        json.parseToJsonElement(rawValue)
      } catch (_: Exception) {
        return null
      }
    return parsed as? JsonObject
  }

  fun parseJsonArrayStrict(rawValue: String): List<Any?> {
    val parsed = parseJsonElementStrict(rawValue)
    if (parsed !is JsonArray) {
      throw JsonWrongRootTypeError("a JSON array")
    }
    return parsed.map(::jsonElementToValue)
  }

  fun parseArrayOrEmpty(rawValue: String): List<Any?> {
    val parsed =
      try {
        json.parseToJsonElement(rawValue)
      } catch (_: Exception) {
        return emptyList()
      }
    return if (parsed is JsonArray) {
      parsed.map(::jsonElementToValue)
    } else {
      emptyList()
    }
  }

  fun jsonElementToValue(element: JsonElement): Any? = when (element) {
    JsonNull -> null
    is JsonObject -> element.entries.associate { (key, value) -> key to jsonElementToValue(value) }
    is JsonArray -> element.map(::jsonElementToValue)
    is JsonPrimitive -> jsonPrimitiveToValue(element)
  }

  fun anyToStringAnyMap(value: Any?): Map<String, Any?>? {
    val entries = (value as? Map<*, *>)?.entries ?: return null
    val converted = LinkedHashMap<String, Any?>()
    entries.forEach { (entryKey, entryValue) ->
      if (entryKey !is String) {
        throw UnsupportedJsonValueError("JSON map keys must be strings")
      }
      converted[entryKey] = entryValue
    }
    return converted
  }

  fun anyToStringList(value: Any?): List<String>? = when (value) {
    null -> null
    is List<*> -> value.map { entry -> entry as? String ?: return null }
    else -> null
  }

  fun anyToStringAnyMapList(value: Any?): List<Map<String, Any?>>? = when (value) {
    null -> null
    is List<*> -> value.map { entry ->
      anyToStringAnyMap(entry) ?: return null
    }
    else -> null
  }

  fun mapToJsonString(map: Map<String, Any?>): String =
    json.encodeToString(JsonObject.serializer(), mapToJsonObject(map))

  fun mapToJsonObject(map: Map<String, Any?>): JsonObject = buildJsonObject {
    map.forEach { (key, value) ->
      put(key, valueToJsonElement(value))
    }
  }

  fun valueToJsonElement(value: Any?): JsonElement = jsonPrimitiveElement(value)
    ?: collectionJsonElement(value)
    ?: throw UnsupportedJsonValueError(
      "JSON value type ${value?.let { it::class.simpleName } ?: "null"} is not supported",
    )

  fun parseValue(rawValue: String): Any? = try {
    jsonElementToValue(parseJsonElementStrict(rawValue))
  } catch (error: MalformedJsonTextError) {
    throw error
  } catch (error: JsonWrongRootTypeError) {
    throw error
  } catch (error: SerializationException) {
    throw MalformedJsonTextError(error)
  } catch (error: IllegalArgumentException) {
    throw MalformedJsonTextError(error)
  }

  fun valueToJsonString(value: Any?): String = json.encodeToString(JsonElement.serializer(), valueToJsonElement(value))
}

private fun JsonCodec.parseJsonElementStrict(rawValue: String): JsonElement = try {
  json.parseToJsonElement(rawValue)
} catch (error: SerializationException) {
  throw MalformedJsonTextError(error)
} catch (error: IllegalArgumentException) {
  throw MalformedJsonTextError(error)
}

private fun jsonPrimitiveToValue(primitive: JsonPrimitive): Any? = if (primitive.isString) {
  primitive.contentOrNull
} else {
  primitive.booleanOrNull
    ?: primitive.intOrNull
    ?: primitive.longOrNull
    ?: decodeIntegralPrimitive(primitive)
    ?: decodeDecimalPrimitive(primitive)
    ?: primitive.doubleOrNull
    ?: primitive.contentOrNull
}

private fun decodeDecimalPrimitive(primitive: JsonPrimitive): BigDecimal? {
  val content = primitive.contentOrNull ?: return null
  if (!content.contains('.')) {
    return null
  }
  return runCatching { BigDecimal(content) }.getOrNull()
}

private fun decodeIntegralPrimitive(primitive: JsonPrimitive): Any? {
  val content = primitive.contentOrNull ?: return null
  if (content.contains('.') || content.contains('e', ignoreCase = true)) {
    return null
  }
  return runCatching { BigInteger(content) }.getOrNull()?.let { integral ->
    if (integral >= BigInteger.valueOf(Long.MIN_VALUE) &&
      integral <= BigInteger.valueOf(Long.MAX_VALUE)
    ) {
      integral.longValueExact()
    } else {
      integral
    }
  }
}

@OptIn(ExperimentalSerializationApi::class)
private fun jsonPrimitiveElement(value: Any?): JsonElement? = when (value) {
  null -> JsonNull
  is JsonElement -> value
  is String -> JsonPrimitive(value)
  is Boolean -> JsonPrimitive(value)
  is Int -> JsonPrimitive(value)
  is Long -> JsonPrimitive(value)
  is BigInteger -> JsonUnquotedLiteral(value.toString())
  is BigDecimal -> JsonUnquotedLiteral(value.toPlainString())
  is Float -> if (value.isFinite()) {
    JsonPrimitive(value)
  } else {
    throw UnsupportedJsonValueError("JSON number must be finite")
  }
  is Double -> if (value.isFinite()) {
    JsonPrimitive(value)
  } else {
    throw UnsupportedJsonValueError("JSON number must be finite")
  }
  is Number -> {
    val doubleValue = value.toDouble()
    if (!doubleValue.isFinite()) {
      throw UnsupportedJsonValueError("JSON number must be finite")
    }
    JsonPrimitive(doubleValue)
  }
  else -> null
}

private fun JsonCodec.collectionJsonElement(value: Any?): JsonElement? =
  mapJsonElement(value) ?: iterableJsonElement(value) ?: arrayJsonElement(value)

private fun JsonCodec.mapJsonElement(value: Any?): JsonElement? = (value as? Map<*, *>)?.let { entries ->
  buildJsonObject {
    entries.forEach { (entryKey, entryValue) ->
      if (entryKey !is String) {
        throw UnsupportedJsonValueError("JSON map keys must be strings")
      }
      put(entryKey, valueToJsonElement(entryValue))
    }
  }
}

private fun JsonCodec.iterableJsonElement(value: Any?): JsonElement? = (value as? Iterable<*>)?.let { entries ->
  buildJsonArray {
    entries.forEach { add(valueToJsonElement(it)) }
  }
}

private fun JsonCodec.arrayJsonElement(value: Any?): JsonElement? = (value as? Array<*>)?.let { entries ->
  buildJsonArray {
    entries.forEach { add(valueToJsonElement(it)) }
  }
}
