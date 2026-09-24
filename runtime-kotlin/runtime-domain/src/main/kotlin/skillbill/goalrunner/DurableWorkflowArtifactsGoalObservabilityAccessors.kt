package skillbill.goalrunner

import skillbill.workflow.engine.model.DurableWorkflowArtifacts
import skillbill.workflow.goal.model.GoalObservabilityEvent
import skillbill.workflow.goal.model.GoalObservabilityHistory
import skillbill.workflow.goal.model.goalObservabilityHistoryFromArtifacts
import skillbill.workflow.goal.model.goalObservabilityLatestEventFromArtifacts

fun DurableWorkflowArtifacts.goalObservabilityLatestEvent(): GoalObservabilityEvent? =
  goalObservabilityLatestEventFromArtifacts(this)

fun DurableWorkflowArtifacts.goalObservabilityHistory(): GoalObservabilityHistory =
  goalObservabilityHistoryFromArtifacts(this)
