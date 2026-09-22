package skillbill.cli.workflow

import skillbill.application.workflow.model.WorkflowGetResult
import skillbill.application.workflow.model.WorkflowLatestResult
import skillbill.application.workflow.model.WorkflowListResult
import skillbill.application.workflow.model.WorkflowOpenResult
import skillbill.application.workflow.model.WorkflowResumeResult
import skillbill.application.workflow.persist.WorkflowWireProjections
import skillbill.contracts.SharedPayloadKeys
import skillbill.workflow.goal.GoalObservabilityEventValidator

internal fun WorkflowOpenResult.toCliMap(
  goalObservabilityEventValidator: GoalObservabilityEventValidator,
): Map<String, Any?> =
  when (this) {
    is WorkflowOpenResult.Ok ->
      workflowSnapshotCliMap(snapshot, goalObservabilityEventValidator).apply {
        launchProjection?.let { put("launch_projection", WorkflowWireProjections.inputProjectionMap(it).toPayload()) }
        put(SharedPayloadKeys.STATUS, "ok")
        put("db_path", dbPath)
      }
    is WorkflowOpenResult.Error ->
      linkedMapOf(
        SharedPayloadKeys.STATUS to "error",
        SharedPayloadKeys.WORKFLOW_ID to workflowId,
        "error" to error,
      )
  }

internal fun WorkflowGetResult.toCliMap(
  goalObservabilityEventValidator: GoalObservabilityEventValidator,
): Map<String, Any?> =
  when (this) {
    is WorkflowGetResult.Ok ->
      workflowSnapshotCliMap(snapshot, goalObservabilityEventValidator).apply {
        put(SharedPayloadKeys.STATUS, "ok")
        put("db_path", dbPath)
      }
    is WorkflowGetResult.Error ->
      linkedMapOf(
        SharedPayloadKeys.STATUS to "error",
        SharedPayloadKeys.WORKFLOW_ID to workflowId,
        "error" to error,
        "db_path" to dbPath,
      )
  }

internal fun WorkflowListResult.toCliMap(): Map<String, Any?> =
  linkedMapOf(
    SharedPayloadKeys.STATUS to "ok",
    "db_path" to dbPath,
    "workflow_count" to workflowCount,
    "workflows" to workflows.map { WorkflowWireProjections.summaryMap(it).toPayload() },
  )

internal fun WorkflowLatestResult.toCliMap(): Map<String, Any?> =
  when (this) {
    is WorkflowLatestResult.Ok ->
      LinkedHashMap(WorkflowWireProjections.summaryMap(summary).toPayload()).apply {
        put(SharedPayloadKeys.STATUS, "ok")
        put("db_path", dbPath)
      }
    is WorkflowLatestResult.Error ->
      linkedMapOf(
        SharedPayloadKeys.STATUS to "error",
        "error" to error,
        "db_path" to dbPath,
      )
  }

internal fun WorkflowResumeResult.toCliMap(): Map<String, Any?> =
  when (this) {
    is WorkflowResumeResult.Ok ->
      LinkedHashMap(WorkflowWireProjections.resumeMap(resume).toPayload()).apply {
        put(SharedPayloadKeys.STATUS, "ok")
        put("db_path", dbPath)
      }
    is WorkflowResumeResult.Error ->
      linkedMapOf(
        SharedPayloadKeys.STATUS to "error",
        SharedPayloadKeys.WORKFLOW_ID to workflowId,
        "error" to error,
        "db_path" to dbPath,
      )
  }
