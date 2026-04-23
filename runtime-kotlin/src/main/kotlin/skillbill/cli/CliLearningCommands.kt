package skillbill.cli

import skillbill.db.DatabaseRuntime
import skillbill.learnings.CreateLearningRequest
import skillbill.learnings.LearningStore
import skillbill.learnings.LearningsRuntime
import skillbill.learnings.UpdateLearningRequest
import skillbill.learnings.learningPayload

internal fun learningsCommand(
  cursor: ArgumentCursor,
  context: CliRuntimeContext,
  dbOverride: String?,
): CliExecutionResult {
  val subcommand = cursor.take()
  return when (subcommand) {
    "add" -> learningsAddCommand(cursor, context, dbOverride)
    "list" -> learningsListCommand(cursor, context, dbOverride)
    "show" -> learningsShowCommand(cursor, context, dbOverride)
    "resolve" -> learningsResolveCommand(cursor, context, dbOverride)
    "edit" -> learningsEditCommand(cursor, context, dbOverride)
    "disable" -> learningsStatusCommand(cursor, context, dbOverride, "disabled")
    "enable" -> learningsStatusCommand(cursor, context, dbOverride, "active")
    "delete" -> learningsDeleteCommand(cursor, context, dbOverride)
    else -> throw IllegalArgumentException("Unsupported learnings command '$subcommand'.")
  }
}

private fun learningsAddCommand(
  cursor: ArgumentCursor,
  context: CliRuntimeContext,
  dbOverride: String?,
): CliExecutionResult {
  var scope = "global"
  var scopeKey = ""
  var title: String? = null
  var rule = ""
  var reason = ""
  var fromRun: String? = null
  var fromFinding: String? = null
  var format = "text"
  while (cursor.hasNext()) {
    when (val token = cursor.take()) {
      "--scope" -> scope = cursor.requireValue(token)
      "--scope-key" -> scopeKey = cursor.requireValue(token)
      "--title" -> title = cursor.requireValue(token)
      "--rule" -> rule = cursor.requireValue(token)
      "--reason" -> reason = cursor.requireValue(token)
      "--from-run" -> fromRun = cursor.requireValue(token)
      "--from-finding" -> fromFinding = cursor.requireValue(token)
      "--format" -> format = requireFormat(cursor.requireValue(token))
      else -> throw IllegalArgumentException("Unknown option '$token' for learnings add.")
    }
  }
  require(!title.isNullOrBlank()) { "--title is required." }
  require(rule.isNotBlank()) { "--rule is required." }
  require(!fromRun.isNullOrBlank()) { "--from-run is required." }
  require(!fromFinding.isNullOrBlank()) { "--from-finding is required." }
  DatabaseRuntime.openDb(dbOverride, context.environment, context.userHome).use { openDb ->
    val learningId =
      LearningStore.addLearning(
        openDb.connection,
        CreateLearningRequest(scope, scopeKey, title, rule, reason, fromRun, fromFinding),
      )
    val record = LearningStore.getLearning(openDb.connection, learningId)
    return learningRecordResult(openDb.dbPath.toString(), record, format)
  }
}

private fun learningsListCommand(
  cursor: ArgumentCursor,
  context: CliRuntimeContext,
  dbOverride: String?,
): CliExecutionResult {
  var status = "all"
  var format = "text"
  while (cursor.hasNext()) {
    when (val token = cursor.take()) {
      "--status" -> status = cursor.requireValue(token)
      "--format" -> format = requireFormat(cursor.requireValue(token))
      else -> throw IllegalArgumentException("Unknown option '$token' for learnings list.")
    }
  }
  DatabaseRuntime.openDb(dbOverride, context.environment, context.userHome).use { openDb ->
    val entries = LearningStore.listLearnings(openDb.connection, status).map(::learningPayload)
    return if (format == "json") {
      payloadResult(
        linkedMapOf("db_path" to openDb.dbPath.toString(), "learnings" to entries),
        format,
      )
    } else {
      CliExecutionResult(exitCode = 0, stdout = CliOutput.learnings(entries))
    }
  }
}

private fun learningsShowCommand(
  cursor: ArgumentCursor,
  context: CliRuntimeContext,
  dbOverride: String?,
): CliExecutionResult {
  var id: Int? = null
  var format = "text"
  while (cursor.hasNext()) {
    when (val token = cursor.take()) {
      "--id" -> id = cursor.requireValue(token).toInt()
      "--format" -> format = requireFormat(cursor.requireValue(token))
      else -> throw IllegalArgumentException("Unknown option '$token' for learnings show.")
    }
  }
  require(id != null) { "--id is required." }
  DatabaseRuntime.openDb(dbOverride, context.environment, context.userHome).use { openDb ->
    val record = LearningStore.getLearning(openDb.connection, id)
    return learningRecordResult(openDb.dbPath.toString(), record, format)
  }
}

