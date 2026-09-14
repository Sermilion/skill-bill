package skillbill.goalrunner

import skillbill.contracts.SharedPayloadKeys
import skillbill.workflow.goal.model.asGoalWorkflowArtifactMap
import skillbill.goalrunner.model.GoalRunnerStoredOutcome
import skillbill.goalrunner.model.GoalRunnerTerminalStatus

fun goalContinuationTerminalStatus(status: String?): GoalRunnerTerminalStatus? =
  status?.let(GoalRunnerTerminalStatus::fromWire)

fun goalContinuationOutcome(
  artifacts: Any,
  issueKey: String,
  subtaskId: Int,
  suppressPr: Boolean,
): GoalRunnerStoredOutcome? = (artifacts.asGoalWorkflowArtifactMap("goal continuation outcome artifacts")["goal_continuation_outcome"] as? Map<*, *>)
  ?.takeIf { outcome -> outcome[SharedPayloadKeys.ISSUE_KEY]?.toString() == issueKey }
  ?.takeIf { outcome -> outcome[SharedPayloadKeys.SUBTASK_ID].asGoalRunnerIntOrNull() == subtaskId }
  ?.let { outcome ->
    goalContinuationTerminalStatus(outcome[SharedPayloadKeys.STATUS]?.toString())?.let { status ->
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
