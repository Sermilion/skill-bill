package skillbill.cli.workflow

import skillbill.application.workflow.persist.WorkflowWireProjections
import skillbill.contracts.JsonCodec
import skillbill.workflow.engine.model.WorkflowSnapshotView
import skillbill.workflow.goal.GoalObservabilityEventValidator
import skillbill.workflow.goal.model.goalObservabilityLatestEventFromArtifacts

internal fun workflowSnapshotCliMap(
  snapshot: WorkflowSnapshotView,
  goalObservabilityEventValidator: GoalObservabilityEventValidator,
): LinkedHashMap<String, Any?> =
  LinkedHashMap(WorkflowWireProjections.snapshotMap(snapshot).toPayload()).apply {
    goalObservabilitySummaryFromArtifacts(snapshot.artifacts, goalObservabilityEventValidator)?.let { summary ->
      put("goal_observability", summary)
    }
  }

private fun goalObservabilitySummaryFromArtifacts(
  artifacts: Map<String, Any?>,
  goalObservabilityEventValidator: GoalObservabilityEventValidator,
): Map<String, Any?>? =
  goalObservabilityLatestEventFromArtifacts(artifacts, goalObservabilityEventValidator)
    ?.toCompactSummaryWire()
    ?.let(JsonCodec::anyToStringAnyMap)
