package skillbill.mcp.workflow

import skillbill.application.workflow.model.WorkflowContinueResult
import skillbill.application.workflow.model.WorkflowGetResult
import skillbill.application.workflow.model.WorkflowLatestResult
import skillbill.application.workflow.model.WorkflowListResult
import skillbill.application.workflow.model.WorkflowOpenResult
import skillbill.application.workflow.model.WorkflowResumeResult
import skillbill.application.workflow.model.WorkflowUpdateResult
import skillbill.application.workflow.persist.WorkflowWireProjections
import skillbill.contracts.SharedPayloadKeys
import skillbill.workflow.goal.GoalObservabilityEventValidator
internal fun WorkflowContinueResult.toMcpMap(): Map<String, Any?> = when (this) {
  is WorkflowContinueResult.Standard -> toStandardMcpMap()
  is WorkflowContinueResult.DecompositionStandard -> toDecompositionStandardMcpMap()
  is WorkflowContinueResult.UnknownWorkflow -> toUnknownWorkflowMcpMap()
  is WorkflowContinueResult.DecompositionMissingSubtaskWorkflow -> toDecompositionMissingSubtaskWorkflowMcpMap()
  is WorkflowContinueResult.DecompositionBlockedSubtask -> toDecompositionBlockedSubtaskMcpMap()
  is WorkflowContinueResult.DecompositionBlockedBranchStart -> toDecompositionBlockedBranchStartMcpMap()
  is WorkflowContinueResult.DecompositionDone -> toDecompositionDoneMcpMap()
  is WorkflowContinueResult.DecompositionSubtaskOutcome -> toDecompositionSubtaskOutcomeMcpMap()
  is WorkflowContinueResult.DecompositionBlockedGit -> toDecompositionBlockedGitMcpMap()
  is WorkflowContinueResult.Error -> toErrorMcpMap()
}

internal fun WorkflowOpenResult.toMcpMap(
  goalObservabilityEventValidator: GoalObservabilityEventValidator,
): Map<String, Any?> = when (this) {
  is WorkflowOpenResult.Ok -> workflowSnapshotMcpMap(snapshot, goalObservabilityEventValidator).apply {
    launchProjection?.let {
      put("launch_projection", WorkflowWireProjections.inputProjectionMap(it).toPayload())
    }
    put(SharedPayloadKeys.STATUS, "ok")
    put("db_path", dbPath)
  }
  is WorkflowOpenResult.Error -> linkedMapOf(
    SharedPayloadKeys.STATUS to "error",
    SharedPayloadKeys.WORKFLOW_ID to workflowId,
    "error" to error,
  )
}

internal fun WorkflowUpdateResult.toMcpMap(): Map<String, Any?> = when (this) {
  is WorkflowUpdateResult.Ok -> LinkedHashMap(
    WorkflowWireProjections.updateAcknowledgementMap(acknowledgement).toPayload(),
  ).apply {
    launchProjection?.let { put("launch_projection", WorkflowWireProjections.inputProjectionMap(it).toPayload()) }
    put(
      "read_only_full_state_command",
      readOnlyFullStateCommand(
        dbPath,
        acknowledgement.workflowId,
        acknowledgement.workflowName,
      ),
    )
    put("db_path", dbPath)
  }
  is WorkflowUpdateResult.Error -> linkedMapOf<String, Any?>(
    SharedPayloadKeys.STATUS to "error",
    SharedPayloadKeys.WORKFLOW_ID to workflowId,
    "error" to error,
  ).apply { dbPath?.let { put("db_path", it) } }
}

internal fun WorkflowGetResult.toMcpMap(
  goalObservabilityEventValidator: GoalObservabilityEventValidator,
): Map<String, Any?> = when (this) {
  is WorkflowGetResult.Ok -> workflowSnapshotMcpMap(snapshot, goalObservabilityEventValidator).apply {
    put(SharedPayloadKeys.STATUS, "ok")
    put("db_path", dbPath)
  }
  is WorkflowGetResult.Error -> linkedMapOf(
    SharedPayloadKeys.STATUS to "error",
    SharedPayloadKeys.WORKFLOW_ID to workflowId,
    "error" to error,
    "db_path" to dbPath,
  )
}

internal fun WorkflowListResult.toMcpMap(): Map<String, Any?> = linkedMapOf(
  SharedPayloadKeys.STATUS to "ok",
  "db_path" to dbPath,
  "workflow_count" to workflowCount,
  "workflows" to workflows.map { WorkflowWireProjections.summaryMap(it).toPayload() },
)

internal fun WorkflowLatestResult.toMcpMap(): Map<String, Any?> = when (this) {
  is WorkflowLatestResult.Ok -> LinkedHashMap(WorkflowWireProjections.summaryMap(summary).toPayload()).apply {
    put(SharedPayloadKeys.STATUS, "ok")
    put("db_path", dbPath)
  }
  is WorkflowLatestResult.Error -> linkedMapOf(
    SharedPayloadKeys.STATUS to "error",
    "error" to error,
    "db_path" to dbPath,
  )
}

internal fun WorkflowResumeResult.toMcpMap(): Map<String, Any?> = when (this) {
  is WorkflowResumeResult.Ok -> LinkedHashMap(WorkflowWireProjections.resumeMap(resume).toPayload()).apply {
    put(SharedPayloadKeys.STATUS, "ok")
    put("db_path", dbPath)
  }
  is WorkflowResumeResult.Error -> linkedMapOf(
    SharedPayloadKeys.STATUS to "error",
    SharedPayloadKeys.WORKFLOW_ID to workflowId,
    "error" to error,
    "db_path" to dbPath,
  )
}

internal fun readOnlyFullStateCommand(dbPath: String, workflowId: String, skillName: String): String {
  val workflowCommand = if (skillName == "bill-feature-verify") "verify-workflow" else "workflow"
  val quotedDbPath = "'${dbPath.replace("'", "'\"'\"'")}'"
  val quotedWorkflowId = "'${workflowId.replace("'", "'\"'\"'")}'"
  return "skill-bill --db $quotedDbPath $workflowCommand show $quotedWorkflowId --format json"
}
