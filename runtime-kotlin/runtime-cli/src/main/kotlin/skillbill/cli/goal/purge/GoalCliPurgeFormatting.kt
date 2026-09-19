package skillbill.cli.goal.purge
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.goalrunner.GoalRunnerPurgePayloadKeys
import skillbill.engine.goalrunner.model.GoalRunnerPurgeResult

internal fun GoalRunnerPurgeResult.toGoalPurgeCliMap(): Map<String, Any?> {
  val status = if (refusalReason != null) "refused" else "ok"
  return linkedMapOf(
    SharedPayloadKeys.STATUS to status,
    SharedPayloadKeys.ISSUE_KEY to issueKey,
    GoalRunnerPurgePayloadKeys.PARENT_WORKFLOW_ID to parentWorkflowId,
    GoalRunnerPurgePayloadKeys.DELETED_CHILD_WORKFLOW_IDS to deletedChildWorkflowIds,
    GoalRunnerPurgePayloadKeys.SPEC_RESTORED to specRestored,
    GoalRunnerPurgePayloadKeys.REFUSAL_REASON to refusalReason,
  )
}

internal fun goalPurgeText(payload: Map<String, Any?>): String {
  val status = payload[SharedPayloadKeys.STATUS]?.toString() ?: "unknown"
  val issueKey = payload[SharedPayloadKeys.ISSUE_KEY]?.toString() ?: ""
  return when (status) {
    "ok" -> "Purged goal runtime state for $issueKey."
    "refused" -> "Purge refused for $issueKey: ${payload[GoalRunnerPurgePayloadKeys.REFUSAL_REASON]}"
    else -> "Goal purge for $issueKey: $status."
  }
}

internal fun Map<String, Any?>.goalPurgeExitCode(): Int = if (this[SharedPayloadKeys.STATUS] == "ok") 0 else 1
