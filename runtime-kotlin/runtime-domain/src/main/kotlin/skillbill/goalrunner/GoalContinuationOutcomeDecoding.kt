package skillbill.goalrunner

import skillbill.contracts.SharedPayloadKeys
import skillbill.goalrunner.model.GoalRunnerStoredOutcome
import skillbill.goalrunner.model.GoalRunnerTerminalStatus
import skillbill.workflow.goal.model.asGoalWorkflowArtifactMap

fun goalContinuationTerminalStatus(status: String?): GoalRunnerTerminalStatus? =
  status?.let(GoalRunnerTerminalStatus::fromWire)

fun goalContinuationOutcome(
  artifacts: Any,
  issueKey: String,
  subtaskId: Int,
  suppressPr: Boolean,
): GoalRunnerStoredOutcome? {
  val outcome =
    artifacts.asGoalWorkflowArtifactMap("goal continuation outcome artifacts")
      .get("goal_continuation_outcome") as? Map<*, *> ?: return null
  if (outcome[SharedPayloadKeys.ISSUE_KEY]?.toString() != issueKey) return null
  if (outcome[SharedPayloadKeys.SUBTASK_ID].asGoalRunnerIntOrNull() != subtaskId) return null
  return goalContinuationTerminalStatus(outcome[SharedPayloadKeys.STATUS]?.toString())?.let { status ->
    GoalRunnerStoredOutcome(
      status = status,
      workflowId = outcome[SharedPayloadKeys.WORKFLOW_ID]?.toString().orEmpty(),
      commitSha = outcome["commit_sha"]?.toString()?.takeIf(String::isNotBlank),
      blockedReason = outcome["blocked_reason"]?.toString()?.takeIf(String::isNotBlank),
      lastResumableStep = outcome["last_resumable_step"]?.toString()?.takeIf(String::isNotBlank),
      suppressPr = suppressPr,
    )
  }
}
