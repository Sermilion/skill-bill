package skillbill.infrastructure.sqlite.review
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.telemetry.GoalTelemetryPayloadKeys
import skillbill.contracts.telemetry.TelemetryMeasurementAvailability
import skillbill.review.model.GoalBlockedSubtaskSummary
import skillbill.review.model.GoalModeStats
import skillbill.review.model.GoalRunSummary
import skillbill.review.model.GoalWorkflowStats
import skillbill.workflow.model.DecompositionStatus
import skillbill.workflow.model.WorkflowStatus
import java.sql.Connection
import java.util.Locale

private val goalFinishedStatuses = listOf(
  WorkflowStatus.COMPLETED,
  WorkflowStatus.PAUSED,
  WorkflowStatus.BLOCKED,
  WorkflowStatus.ABANDONED,
)
private val goalSubtaskStatuses = listOf(
  DecompositionStatus.COMPLETE,
  DecompositionStatus.BLOCKED,
  DecompositionStatus.SKIPPED,
)

internal fun loadGoalRows(connection: Connection, tableName: String): List<Map<String, Any?>> =
  connection.prepareStatement("SELECT * FROM $tableName").use { statement ->
    statement.executeQuery().use(::collectRows)
  }

internal fun buildGoalStats(runRows: List<Map<String, Any?>>, subtaskRows: List<Map<String, Any?>>): GoalWorkflowStats {
  val runs = runRows.map(::parseGoalRunRow)
  val subtasks = subtaskRows.map(::parseGoalSubtaskRow)
  val finished = runs.filter { it.finishedAt.isNotBlank() }
  val completedRuns = finished.count { it.workflowStatus == WorkflowStatus.COMPLETED }
  val blockedRuns = finished.count { it.workflowStatus == WorkflowStatus.BLOCKED }
  val mostRecent = runs.maxByOrNull { it.startedAt }
  return GoalWorkflowStats(
    totalRuns = runs.size,
    finishedRuns = finished.size,
    inProgressRuns = runs.size - finished.size,
    completionStatusCounts = goalFinishedStatuses.associate { status ->
      status.wireValue to finished.count { it.workflowStatus == status }
    },
    completedRuns = completedRuns,
    completedRate = rate(completedRuns, finished.size),
    blockedRuns = blockedRuns,
    blockedRate = rate(blockedRuns, finished.size),
    subtaskOutcomeCounts = goalSubtaskStatuses.associate { status ->
      status.wireValue to subtasks.count { it.decompositionStatus == status }
    },
    totalSubtaskEvents = subtasks.size,
    averageRunDurationMs = averageMillis(finished.map { it.durationMs }),
    averageSubtaskDurationMs = averageMillis(subtasks.map { it.durationMs }),
    averageAttemptCount = average(subtasks.map { it.attemptCount }),
    mostRecentRun = mostRecent?.let { run ->
      GoalRunSummary(
        workflowId = run.workflowId,
        issueKey = run.issueKey,
        featureName = run.featureName,
        status = run.status,
        startedAt = run.startedAt,
        finishedAt = run.finishedAt,
        durationMs = run.durationMs,
        resumed = run.resumed,
        subtaskTotal = run.subtaskTotal,
      )
    },
    topBlockedSubtasks = subtasks
      .filter { it.decompositionStatus == DecompositionStatus.BLOCKED }
      .map { s ->
        GoalBlockedSubtaskSummary(
          subtaskId = s.subtaskId,
          subtaskName = s.subtaskName,
          issueKey = s.issueKey,
          blockedReason = s.blockedReason ?: "",
          attemptCount = s.attemptCount,
        )
      },
    byMode = buildByModeStats(runs),
    logicalGoals = runs.mapNotNull { it.parentWorkflowId }.distinct().size,
    invocationsWithUnknownGoal = runs.count { it.parentWorkflowId == null },
    goalIdentityAvailability = goalIdentityAvailability(runs).wireValue,
    resumedInvocations = runs.count { it.resumed },
  )
}

private fun goalIdentityAvailability(runs: List<GoalRunRow>): TelemetryMeasurementAvailability {
  val unattributed = runs.count { it.parentWorkflowId == null }
  return when {
    unattributed == 0 -> TelemetryMeasurementAvailability.MEASURED
    unattributed == runs.size -> TelemetryMeasurementAvailability.UNAVAILABLE_NO_DURABLE_STATE
    else -> TelemetryMeasurementAvailability.UNAVAILABLE_INCOMPLETE
  }
}

private const val RETIRED_PROSE_MODE = "prose"

