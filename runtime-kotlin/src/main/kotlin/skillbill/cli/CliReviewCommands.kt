package skillbill.cli

import skillbill.db.DatabaseRuntime
import skillbill.review.FeedbackRequest
import skillbill.review.NumberedFinding
import skillbill.review.ReviewRuntime
import skillbill.review.ReviewStatsRuntime
import skillbill.review.TriageRuntime

internal fun importReviewCommand(
  cursor: ArgumentCursor,
  context: CliRuntimeContext,
  dbOverride: String?,
): CliExecutionResult {
  val input = cursor.take()
  val format = parseSingleFormat(cursor, "import-review")
  val (text, sourcePath) = ReviewRuntime.readInput(input, context.stdinText)
  val review = ReviewRuntime.parseReview(text)
  DatabaseRuntime.openDb(dbOverride, context.environment, context.userHome).use { openDb ->
    ReviewRuntime.saveImportedReview(openDb.connection, review, sourcePath)
    if (review.findings.isEmpty()) {
      val settings = telemetrySettingsOrNull(context)
      ReviewStatsRuntime.updateReviewFinishedTelemetryState(
        openDb.connection,
        review.reviewRunId,
        enabled = settings?.enabled ?: false,
        level = settings?.level ?: "off",
      )
    }
    return payloadResult(
      linkedMapOf(
        "db_path" to openDb.dbPath.toString(),
        "review_run_id" to review.reviewRunId,
        "review_session_id" to review.reviewSessionId,
        "finding_count" to review.findings.size,
        "routed_skill" to review.routedSkill,
        "detected_scope" to review.detectedScope,
        "detected_stack" to review.detectedStack,
        "execution_mode" to review.executionMode,
      ),
      format,
    )
  }
}

internal fun recordFeedbackCommand(
  cursor: ArgumentCursor,
  context: CliRuntimeContext,
  dbOverride: String?,
): CliExecutionResult {
  var runId: String? = null
  var event: String? = null
  var note = ""
  val findings = mutableListOf<String>()
  var format = "text"
  while (cursor.hasNext()) {
    when (val token = cursor.take()) {
      "--run-id" -> runId = cursor.requireValue(token)
      "--event" -> event = cursor.requireValue(token)
      "--finding" -> findings += cursor.requireValue(token)
      "--note" -> note = cursor.requireValue(token)
      "--format" -> format = requireFormat(cursor.requireValue(token))
      else -> throw IllegalArgumentException("Unknown option '$token' for record-feedback.")
    }
  }
  require(!runId.isNullOrBlank()) { "--run-id is required." }
  require(!event.isNullOrBlank()) { "--event is required." }
  require(findings.isNotEmpty()) { "At least one --finding is required." }
  DatabaseRuntime.openDb(dbOverride, context.environment, context.userHome).use { openDb ->
    TriageRuntime.recordFeedback(
      openDb.connection,
      FeedbackRequest(runId, findings, event, note),
      feedbackTelemetryOptions(context),
    )
    return payloadResult(
      linkedMapOf(
        "db_path" to openDb.dbPath.toString(),
        "review_run_id" to runId,
        "outcome_type" to event,
        "recorded_findings" to findings.size,
      ),
      format,
    )
  }
}

internal fun triageCommand(
  cursor: ArgumentCursor,
  context: CliRuntimeContext,
  dbOverride: String?,
): CliExecutionResult {
  val request = parseTriageRequest(cursor)
  DatabaseRuntime.openDb(dbOverride, context.environment, context.userHome).use { openDb ->
    val numberedFindings = ReviewRuntime.fetchNumberedFindings(openDb.connection, request.runId)
    if (request.listOnly || request.decisions.isEmpty()) {
      return findingsListResult(openDb.dbPath.toString(), request.runId, numberedFindings, request.format)
    }
    val recorded = applyTriageDecisions(openDb.connection, request.runId, numberedFindings, request.decisions, context)
    val payload =
      linkedMapOf(
        "db_path" to openDb.dbPath.toString(),
        "review_run_id" to request.runId,
        "recorded" to recorded,
      )
    return if (request.format == "json") {
      payloadResult(payload, request.format)
    } else {
      CliExecutionResult(
        exitCode = 0,
        stdout = CliOutput.triageResult(request.runId, recorded),
        payload = payload,
      )
    }
  }
}

