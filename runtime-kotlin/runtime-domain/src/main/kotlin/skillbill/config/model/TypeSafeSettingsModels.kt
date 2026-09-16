package skillbill.config.model

import skillbill.contracts.typesafe.SystemOneConfigPayloadKeys
import skillbill.contracts.typesafe.SystemOneDefaults
import skillbill.telemetry.model.TelemetryConfigDocument
import skillbill.workflow.engine.model.TelemetryOpenDocument

data class TypeSafeSettings(
  val enabled: Boolean = false,
  val apiKey: String? = null,
  val baseUrl: String = SystemOneDefaults.BASE_URL,
  val defaultModel: String = SystemOneDefaults.MODEL,
) {
  companion object {
    val DISABLED: TypeSafeSettings = TypeSafeSettings()
  }
}

data class TypeSafeSettingsPatch(
  val enabled: Boolean? = null,
  val apiKey: String? = null,
  val baseUrl: String? = null,
  val defaultModel: String? = null,
) {
  fun isEmpty(): Boolean = enabled == null && apiKey == null && baseUrl == null && defaultModel == null
}

sealed interface TypeSafeSettingsParse {
  data class Valid(val settings: TypeSafeSettings) : TypeSafeSettingsParse

  data class Invalid(
    val keyPath: String,
    val value: String,
    val reason: String,
  ) : TypeSafeSettingsParse
}

fun parseTypeSafeSettings(raw: Any?): TypeSafeSettingsParse = try {
  TypeSafeSettingsParse.Valid(parseTypeSafeMapping(raw))
} catch (failure: InvalidTypeSafeSettings) {
  failure.invalid
}

fun TelemetryConfigDocument.withTypeSafeSettings(patch: TypeSafeSettingsPatch): TelemetryConfigDocument {
  val updatedPayload = payload.toMutableMap()
  val current = typeSafeObject(updatedPayload[SystemOneConfigPayloadKeys.ROOT])
  patch.enabled?.let { current[SystemOneConfigPayloadKeys.ENABLED] = it }
  patch.apiKey?.let { current[SystemOneConfigPayloadKeys.API_KEY] = it }
  patch.baseUrl?.let { current[SystemOneConfigPayloadKeys.BASE_URL] = it }
  patch.defaultModel?.let { current[SystemOneConfigPayloadKeys.DEFAULT_MODEL] = it }
  updatedPayload[SystemOneConfigPayloadKeys.ROOT] = current
  return TelemetryConfigDocument(TelemetryOpenDocument.from(updatedPayload))
}

private fun parseTypeSafeMapping(raw: Any?): TypeSafeSettings {
  if (raw == null) {
    return TypeSafeSettings.DISABLED
  }
  val root = raw as? Map<*, *> ?: invalidTypeSafe(SystemOneConfigPayloadKeys.ROOT, raw, "must be a mapping.")
  val fields = root.entries.associate { (key, value) -> key.toString() to value }
  fields.entries.firstOrNull { (key, _) -> key !in TYPESAFE_FIELDS }?.let { (key, value) ->
    invalidTypeSafe("${SystemOneConfigPayloadKeys.ROOT}.$key", value, "is not a supported typesafe field.")
  }
  return TypeSafeSettings(
    enabled = booleanField(SystemOneConfigPayloadKeys.ENABLED, fields, default = false),
    apiKey = optionalString(SystemOneConfigPayloadKeys.API_KEY, fields),
    baseUrl = optionalString(SystemOneConfigPayloadKeys.BASE_URL, fields) ?: SystemOneDefaults.BASE_URL,
    defaultModel = optionalString(SystemOneConfigPayloadKeys.DEFAULT_MODEL, fields) ?: SystemOneDefaults.MODEL,
  )
}

private fun typeSafeObject(raw: Any?): MutableMap<String, Any?> = when (raw) {
  null -> mutableMapOf()
  is Map<*, *> ->
    raw.entries
      .filter { it.key is String }
      .associate { it.key as String to it.value }
      .toMutableMap()
  else -> throw IllegalArgumentException(
    "Machine config '${SystemOneConfigPayloadKeys.ROOT}' must be an object.",
  )
}

private fun booleanField(key: String, fields: Map<String, Any?>, default: Boolean): Boolean {
  val value = fields[key]
  return when (value) {
    null -> default
    is Boolean -> value
    else -> invalidTypeSafe("${SystemOneConfigPayloadKeys.ROOT}.$key", value, "must be a boolean.")
  }
}

private fun optionalString(key: String, fields: Map<String, Any?>): String? {
  val value = fields[key] ?: return null
  val text = (value as? String)?.trim()?.takeIf(String::isNotBlank)
    ?: invalidTypeSafe("${SystemOneConfigPayloadKeys.ROOT}.$key", value, "must be a non-blank string.")
  return text
}

private fun invalidTypeSafe(keyPath: String, value: Any?, reason: String): Nothing = throw InvalidTypeSafeSettings(
  TypeSafeSettingsParse.Invalid(keyPath = keyPath, value = value?.toString() ?: "null", reason = reason),
)

private class InvalidTypeSafeSettings(
  val invalid: TypeSafeSettingsParse.Invalid,
) : RuntimeException()

private val TYPESAFE_FIELDS: Set<String> = setOf(
  SystemOneConfigPayloadKeys.ENABLED,
  SystemOneConfigPayloadKeys.API_KEY,
  SystemOneConfigPayloadKeys.BASE_URL,
  SystemOneConfigPayloadKeys.DEFAULT_MODEL,
)
