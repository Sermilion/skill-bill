package skillbill.db

import skillbill.contracts.JsonSupport
import skillbill.telemetry.model.FeatureTaskRuntimeFinishedRecord
import skillbill.telemetry.model.FeatureTaskRuntimeStartedRecord
import java.sql.Connection

fun saveFeatureTaskRuntimeStarted(connection: Connection, record: FeatureTaskRuntimeStartedRecord) {
  if (rowExists(connection, "feature_task_runtime_sessions", record.sessionId)) {
    updateFeatureTaskRuntimeStarted(connection, record)
    return
  }
  connection.prepareStatement(
    """
    INSERT INTO feature_task_runtime_sessions (
      session_id, feature_size, issue_key, feature_name
    ) VALUES (?, ?, ?, ?)
    """.trimIndent(),
  ).use { statement ->
    statement.bind(
      record.sessionId,
      record.featureSize,
      record.issueKey,
      record.featureName,
    )
    statement.executeUpdate()
  }
}

private fun updateFeatureTaskRuntimeStarted(connection: Connection, record: FeatureTaskRuntimeStartedRecord) {
  connection.prepareStatement(
    """
    UPDATE feature_task_runtime_sessions SET
      feature_size = ?,
      issue_key = ?,
      feature_name = ?
    WHERE session_id = ?
    """.trimIndent(),
  ).use { statement ->
    statement.bind(
      record.featureSize,
      record.issueKey,
      record.featureName,
      record.sessionId,
    )
    statement.executeUpdate()
  }
}

fun saveFeatureTaskRuntimeFinished(connection: Connection, record: FeatureTaskRuntimeFinishedRecord) {
  val completedPhaseIdsJson = listJson(record.completedPhaseIds)
  val phaseOutcomesJson = JsonSupport.mapToJsonString(record.phaseOutcomes)
  val laneStatusesJson = listJson(
    record.reviewLaneStatuses.map { lane ->
      linkedMapOf<String, Any?>(
        "lane_id" to lane.laneId,
        "agent_id" to lane.agentId,
        "status" to lane.status,
        "finding_count" to lane.findingCount,
      )
    },
  )
  if (rowExists(connection, "feature_task_runtime_sessions", record.sessionId)) {
    updateFeatureTaskRuntimeFinished(connection, record, completedPhaseIdsJson, phaseOutcomesJson, laneStatusesJson)
  } else {
    insertFeatureTaskRuntimeFinished(connection, record, completedPhaseIdsJson, phaseOutcomesJson, laneStatusesJson)
  }
}

private fun updateFeatureTaskRuntimeFinished(
  connection: Connection,
  record: FeatureTaskRuntimeFinishedRecord,
  completedPhaseIdsJson: String,
  phaseOutcomesJson: String,
  laneStatusesJson: String,
) {
  connection.prepareStatement(
    """
    UPDATE feature_task_runtime_sessions SET
      completion_status = ?,
      completed_phase_ids = ?,
      phase_outcomes = ?,
      last_incomplete_phase = ?,
      blocked_reason = ?,
      resolved_branch = ?,
      parallel_review_requested = ?,
      default_review_agent_id = ?,
      alternative_review_agent_id = ?,
      review_lane_count = ?,
      review_lane_statuses = ?,
      merged_review_finding_count = ?,
      accepted_review_finding_count = ?,
      rejected_review_finding_count = ?,
      unresolved_review_finding_count = ?,
      finished_at = CURRENT_TIMESTAMP
    WHERE session_id = ?
    """.trimIndent(),
  ).use { statement ->
    statement.bind(
      record.completionStatus,
      completedPhaseIdsJson,
      phaseOutcomesJson,
      record.lastIncompletePhase,
      record.blockedReason,
      record.resolvedBranch,
      if (record.parallelReviewRequested) 1 else 0,
      record.defaultReviewAgentId,
      record.alternativeReviewAgentId,
      record.reviewLaneCount,
      laneStatusesJson,
      record.mergedReviewFindingCount,
      record.acceptedReviewFindingCount,
      record.rejectedReviewFindingCount,
      record.unresolvedReviewFindingCount,
      record.sessionId,
    )
    statement.executeUpdate()
  }
}

private fun insertFeatureTaskRuntimeFinished(
  connection: Connection,
  record: FeatureTaskRuntimeFinishedRecord,
  completedPhaseIdsJson: String,
  phaseOutcomesJson: String,
  laneStatusesJson: String,
) {
  connection.prepareStatement(
    """
    INSERT INTO feature_task_runtime_sessions (
      session_id, completion_status, completed_phase_ids,
      phase_outcomes, last_incomplete_phase, blocked_reason,
      resolved_branch,
      parallel_review_requested, default_review_agent_id,
      alternative_review_agent_id, review_lane_count, review_lane_statuses,
      merged_review_finding_count, accepted_review_finding_count,
      rejected_review_finding_count, unresolved_review_finding_count,
      finished_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
    """.trimIndent(),
  ).use { statement ->
    statement.bind(
      record.sessionId,
      record.completionStatus,
      completedPhaseIdsJson,
      phaseOutcomesJson,
      record.lastIncompletePhase,
      record.blockedReason,
      record.resolvedBranch,
      if (record.parallelReviewRequested) 1 else 0,
      record.defaultReviewAgentId,
      record.alternativeReviewAgentId,
      record.reviewLaneCount,
      laneStatusesJson,
      record.mergedReviewFindingCount,
      record.acceptedReviewFindingCount,
      record.rejectedReviewFindingCount,
      record.unresolvedReviewFindingCount,
    )
    statement.executeUpdate()
  }
}
