package skillbill.cli.workflow

import skillbill.application.workflow.model.GoalContinuationOutcome
import skillbill.application.workflow.model.WorkflowContinueResult
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.decomposition.DecompositionManifestPayloadKeys
import skillbill.workflow.model.WorkflowContinueStatus

private typealias MissingSubtaskWorkflow = WorkflowContinueResult.DecompositionMissingSubtaskWorkflow
private typealias BlockedSubtask = WorkflowContinueResult.DecompositionBlockedSubtask
private typealias BlockedBranchStart = WorkflowContinueResult.DecompositionBlockedBranchStart
private typealias SubtaskOutcome = WorkflowContinueResult.DecompositionSubtaskOutcome

internal fun WorkflowContinueResult.DecompositionStandard.toDecompositionStandardCliMap(): Map<String, Any?> =
  standardContinueMap(
    view = view,
    dbPath = dbPath,
    decompositionExtras =
      linkedMapOf(
        SharedPayloadKeys.ISSUE_KEY to (outcome?.issueKey ?: issueKey),
        "decomposition_subtask_id" to decompositionSubtaskId,
        "decomposition_subtask_spec_path" to decompositionSubtaskSpecPath,
        "goal_continuation_outcome" to outcome.toWireMap(),
      ),
  )

internal fun MissingSubtaskWorkflow.toDecompositionMissingSubtaskWorkflowCliMap(): Map<String, Any?> =
  linkedMapOf(
    SharedPayloadKeys.STATUS to "error",
    "continue_status" to WorkflowContinueStatus.BLOCKED.wireValue,
    SharedPayloadKeys.SUBTASK_ID to subtaskId,
    DecompositionManifestPayloadKeys.BLOCKED_REASON to blockedReason,
    "db_path" to dbPath,
  )

internal fun BlockedSubtask.toDecompositionBlockedSubtaskCliMap(): Map<String, Any?> =
  linkedMapOf(
    SharedPayloadKeys.STATUS to "error",
    "continue_status" to WorkflowContinueStatus.BLOCKED.wireValue,
    SharedPayloadKeys.WORKFLOW_ID to workflowId,
    SharedPayloadKeys.ISSUE_KEY to issueKey,
    "decomposition_subtask_id" to subtaskId,
    "decomposition_subtask_spec_path" to subtaskSpecPath,
    DecompositionManifestPayloadKeys.BLOCKED_REASON to blockedReason,
    "error" to blockedReason,
    "db_path" to dbPath,
  )

internal fun BlockedBranchStart.toDecompositionBlockedBranchStartCliMap(): Map<String, Any?> =
  linkedMapOf(
    SharedPayloadKeys.STATUS to "error",
    "continue_status" to WorkflowContinueStatus.BLOCKED.wireValue,
    SharedPayloadKeys.WORKFLOW_ID to workflowId,
    SharedPayloadKeys.ISSUE_KEY to issueKey,
    "error" to blockedReason,
    "db_path" to dbPath,
  )

internal fun WorkflowContinueResult.DecompositionDone.toDecompositionDoneCliMap(): Map<String, Any?> =
  linkedMapOf(
    SharedPayloadKeys.STATUS to "ok",
    "continue_status" to WorkflowContinueStatus.DONE.wireValue,
    SharedPayloadKeys.WORKFLOW_ID to workflowId,
    SharedPayloadKeys.ISSUE_KEY to issueKey,
    "decomposition_status" to decompositionStatus,
    "db_path" to dbPath,
  )

internal fun SubtaskOutcome.toDecompositionSubtaskOutcomeCliMap(): Map<String, Any?> =
  linkedMapOf(
    SharedPayloadKeys.STATUS to "ok",
    "continue_status" to WorkflowContinueStatus.DONE.wireValue,
    SharedPayloadKeys.WORKFLOW_ID to workflowId,
    SharedPayloadKeys.ISSUE_KEY to issueKey,
    "decomposition_subtask_id" to subtaskId,
    "decomposition_subtask_spec_path" to subtaskSpecPath,
    "goal_continuation_outcome" to outcome.toWireMap(),
    "db_path" to dbPath,
  )

internal fun WorkflowContinueResult.DecompositionBlockedGit.toDecompositionBlockedGitCliMap(): Map<String, Any?> =
  linkedMapOf(
    SharedPayloadKeys.STATUS to "error",
    "continue_status" to WorkflowContinueStatus.BLOCKED.wireValue,
    SharedPayloadKeys.WORKFLOW_ID to workflowId,
    SharedPayloadKeys.ISSUE_KEY to issueKey,
    DecompositionManifestPayloadKeys.BLOCKED_REASON to blockedReason,
    "error" to blockedReason,
    "db_path" to dbPath,
  )

internal fun GoalContinuationOutcome?.toWireMap(): Map<String, Any?> =
  this?.let { outcome ->
    linkedMapOf(
      SharedPayloadKeys.ISSUE_KEY to outcome.issueKey,
      SharedPayloadKeys.SUBTASK_ID to outcome.subtaskId,
      SharedPayloadKeys.STATUS to outcome.status,
      DecompositionManifestPayloadKeys.COMMIT_SHA to outcome.commitSha,
      SharedPayloadKeys.WORKFLOW_ID to outcome.workflowId,
      DecompositionManifestPayloadKeys.BLOCKED_REASON to outcome.blockedReason,
      DecompositionManifestPayloadKeys.LAST_RESUMABLE_STEP to outcome.lastResumableStep,
    )
  }.orEmpty()
