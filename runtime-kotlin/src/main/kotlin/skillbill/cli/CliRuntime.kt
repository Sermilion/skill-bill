package skillbill.cli

object CliRuntime {
  fun run(arguments: List<String>, context: CliRuntimeContext = CliRuntimeContext()): CliExecutionResult {
    val cursor = ArgumentCursor(arguments)
    val dbOverride = parseGlobalDbOverride(cursor, context.dbPathOverride)
    val command = cursor.take()
    return when (command) {
      "import-review" -> importReviewCommand(cursor, context, dbOverride)
      "record-feedback" -> recordFeedbackCommand(cursor, context, dbOverride)
      "triage" -> triageCommand(cursor, context, dbOverride)
      "stats" -> reviewStatsCommand(cursor, context, dbOverride)
      "implement-stats", "feature-implement-stats" -> featureImplementStatsCommand(cursor, context, dbOverride)
      "verify-stats", "feature-verify-stats" -> featureVerifyStatsCommand(cursor, context, dbOverride)
      "learnings" -> learningsCommand(cursor, context, dbOverride)
      "telemetry" -> telemetryCommand(cursor, context, dbOverride)
      "version" -> payloadResult(linkedMapOf("version" to skillbill.SkillBillVersion.VALUE), parseFormat(cursor))
      "doctor" -> doctorCommand(cursor, context, dbOverride)
      else -> throw IllegalArgumentException("Unsupported Phase 4 CLI command '$command'.")
    }
  }
}

internal fun parseGlobalDbOverride(cursor: ArgumentCursor, fallback: String?): String? {
  var dbOverride = fallback
  while (cursor.hasNext() && cursor.peek() == "--db") {
    cursor.take()
    dbOverride = cursor.requireValue("--db")
  }
  return dbOverride
}