internal fun reviewStatsCommand(
  cursor: ArgumentCursor,
  context: CliRuntimeContext,
  dbOverride: String?,
): CliExecutionResult {
  var runId: String? = null
  var format = "text"
  while (cursor.hasNext()) {
    when (val token = cursor.take()) {
      "--run-id" -> runId = cursor.requireValue(token)
      "--format" -> format = requireFormat(cursor.requireValue(token))
      else -> throw IllegalArgumentException("Unknown option '$token' for stats.")
    }
  }
  return featureStatsPayloadResult(dbOverride, context, format) { connection ->
    ReviewStatsRuntime.statsPayload(connection, runId)
  }
}

internal fun featureImplementStatsCommand(
  cursor: ArgumentCursor,
  context: CliRuntimeContext,
  dbOverride: String?,
): CliExecutionResult = featureStatsPayloadResult(dbOverride, context, parseFormat(cursor)) { connection ->
  ReviewStatsRuntime.featureImplementStatsPayload(connection)
}

internal fun featureVerifyStatsCommand(
  cursor: ArgumentCursor,
  context: CliRuntimeContext,
  dbOverride: String?,
): CliExecutionResult = featureStatsPayloadResult(dbOverride, context, parseFormat(cursor)) { connection ->
  ReviewStatsRuntime.featureVerifyStatsPayload(connection)
}

private fun findingsListResult(
  dbPath: String,
  runId: String,
  numberedFindings: List<skillbill.review.NumberedFinding>,
  format: String,
): CliExecutionResult {
  val findings = numberedFindings.map(::findingPayload)
  val payload = linkedMapOf("db_path" to dbPath, "review_run_id" to runId, "findings" to findings)
  return if (format == "json") {
    payloadResult(payload, format)
  } else {
    CliExecutionResult(exitCode = 0, stdout = CliOutput.numberedFindings(runId, findings), payload = payload)
  }
}

private fun parseTriageRequest(cursor: ArgumentCursor): TriageRequest {
  var runId: String? = null
  val decisions = mutableListOf<String>()
  var listOnly = false
  var format = "text"
  while (cursor.hasNext()) {
    when (val token = cursor.take()) {
      "--run-id" -> runId = cursor.requireValue(token)
      "--decision" -> decisions += cursor.requireValue(token)
      "--list" -> listOnly = true
      "--format" -> format = requireFormat(cursor.requireValue(token))
      else -> throw IllegalArgumentException("Unknown option '$token' for triage.")
    }
  }
  require(!runId.isNullOrBlank()) { "--run-id is required." }
  return TriageRequest(runId, decisions, listOnly, format)
}

private fun applyTriageDecisions(
  connection: java.sql.Connection,
  runId: String,
  numberedFindings: List<NumberedFinding>,
  decisions: List<String>,
  context: CliRuntimeContext,
): List<Map<String, Any?>> {
  val parsedDecisions = TriageRuntime.parseTriageDecisions(decisions, numberedFindings)
  parsedDecisions.forEach { decision ->
    TriageRuntime.recordFeedback(
      connection,
      FeedbackRequest(runId, listOf(decision.findingId), decision.outcomeType, decision.note),
      feedbackTelemetryOptions(context),
    )
  }
  return parsedDecisions.map { decision ->
    linkedMapOf(
      "number" to decision.number,
      "finding_id" to decision.findingId,
      "outcome_type" to decision.outcomeType,
      "note" to decision.note,
    )
  }
}

private data class TriageRequest(
  val runId: String,
  val decisions: List<String>,
  val listOnly: Boolean,
  val format: String,
)
