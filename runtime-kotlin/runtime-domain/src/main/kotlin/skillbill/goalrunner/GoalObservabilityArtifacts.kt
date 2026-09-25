package skillbill.goalrunner

import skillbill.contracts.SharedPayloadKeys
import skillbill.error.shellcontent.InvalidGoalObservabilityEventSchemaError
import skillbill.goalrunner.model.GoalObservabilityProgressInput
import skillbill.goalrunner.model.GoalObservabilityRuntimeEventInput
import skillbill.workflow.model.goalreview.GOAL_OBSERVABILITY_LATEST_EVENT_ARTIFACT_KEY
import skillbill.workflow.model.goalreview.GOAL_OBSERVABILITY_RUN_HISTORY_ARTIFACT_KEY
import skillbill.workflow.model.goalreview.GoalObservabilityEvent
import skillbill.workflow.model.goalreview.asGoalWorkflowArtifactMap
import skillbill.workflow.model.goalreview.goalObservabilityHistoryFromArtifacts
import skillbill.workflow.model.persistence.artifact.asExactIntOrNull

object GoalObservabilityArtifacts {
  private data class RequiredProgressFields(
    val progressEvent: Map<*, *>,
    val issueKey: String,
    val subtaskId: Int,
    val timestamp: String,
  )

  fun patchForProgressEvent(
    input: GoalObservabilityProgressInput,
    validator: (Any, String) -> Unit,
  ): Any? =
    eventFrom(input)?.let { event ->
      patchForEvent(
        input.artifacts.asGoalWorkflowArtifactMap("goal observability progress input"),
        event,
        validator,
      )
    }

  fun patchForRuntimeEvent(
    input: GoalObservabilityRuntimeEventInput,
    sequenceNumber: Int,
    validator: (Any, String) -> Unit,
  ): Any =
    patchForEvent(
      artifacts = input.artifacts.asGoalWorkflowArtifactMap("goal observability runtime event input"),
      event =
        GoalObservabilityEvent(
          issueKey = input.request.issueKey,
          subtaskId = input.request.subtaskId,
          workflowId = input.request.workflowId,
          workflowPhase = input.request.workflowPhase,
          workerRole = input.request.workerRole,
          livenessClass = input.request.livenessClass,
          activitySummary = input.request.activitySummary,
          timestamp = input.request.timestamp,
          sequenceNumber = sequenceNumber,
        ),
      validator = validator,
    )

  private fun patchForEvent(
    artifacts: Map<String, Any?>,
    event: GoalObservabilityEvent,
    validator: (Any, String) -> Unit,
  ): Map<String, Any?> {
    val eventMap = event.toArtifactMap()
    validator(eventMap, GOAL_OBSERVABILITY_LATEST_EVENT_ARTIFACT_KEY)
    val history = goalObservabilityHistoryFromArtifacts(artifacts).append(event).toArtifactList()
    history.forEachIndexed { index, item ->
      validator(item, "$GOAL_OBSERVABILITY_RUN_HISTORY_ARTIFACT_KEY[$index]")
    }
    return linkedMapOf(
      GOAL_OBSERVABILITY_LATEST_EVENT_ARTIFACT_KEY to eventMap,
      GOAL_OBSERVABILITY_RUN_HISTORY_ARTIFACT_KEY to history,
    )
  }

  private fun eventFrom(input: GoalObservabilityProgressInput): GoalObservabilityEvent? {
    val fields = requiredProgressFields(input) ?: return null
    return eventFrom(input, fields.progressEvent, fields.issueKey, fields.subtaskId, fields.timestamp)
  }

  private fun requiredProgressFields(input: GoalObservabilityProgressInput): RequiredProgressFields? {
    val artifacts = input.artifacts.asGoalWorkflowArtifactMap("goal observability progress input")
    val progressEvent = artifacts["progress_event"] as? Map<*, *>
    val continuation = artifacts["goal_continuation"] as? Map<*, *>
    val issueKey = continuation?.get(SharedPayloadKeys.ISSUE_KEY)?.toString()?.takeIf(String::isNotBlank)
    val subtaskId =
      continuation?.get(SharedPayloadKeys.SUBTASK_ID)?.let { value ->
        value.asExactIntOrNull()
          ?: throw InvalidGoalObservabilityEventSchemaError(
            "goal observability progress input",
            SharedPayloadKeys.SUBTASK_ID,
            "must be an integer.",
          )
      }
    val timestamp = progressEvent?.get("timestamp")?.toString()?.takeIf(String::isNotBlank)
    return when {
      progressEvent == null -> null
      issueKey == null -> null
      subtaskId == null -> null
      timestamp == null -> null
      else -> RequiredProgressFields(progressEvent, issueKey, subtaskId, timestamp)
    }
  }

  private fun eventFrom(
    input: GoalObservabilityProgressInput,
    progressEvent: Map<*, *>,
    issueKey: String,
    subtaskId: Int,
    timestamp: String,
  ): GoalObservabilityEvent {
    val kind = progressEvent["kind"]?.toString()?.takeIf(String::isNotBlank) ?: "durable_progress"
    return GoalObservabilityEvent(
      issueKey = issueKey,
      subtaskId = subtaskId,
      workflowId = input.workflowId,
      workflowPhase =
        progressEvent[SharedPayloadKeys.STEP_ID]?.toString()?.takeIf(String::isNotBlank)
          ?: input.currentStepId.takeIf(String::isNotBlank)
          ?: "unknown",
      workerRole = progressEvent["source"]?.toString()?.takeIf(String::isNotBlank) ?: "unknown",
      livenessClass = kind,
      activitySummary =
        progressEvent["message"]?.toString()?.takeIf(String::isNotBlank)
          ?: "workflow_status=${input.workflowStatus}; progress_kind=$kind",
      timestamp = timestamp,
      sequenceNumber =
        progressEvent["sequence"]?.let { value ->
          value.asExactIntOrNull()
            ?: throw InvalidGoalObservabilityEventSchemaError(
              "goal observability progress input",
              "sequence",
              "must be an integer.",
            )
        } ?: 0,
      changedFileSummary = input.worktreeActivity?.changedFileSummary,
      diffStat = input.worktreeActivity?.diffStat,
    )
  }
}
