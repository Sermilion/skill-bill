package skillbill.goalrunner
import skillbill.contracts.SharedPayloadKeys
import skillbill.error.InvalidGoalProgressEventSchemaError
import skillbill.goalrunner.model.GoalRunnerProgressEvent
import skillbill.workflow.goal.model.GOAL_PROGRESS_LATEST_EVENT_ARTIFACT_KEY
import skillbill.workflow.goal.model.GoalProgressEvent
import skillbill.workflow.goal.model.GoalProgressEventKind
import skillbill.workflow.goal.model.GoalProgressOutcome
import skillbill.workflow.goal.model.asGoalWorkflowArtifactMap
import skillbill.workflow.taskruntime.model.DurableArtifactMapReader
import skillbill.workflow.taskruntime.model.toStringKeyedArtifactMap

fun progressEventFrom(artifacts: Any): GoalRunnerProgressEvent? {
  val wire = artifacts.asGoalWorkflowArtifactMap("goal progress event artifacts")
  return (wire["progress_event"] as? Map<*, *>)
    ?.toGoalRunnerProgressEventOrNull()
}

fun declaredProgressEventFrom(artifacts: Any): GoalProgressEvent? {
  val wire = artifacts.asGoalWorkflowArtifactMap("goal declared progress event artifacts")
  return when (val raw = wire[GOAL_PROGRESS_LATEST_EVENT_ARTIFACT_KEY]) {
    null -> null
    is Map<*, *> -> raw.decodeDeclaredGoalProgressEvent(GOAL_PROGRESS_LATEST_EVENT_ARTIFACT_KEY)
    else -> throw InvalidGoalProgressEventSchemaError(
      GOAL_PROGRESS_LATEST_EVENT_ARTIFACT_KEY,
      "<root>",
      "must be an object.",
    )
  }
}

fun Map<*, *>.decodeDeclaredGoalProgressEvent(sourceLabel: String): GoalProgressEvent {
  val reader = DurableArtifactMapReader(
    toStringKeyedArtifactMap {
      invalidDeclaredGoalProgressEvent(sourceLabel, "<root>", it)
    },
  ) { detail ->
    invalidDeclaredGoalProgressEvent(sourceLabel, "<root>", detail)
  }
  val eventKind = requiredProgressEventKind(reader, sourceLabel)
  val workflowId = reader.requiredString("workflow_id")
  val workflowPhase = reader.requiredString("workflow_phase")
  val timestamp = reader.requiredString("timestamp")
  val sequenceNumber = reader.requiredInt("sequence_number").also { value ->
    if (value < 0) {
      invalidDeclaredGoalProgressEvent(sourceLabel, "sequence_number", "must be non-negative.")
    }
  }
  val outcome = optionalProgressOutcome(reader, sourceLabel)
  return buildDeclaredGoalProgressEvent(
    sourceLabel = sourceLabel,
    eventKind = eventKind,
    workflowId = workflowId,
    workflowPhase = workflowPhase,
    sequenceNumber = sequenceNumber,
    timestamp = timestamp,
    outcome = outcome,
    reader = reader,
  )
}

private fun invalidDeclaredGoalProgressEvent(sourceLabel: String, field: String, detail: String): Nothing =
  throw InvalidGoalProgressEventSchemaError(sourceLabel, field, detail)

private fun requiredProgressEventKind(reader: DurableArtifactMapReader, sourceLabel: String): GoalProgressEventKind {
  val wire = reader.requiredString("event_kind")
  return GoalProgressEventKind.entries.firstOrNull { it.wireValue == wire }
    ?: throw InvalidGoalProgressEventSchemaError(sourceLabel, "event_kind", "unrecognized value '$wire'.")
}

private fun optionalProgressOutcome(reader: DurableArtifactMapReader, sourceLabel: String): GoalProgressOutcome {
  val outcomeWire = reader.optionalString("outcome") ?: return GoalProgressOutcome.NONE
  return GoalProgressOutcome.entries.firstOrNull { it.wireValue == outcomeWire }
    ?: throw InvalidGoalProgressEventSchemaError(sourceLabel, "outcome", "unrecognized value '$outcomeWire'.")
}

private fun Map<*, *>.buildDeclaredGoalProgressEvent(
  sourceLabel: String,
  eventKind: GoalProgressEventKind,
  workflowId: String,
  workflowPhase: String,
  sequenceNumber: Int,
  timestamp: String,
  outcome: GoalProgressOutcome,
  reader: DurableArtifactMapReader,
): GoalProgressEvent = try {
  GoalProgressEvent(
    eventKind = eventKind,
    workflowId = workflowId,
    workflowPhase = workflowPhase,
    processAlive = reader.optionalBoolean("process_alive") ?: false,
    sequenceNumber = sequenceNumber,
    timestamp = timestamp,
    stepId = reader.optionalString(SharedPayloadKeys.STEP_ID),
    operationName = reader.optionalString("operation_name"),
    operationKind = reader.optionalString("operation_kind"),
    expectedLong = reader.optionalBoolean("expected_long") ?: false,
    outcome = outcome,
  )
} catch (error: IllegalArgumentException) {
  throw InvalidGoalProgressEventSchemaError(sourceLabel, "<root>", error.message ?: "invalid event.", error)
}
