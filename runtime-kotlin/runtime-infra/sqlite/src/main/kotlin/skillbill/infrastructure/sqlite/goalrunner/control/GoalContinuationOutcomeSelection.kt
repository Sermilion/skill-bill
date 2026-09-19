package skillbill.infrastructure.sqlite.goalrunner.control
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.goalrunner.asGoalRunnerIntOrNull
import skillbill.goalrunner.goalContinuationTerminalStatus
import skillbill.goalrunner.model.GoalRunnerStoredOutcome
import skillbill.goalrunner.model.GoalRunnerTerminalStatus
import skillbill.infrastructure.sqlite.goalrunner.manifest.artifacts
import skillbill.infrastructure.sqlite.goalrunner.manifest.current
import skillbill.infrastructure.sqlite.goalrunner.manifest.outcome
import skillbill.infrastructure.sqlite.goalrunner.manifest.snapshot
import skillbill.infrastructure.sqlite.goalrunner.outcome.artifacts
import skillbill.infrastructure.sqlite.goalrunner.outcome.authoritative
import skillbill.infrastructure.sqlite.goalrunner.outcome.candidate
import skillbill.infrastructure.sqlite.goalrunner.outcome.goalContinuation
import skillbill.infrastructure.sqlite.goalrunner.outcome.issueKey
import skillbill.infrastructure.sqlite.goalrunner.outcome.outcome
import skillbill.infrastructure.sqlite.goalrunner.outcome.output
import skillbill.infrastructure.sqlite.goalrunner.outcome.subtaskId
import skillbill.infrastructure.sqlite.goalrunner.outcome.workflowId
import skillbill.ports.goalrunner.persistence.model.GoalContinuationCandidate

internal fun List<GoalContinuationCandidate>.authoritativeOutcomesBySubtask(): Map<Int, GoalRunnerStoredOutcome> =
  groupBy { candidate -> candidate.goalContinuation.subtaskId }
    .mapNotNull { (subtaskId, candidates) ->
      candidates.selectAuthoritativeOutcome()?.let { outcome -> subtaskId to outcome }
    }
    .toMap()

internal fun List<GoalContinuationCandidate>.selectAuthoritativeOutcome(): GoalRunnerStoredOutcome? {
  val completeWinner = asSequence()
    .filter { candidate -> candidate.outcome?.status == GoalRunnerTerminalStatus.COMPLETE }
    .maxWithOrNull(compareBy<GoalContinuationCandidate> { it.snapshot.updatedAt }.thenBy { it.snapshot.workflowId })
  if (completeWinner != null) {
    return completeWinner.outcome
  }
  val fallbackWinner = asSequence()
    .filter { candidate -> candidate.outcome != null }
    .maxWithOrNull(compareBy<GoalContinuationCandidate> { it.snapshot.updatedAt }.thenBy { it.snapshot.workflowId })
  return fallbackWinner?.outcome
}

internal fun staleRunningReason(
  staleWorkflowId: String,
  issueKey: String,
  subtaskId: Int,
  authoritative: GoalRunnerStoredOutcome?,
): String = authoritative?.let { outcome ->
  if (outcome.workflowId == staleWorkflowId) {
    "Goal status reconciliation closed inactive running child '$staleWorkflowId' for issue '$issueKey' " +
      "subtask $subtaskId because a terminal outcome was already durable."
  } else {
    "Goal status reconciliation closed stale running child '$staleWorkflowId' for issue '$issueKey' " +
      "subtask $subtaskId in favor of authoritative ${outcome.status.wireValue} workflow " +
      "'${outcome.workflowId}'."
  }
} ?: (
  "Goal status reconciliation closed stale running child '$staleWorkflowId' for issue '$issueKey' " +
    "subtask $subtaskId because it was no longer active."
  )

internal fun missingResultPrefixTerminalOutcomeArtifact(
  output: Any,
  issueKey: String,
  subtaskId: Int,
  workflowId: String,
): Map<String, Any?>? {
  val wire = JsonCodec.anyToStringAnyMap(output) ?: return null
  return (JsonCodec.anyToStringAnyMap(wire["subtask_outcome"]) ?: wire)
    .takeIf { candidate -> candidate.matchesGoalContinuation(issueKey, subtaskId) }
    ?.let { candidate ->
      candidate[SharedPayloadKeys.STATUS]?.toString()?.let(::goalContinuationTerminalStatus)?.let { status ->
        candidate.toMissingResultPrefixOutcomeArtifact(issueKey, subtaskId, workflowId, status)
      }
    }
}

internal fun Map<String, Any?>.matchesGoalContinuation(issueKey: String, subtaskId: Int): Boolean {
  val candidateIssueKey = this[SharedPayloadKeys.ISSUE_KEY]?.toString()?.takeIf(String::isNotBlank) ?: issueKey
  val candidateSubtaskId = this[SharedPayloadKeys.SUBTASK_ID].asGoalRunnerIntOrNull() ?: subtaskId
  return candidateIssueKey == issueKey && candidateSubtaskId == subtaskId
}

internal fun Map<String, Any?>.toMissingResultPrefixOutcomeArtifact(
  issueKey: String,
  subtaskId: Int,
  workflowId: String,
  status: GoalRunnerTerminalStatus,
): Map<String, Any?> = linkedMapOf<String, Any?>(
  SharedPayloadKeys.ISSUE_KEY to issueKey,
  SharedPayloadKeys.SUBTASK_ID to subtaskId,
  SharedPayloadKeys.STATUS to status.toGoalContinuationWireStatus(),
  SharedPayloadKeys.WORKFLOW_ID to (
    this[SharedPayloadKeys.WORKFLOW_ID]?.toString()?.takeIf(String::isNotBlank) ?: workflowId
    ),
  "last_resumable_step" to (
    this["last_resumable_step"]?.toString()?.takeIf(String::isNotBlank) ?: "preplan"
    ),
).apply {
  this@toMissingResultPrefixOutcomeArtifact["commit_sha"]?.toString()?.takeIf(String::isNotBlank)
    ?.let { put("commit_sha", it) }
  this@toMissingResultPrefixOutcomeArtifact["blocked_reason"]?.toString()?.takeIf(String::isNotBlank)
    ?.let { put("blocked_reason", it) }
}

internal fun GoalRunnerTerminalStatus.toGoalContinuationWireStatus(): String = wireValue

internal fun maxHistorySequence(artifacts: Map<String, Any?>, historyKey: String, current: Int?): Int? {
  val entries = (artifacts[historyKey] as? List<*>).orEmpty()
  var max = current
  entries.forEach { item ->
    val sequence = (item as? Map<*, *>)?.get("sequence_number").asGoalRunnerIntOrNull()
    val currentMax = max
    if (sequence != null && (currentMax == null || sequence > currentMax)) {
      max = sequence
    }
  }
  return max
}
