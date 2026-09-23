package skillbill.mcp.workflow

import skillbill.application.workflow.persist.WorkflowWireProjections
import skillbill.contracts.JsonCodec
import skillbill.goalrunner.model.GOAL_ATTEMPT_LEDGER_ARTIFACT_KEY
import skillbill.goalrunner.model.GoalPlanningStatusSnapshot
import skillbill.workflow.engine.model.WorkflowSnapshotView
import skillbill.workflow.goal.model.GOAL_PROGRESS_LATEST_EVENT_ARTIFACT_KEY
import skillbill.workflow.goal.model.GoalObservabilityEvent

internal fun workflowSnapshotMcpMap(
  snapshot: WorkflowSnapshotView,
  goalObservability: GoalObservabilityEvent?,
): LinkedHashMap<String, Any?> =
  LinkedHashMap(WorkflowWireProjections.snapshotMap(snapshot).toPayload()).apply {
    goalObservabilitySummary(goalObservability)?.let { summary ->
      put("goal_observability", summary)
    }
    (snapshot.artifacts[GOAL_PROGRESS_LATEST_EVENT_ARTIFACT_KEY] as? Map<*, *>)?.let { event ->
      put("goal_progress", event)
    }
    (snapshot.artifacts[GOAL_ATTEMPT_LEDGER_ARTIFACT_KEY] as? List<*>)?.lastOrNull()?.let { entry ->
      put("goal_attempt_ledger_latest", entry)
    }
  }

internal fun GoalPlanningStatusSnapshot.toMcpMap(): Map<String, Any?> =
  linkedMapOf(
    "state" to state.wireValue,
    "shared_preplan_prepared" to sharedPreplanPrepared,
    "planned_subtask_count" to plannedSubtaskCount,
    "total_subtask_count" to totalSubtaskCount,
    "current_planning_subtask" to currentPlanningSubtaskId,
    "planning_wave_subtasks" to planningWaveSubtaskIds,
    "reason" to reason,
  )

private fun goalObservabilitySummary(goalObservability: GoalObservabilityEvent?): Map<String, Any?>? =
  goalObservability
    ?.toCompactSummaryWire()
    ?.let(JsonCodec::anyToStringAnyMap)