private fun buildByModeStats(runs: List<GoalRunRow>): Map<String, GoalModeStats> = runs
  .filterNot { it.mode == RETIRED_PROSE_MODE }
  .groupBy { it.mode }
  .mapValues { (_, modeRuns) ->
    val modeFinished = modeRuns.filter { it.finishedAt.isNotBlank() }
    val modeCompleted = modeFinished.count { it.workflowStatus == WorkflowStatus.COMPLETED }
    val modeBlocked = modeFinished.count { it.workflowStatus == WorkflowStatus.BLOCKED }
    GoalModeStats(
      totalRuns = modeRuns.size,
      finishedRuns = modeFinished.size,
      inProgressRuns = modeRuns.size - modeFinished.size,
      completedRuns = modeCompleted,
      completedRate = rate(modeCompleted, modeFinished.size),
      blockedRuns = modeBlocked,
      blockedRate = rate(modeBlocked, modeFinished.size),
      averageRunDurationMs = averageMillis(modeFinished.map { it.durationMs }),
    )
  }

internal data class GoalRunRow(
  internal val workflowId: String,
  internal val issueKey: String,
  internal val featureName: String,
  internal val subtaskTotal: Int,
  internal val resumed: Boolean,
  internal val startedAt: String,
  internal val status: String,
  internal val workflowStatus: WorkflowStatus?,
  internal val finishedAt: String,
  internal val durationMs: Long,
  internal val mode: String,
  internal val parentWorkflowId: String?,
)

internal data class GoalSubtaskRow(
  internal val subtaskId: Int,
  internal val subtaskName: String,
  internal val issueKey: String,
  internal val blockedReason: String?,
  internal val status: String,
  internal val decompositionStatus: DecompositionStatus?,
  internal val durationMs: Long,
  internal val attemptCount: Int,
)

private fun parseGoalRunRow(row: Map<String, Any?>): GoalRunRow {
  val identity = "goal_run_sessions[workflow_id=${row[SharedPayloadKeys.WORKFLOW_ID] ?: "<null>"}]"
  val finishedAtRaw = row[GoalTelemetryPayloadKeys.FINISHED_AT]?.toString().orEmpty()
  val finished = finishedAtRaw.isNotBlank()
  if (finished) {
    row.requireNonNegativeInt("subtasks_complete", identity)
    row.requireNonNegativeInt("subtasks_blocked", identity)
    row.requireNonNegativeInt("subtasks_skipped", identity)
  }
  val status = if (finished) row.requireEnum("status", goalFinishedStatuses.map { it.wireValue }, identity) else ""
  return GoalRunRow(
    workflowId = row.requireNonBlankString("workflow_id", identity),
    issueKey = row.requireNonBlankString("issue_key", identity),
    featureName = row.requirePresentString("feature_name", identity),
    subtaskTotal = row.requireNonNegativeInt("subtask_total", identity),
    resumed = row.requireBooleanInt("resumed", identity),
    startedAt = row.requireNonBlankString("started_at", identity),
    status = status,
    workflowStatus = status.takeIf(String::isNotEmpty)?.let(WorkflowStatus::fromWire),
    finishedAt = finishedAtRaw,
    durationMs = if (finished) row.requireNonNegativeLong("finished_duration_ms", identity) else 0L,
    mode = row.requireNonBlankString("mode", identity),
    parentWorkflowId = row[GoalTelemetryPayloadKeys.PARENT_WORKFLOW_ID]?.toString()?.takeIf(String::isNotBlank),
  )
}

private fun parseGoalSubtaskRow(row: Map<String, Any?>): GoalSubtaskRow {
  val identity =
    "goal_subtask_events[issue_key=${row[SharedPayloadKeys.ISSUE_KEY] ?: "<null>"}, " +
      "subtask_id=${row[SharedPayloadKeys.SUBTASK_ID] ?: "<null>"}, " +
      "workflow_id=${row[SharedPayloadKeys.WORKFLOW_ID] ?: "<null>"}]"
  row.requireNonBlankString("started_at", identity)
  row.requireNonBlankString("finished_at", identity)
  val status = row.requireEnum("status", goalSubtaskStatuses.map { it.wireValue }, identity)
  return GoalSubtaskRow(
    subtaskId = row.requirePositiveInt("subtask_id", identity),
    subtaskName = row.requirePresentString("subtask_name", identity),
    issueKey = row.requireNonBlankString("issue_key", identity),
    blockedReason = row[GoalTelemetryPayloadKeys.BLOCKED_REASON]?.toString(),
    status = status,
    decompositionStatus = DecompositionStatus.fromWire(status),
    durationMs = row.requireNonNegativeLong("duration_ms", identity),
    attemptCount = row.requireNonNegativeInt("attempt_count", identity),
  )
}

private fun averageMillis(values: List<Long>): Double = if (values.isEmpty()) {
  0.0
} else {
  String.format(Locale.US, "%.2f", values.sum().toDouble() / values.size).toDouble()
}
