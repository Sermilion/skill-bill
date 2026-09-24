package skillbill.goalrunner

import skillbill.contracts.JsonCodec
import skillbill.error.shellcontent.InvalidGoalProgressEventSchemaError
import skillbill.workflow.engine.model.DurableWorkflowArtifacts
import skillbill.workflow.model.goalreview.GOAL_PROGRESS_RUN_HISTORY_ARTIFACT_KEY
import skillbill.workflow.model.goalreview.GoalProgressEvent

internal fun DurableWorkflowArtifacts.goalProgressLatestEvent(): GoalProgressEvent? = declaredProgressEventFrom(this)

internal fun DurableWorkflowArtifacts.goalProgressHistory(): List<GoalProgressEvent> {
  if (!containsKey(GOAL_PROGRESS_RUN_HISTORY_ARTIFACT_KEY)) return emptyList()
  val raw =
    JsonCodec.anyToStringAnyMapList(this[GOAL_PROGRESS_RUN_HISTORY_ARTIFACT_KEY])
      ?: throw InvalidGoalProgressEventSchemaError(
        GOAL_PROGRESS_RUN_HISTORY_ARTIFACT_KEY,
        "<root>",
        "must be an array.",
      )
  return raw.mapIndexed { index, event ->
    event.decodeDeclaredGoalProgressEvent("$GOAL_PROGRESS_RUN_HISTORY_ARTIFACT_KEY[$index]")
  }
}
