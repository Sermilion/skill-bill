package skillbill.cli.goal.core

import skillbill.cli.goal.status.appendDiffStatusLines
import skillbill.goalrunner.model.GoalRunnerStatusProjection

internal data class GoalWatchRefreshPresentation(
  val refreshIndex: Int,
  val projection: GoalRunnerStatusProjection?,
)

internal data class GoalWatchPresentation(
  val issueKey: String,
  val refreshCount: Int,
  val intervalSeconds: Int,
  val latestRefresh: GoalWatchRefreshPresentation?,
  val stopReason: String,
)

internal fun Map<String, Any?>.withWatchRefresh(refreshIndex: Int): Map<String, Any?> =
  linkedMapOf<String, Any?>("refresh_index" to refreshIndex).apply { putAll(this@withWatchRefresh) }

internal fun goalWatchStopReason(
  projection: GoalRunnerStatusProjection?,
  refreshCount: Int,
  maxRefreshes: Int,
  idleStop: Boolean,
): String? =
  when {
    projection == null -> "not_found"
    projection.paused -> "goal_paused"
    projection.pendingCount == 0 -> "goal_terminal"
    idleStop -> "goal_idle"
    maxRefreshes > 0 && refreshCount >= maxRefreshes -> "max_refreshes"
    else -> null
  }

internal fun goalWatchText(presentation: GoalWatchPresentation): String =
  buildString {
    appendLine("goal: ${presentation.issueKey}")
    appendLine("status: ${if (presentation.latestRefresh?.projection == null) "not_found" else "ok"}")
    appendLine("refresh_count: ${presentation.refreshCount}")
    appendLine("interval_seconds: ${presentation.intervalSeconds}")
    appendLine("stop_reason: ${presentation.stopReason}")
    presentation.latestRefresh?.let { append(goalWatchRefreshText(it)) }
  }

internal fun goalWatchRefreshText(refresh: GoalWatchRefreshPresentation): String =
  buildString {
    val projection = refresh.projection
    appendLine(
      "watch_refresh: index=${refresh.refreshIndex} status=${if (projection == null) "not_found" else "ok"} " +
        "current_subtask=${projection?.currentSubtaskId ?: "none"} " +
        "current_step=${projection?.currentStep ?: "none"} " +
        "execution_liveness=${projection?.executionLiveness?.wireValue ?: "unknown"} " +
        "liveness=${projection?.latestLivenessSignal ?: "none"}",
    )
    projection?.latestObservabilityEvent?.let { event ->
      appendLine(
        "watch_observability: index=${refresh.refreshIndex} phase=${event.workflowPhase} " +
          "role=${event.workerRole} liveness=${event.livenessClass} " +
          "sequence=${event.sequenceNumber}",
      )
    }
    projection?.let { appendDiffStatusLines(it, watchIndex = refresh.refreshIndex.toString()) }
  }
