package skillbill.cli

import skillbill.SkillBillVersion
import skillbill.db.DatabaseRuntime
import skillbill.telemetry.RemoteStatsRequest
import skillbill.telemetry.TelemetryConfigMutationRuntime
import skillbill.telemetry.TelemetryHttpRuntime
import skillbill.telemetry.TelemetrySyncRuntime
import java.nio.file.Files

internal fun telemetryCommand(
  cursor: ArgumentCursor,
  context: CliRuntimeContext,
  dbOverride: String?,
): CliExecutionResult {
  val subcommand = cursor.take()
  return when (subcommand) {
    "status" -> telemetryStatusCommand(cursor, context, dbOverride)
    "sync" -> telemetrySyncCommand(cursor, context, dbOverride)
    "capabilities" -> telemetryCapabilitiesCommand(cursor, context)
    "stats" -> telemetryStatsCommand(cursor, context)
    "enable" -> telemetryEnableCommand(cursor, context, dbOverride)
    "disable" -> telemetryDisableCommand(cursor, context, dbOverride)
    "set-level" -> telemetrySetLevelCommand(cursor, context, dbOverride)
    else -> throw IllegalArgumentException("Unsupported telemetry command '$subcommand'.")
  }
}

internal fun doctorCommand(
  cursor: ArgumentCursor,
  context: CliRuntimeContext,
  dbOverride: String?,
): CliExecutionResult {
  val format = parseSingleFormat(cursor, "doctor")
  val dbPath = DatabaseRuntime.resolveDbPath(dbOverride, context.environment, context.userHome)
  val settings = telemetrySettingsOrNull(context)
  return payloadResult(
    linkedMapOf(
      "version" to SkillBillVersion.VALUE,
      "db_path" to dbPath.toString(),
      "db_exists" to Files.exists(dbPath),
      "telemetry_enabled" to (settings?.enabled ?: false),
      "telemetry_level" to (settings?.level ?: "off"),
    ),
    format,
  )
}

private fun telemetryStatusCommand(
  cursor: ArgumentCursor,
  context: CliRuntimeContext,
  dbOverride: String?,
): CliExecutionResult {
  val format = parseFormat(cursor)
  val dbPath = DatabaseRuntime.resolveDbPath(dbOverride, context.environment, context.userHome)
  return payloadResult(
    TelemetrySyncRuntime.telemetryStatusPayload(dbPath, loadTelemetrySettings(context)),
    format,
  )
}

private fun telemetrySyncCommand(
  cursor: ArgumentCursor,
  context: CliRuntimeContext,
  dbOverride: String?,
): CliExecutionResult {
  val format = parseFormat(cursor)
  val dbPath = DatabaseRuntime.resolveDbPath(dbOverride, context.environment, context.userHome)
  val result = TelemetrySyncRuntime.syncTelemetry(dbPath, loadTelemetrySettings(context), context.requester)
  val payload = TelemetrySyncRuntime.syncResultPayload(result)
  return CliExecutionResult(
    exitCode = if (result.status == "failed") 1 else 0,
    stdout = CliOutput.emit(payload, format),
    payload = payload,
  )
}

private fun telemetryCapabilitiesCommand(cursor: ArgumentCursor, context: CliRuntimeContext): CliExecutionResult {
  val format = parseFormat(cursor)
  val payload =
    TelemetryHttpRuntime.fetchProxyCapabilities(
      settings = loadTelemetrySettings(context),
      requester = context.requester,
      environment = context.environment,
    )
  return payloadResult(linkedMapOf<String, Any?>().apply { putAll(payload) }, format)
}

private fun telemetryStatsCommand(cursor: ArgumentCursor, context: CliRuntimeContext): CliExecutionResult {
  val workflow = cursor.take()
  var since = ""
  var dateFrom = ""
  var dateTo = ""
  var groupBy = ""
  var format = "text"
  while (cursor.hasNext()) {
    when (val token = cursor.take()) {
      "--since" -> since = cursor.requireValue(token)
      "--date-from" -> dateFrom = cursor.requireValue(token)
      "--date-to" -> dateTo = cursor.requireValue(token)
      "--group-by" -> groupBy = cursor.requireValue(token)
      "--format" -> format = requireFormat(cursor.requireValue(token))
      else -> throw IllegalArgumentException("Unknown option '$token' for telemetry stats.")
    }
  }
  val request = RemoteStatsRequest(mapWorkflow(workflow), since, dateFrom, dateTo, groupBy)
  val payload =
    skillbill.telemetry.TelemetryRemoteStatsRuntime.fetchRemoteStats(
      request = request,
      settings = loadTelemetrySettings(context),
      requester = context.requester,
      environment = context.environment,
    )
  return payloadResult(linkedMapOf<String, Any?>().apply { putAll(payload) }, format)
}

private fun telemetryEnableCommand(
  cursor: ArgumentCursor,
  context: CliRuntimeContext,
  dbOverride: String?,
): CliExecutionResult {
  var level = "anonymous"
  var format = "text"
  while (cursor.hasNext()) {
    when (val token = cursor.take()) {
      "--level" -> level = cursor.requireValue(token)
      "--format" -> format = requireFormat(cursor.requireValue(token))
      else -> throw IllegalArgumentException("Unknown option '$token' for telemetry enable.")
    }
  }
  return telemetryMutationResult(level, context, dbOverride, format)
}

private fun telemetryDisableCommand(
  cursor: ArgumentCursor,
  context: CliRuntimeContext,
  dbOverride: String?,
): CliExecutionResult = telemetryMutationResult("off", context, dbOverride, parseFormat(cursor))

private fun telemetrySetLevelCommand(
  cursor: ArgumentCursor,
  context: CliRuntimeContext,
  dbOverride: String?,
): CliExecutionResult = telemetryMutationResult(cursor.take(), context, dbOverride, parseFormat(cursor))

private fun telemetryMutationResult(
  level: String,
  context: CliRuntimeContext,
  dbOverride: String?,
  format: String,
): CliExecutionResult {
  val dbPath = DatabaseRuntime.resolveDbPath(dbOverride, context.environment, context.userHome)
  val (settings, clearedEvents) =
    TelemetryConfigMutationRuntime.setTelemetryLevel(
      level = level,
      dbPath = dbPath,
      environment = context.environment,
      userHome = context.userHome,
    )
  return payloadResult(telemetryMutationPayload(settings, clearedEvents), format)
}
