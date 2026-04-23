package skillbill.cli

import skillbill.review.FeedbackTelemetryOptions
import skillbill.telemetry.TelemetryConfigRuntime
import skillbill.telemetry.TelemetrySettings

internal fun payloadResult(payload: Map<String, Any?>, format: String): CliExecutionResult =
  CliExecutionResult(exitCode = 0, stdout = CliOutput.emit(payload, format), payload = payload)

internal fun parseFormat(cursor: ArgumentCursor): String {
  var format = "text"
  while (cursor.hasNext()) {
    when (val token = cursor.take()) {
      "--format" -> format = requireFormat(cursor.requireValue(token))
      else -> throw IllegalArgumentException("Unknown option '$token'.")
    }
  }
  return format
}

internal fun parseSingleFormat(cursor: ArgumentCursor, commandName: String): String {
  val format = parseFormat(cursor)
  cursor.rejectExtraArguments(commandName)
  return format
}

internal fun requireFormat(rawValue: String): String {
  require(rawValue in setOf("text", "json")) { "--format must be one of: text, json." }
  return rawValue
}

internal fun loadTelemetrySettings(context: CliRuntimeContext): TelemetrySettings =
  TelemetryConfigRuntime.loadTelemetrySettings(
    environment = context.environment,
    userHome = context.userHome,
  )

internal fun telemetrySettingsOrNull(context: CliRuntimeContext): TelemetrySettings? =
  runCatching { loadTelemetrySettings(context) }.getOrNull()

internal fun feedbackTelemetryOptions(context: CliRuntimeContext): FeedbackTelemetryOptions {
  val settings = telemetrySettingsOrNull(context)
  return FeedbackTelemetryOptions(
    enabled = settings?.enabled ?: false,
    level = settings?.level ?: "off",
  )
}