private fun learningsResolveCommand(
  cursor: ArgumentCursor,
  context: CliRuntimeContext,
  dbOverride: String?,
): CliExecutionResult {
  var repo: String? = null
  var skill: String? = null
  var reviewSessionId: String? = null
  var format = "text"
  while (cursor.hasNext()) {
    when (val token = cursor.take()) {
      "--repo" -> repo = cursor.requireValue(token)
      "--skill" -> skill = cursor.requireValue(token)
      "--review-session-id" -> reviewSessionId = cursor.requireValue(token)
      "--format" -> format = requireFormat(cursor.requireValue(token))
      else -> throw IllegalArgumentException("Unknown option '$token' for learnings resolve.")
    }
  }
  DatabaseRuntime.openDb(dbOverride, context.environment, context.userHome).use { openDb ->
    val (repoScopeKey, skillName, rows) =
      LearningsRuntime.resolveLearnings(openDb.connection, repo, skill)
    val payloadEntries = rows.map(::learningPayload)
    reviewSessionId?.takeIf(String::isNotBlank)?.let {
      LearningsRuntime.saveSessionLearnings(
        openDb.connection,
        it,
        learningsSessionJson(skillName, payloadEntries),
      )
    }
    val payload =
      learningsResolvePayload(
        openDb.dbPath.toString(),
        repoScopeKey,
        skillName,
        reviewSessionId,
        payloadEntries,
      )
    return if (format == "json") {
      payloadResult(payload, format)
    } else {
      CliExecutionResult(
        exitCode = 0,
        stdout =
        CliOutput.resolvedLearnings(
          repoScopeKey,
          skillName,
          learningScopePrecedence,
          payloadEntries,
        ),
        payload = payload,
      )
    }
  }
}

private fun learningsEditCommand(
  cursor: ArgumentCursor,
  context: CliRuntimeContext,
  dbOverride: String?,
): CliExecutionResult {
  var id: Int? = null
  var scope: String? = null
  var scopeKey: String? = null
  var title: String? = null
  var rule: String? = null
  var reason: String? = null
  var format = "text"
  while (cursor.hasNext()) {
    when (val token = cursor.take()) {
      "--id" -> id = cursor.requireValue(token).toInt()
      "--scope" -> scope = cursor.requireValue(token)
      "--scope-key" -> scopeKey = cursor.requireValue(token)
      "--title" -> title = cursor.requireValue(token)
      "--rule" -> rule = cursor.requireValue(token)
      "--reason" -> reason = cursor.requireValue(token)
      "--format" -> format = requireFormat(cursor.requireValue(token))
      else -> throw IllegalArgumentException("Unknown option '$token' for learnings edit.")
    }
  }
  require(id != null) { "--id is required." }
  require(listOf(scope, scopeKey, title, rule, reason).any { it != null }) {
    "Learning edit requires at least one field to update."
  }
  DatabaseRuntime.openDb(dbOverride, context.environment, context.userHome).use { openDb ->
    val record =
      LearningStore.editLearning(
        openDb.connection,
        UpdateLearningRequest(id, scope, scopeKey, title, rule, reason),
      )
    return learningRecordResult(openDb.dbPath.toString(), record, format)
  }
}

private fun learningsStatusCommand(
  cursor: ArgumentCursor,
  context: CliRuntimeContext,
  dbOverride: String?,
  status: String,
): CliExecutionResult {
  var id: Int? = null
  var format = "text"
  while (cursor.hasNext()) {
    when (val token = cursor.take()) {
      "--id" -> id = cursor.requireValue(token).toInt()
      "--format" -> format = requireFormat(cursor.requireValue(token))
      else -> throw IllegalArgumentException("Unknown option '$token' for learnings status.")
    }
  }
  require(id != null) { "--id is required." }
  DatabaseRuntime.openDb(dbOverride, context.environment, context.userHome).use { openDb ->
    val record = LearningStore.setLearningStatus(openDb.connection, id, status)
    return learningRecordResult(openDb.dbPath.toString(), record, format)
  }
}

private fun learningsDeleteCommand(
  cursor: ArgumentCursor,
  context: CliRuntimeContext,
  dbOverride: String?,
): CliExecutionResult {
  var id: Int? = null
  var format = "text"
  while (cursor.hasNext()) {
    when (val token = cursor.take()) {
      "--id" -> id = cursor.requireValue(token).toInt()
      "--format" -> format = requireFormat(cursor.requireValue(token))
      else -> throw IllegalArgumentException("Unknown option '$token' for learnings delete.")
    }
  }
  require(id != null) { "--id is required." }
  DatabaseRuntime.openDb(dbOverride, context.environment, context.userHome).use { openDb ->
    LearningStore.deleteLearning(openDb.connection, id)
    return payloadResult(
      linkedMapOf("db_path" to openDb.dbPath.toString(), "deleted_learning_id" to id),
      format,
    )
  }
}
