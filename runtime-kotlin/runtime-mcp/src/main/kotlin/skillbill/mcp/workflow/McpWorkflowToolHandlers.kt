package skillbill.mcp.workflow

import skillbill.application.workflow.model.WorkflowFamilyKind
import skillbill.application.workflow.model.WorkflowUpdateRequest
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.decomposition.DecompositionPlanningResult
import skillbill.contracts.workflow.WorkflowArtifactKeys
import skillbill.error.InvalidMcpToolArgumentError
import skillbill.contracts.mcp.McpToolPayloadKeys
import skillbill.mcp.shared.McpComponent
import skillbill.mcp.shared.componentForLegacyContext
import skillbill.mcp.shared.int
import skillbill.mcp.shared.optionalInt
import skillbill.mcp.shared.optionalListMap
import skillbill.mcp.shared.optionalMap
import skillbill.mcp.shared.optionalString
import skillbill.mcp.shared.string
import skillbill.workflow.engine.model.WorkflowArtifactPatch
import skillbill.workflow.engine.model.WorkflowStepUpdates

internal fun workflowOpen(
  kind: WorkflowFamilyKind,
  arguments: Map<String, Any?>,
  component: McpComponent,
): Map<String, Any?> = McpWorkflowRuntime.open(
  McpWorkflowOpenArgs(
    kind = kind,
    sessionId = arguments.string(McpToolPayloadKeys.SESSION_ID),
    currentStepId = arguments.optionalString(McpToolPayloadKeys.CURRENT_STEP_ID),
    issueKey = arguments.optionalString(McpToolPayloadKeys.ISSUE_KEY),
    repositoryIdentity = arguments.optionalString(McpToolPayloadKeys.REPOSITORY_IDENTITY),
    governedSpecPath = arguments.optionalString(McpToolPayloadKeys.GOVERNED_SPEC_PATH),
    component = component,
  ),
)

internal fun workflowUpdate(
  kind: WorkflowFamilyKind,
  arguments: Map<String, Any?>,
  component: McpComponent,
): Map<String, Any?> = McpWorkflowRuntime.update(
  kind = kind,
  request = arguments.optionalMap(McpToolPayloadKeys.ARTIFACTS_PATCH).let { artifactsPatch ->
    WorkflowUpdateRequest(
      workflowId = arguments.string(SharedPayloadKeys.WORKFLOW_ID),
      workflowStatus = arguments.string(McpToolPayloadKeys.WORKFLOW_STATUS),
      currentStepId = arguments.string(McpToolPayloadKeys.CURRENT_STEP_ID),
      stepUpdates = arguments.optionalListMap(McpToolPayloadKeys.STEP_UPDATES)?.let(WorkflowStepUpdates::from),
      artifactsPatch = artifactsPatch?.let(WorkflowArtifactPatch::from),
      planningResult = artifactsPatch?.get(WorkflowArtifactKeys.PLAN)
        ?.let(JsonCodec::anyToStringAnyMap)
        ?.let { DecompositionPlanningResult.fromWireMap(it, "mcp.artifacts_patch.plan") },
      sessionId = arguments.string(McpToolPayloadKeys.SESSION_ID),
    )
  },
  component = component,
)

internal fun workflowGet(
  kind: WorkflowFamilyKind,
  arguments: Map<String, Any?>,
  component: McpComponent,
): Map<String, Any?> = McpWorkflowRuntime.get(kind, arguments.string(SharedPayloadKeys.WORKFLOW_ID), component)

internal fun workflowList(
  kind: WorkflowFamilyKind,
  arguments: Map<String, Any?>,
  component: McpComponent,
): Map<String, Any?> = McpWorkflowRuntime.list(
  kind,
  limit = arguments.int("limit", default = 20),
  component = component,
)

internal fun workflowResume(
  kind: WorkflowFamilyKind,
  arguments: Map<String, Any?>,
  component: McpComponent,
): Map<String, Any?> = McpWorkflowRuntime.resume(kind, arguments.string(SharedPayloadKeys.WORKFLOW_ID), component)

internal fun workflowContinue(
  kind: WorkflowFamilyKind,
  arguments: Map<String, Any?>,
  context: Any,
): Map<String, Any?> = workflowContinue(kind, arguments, componentForLegacyContext(context))

internal fun workflowContinue(
  kind: WorkflowFamilyKind,
  arguments: Map<String, Any?>,
  component: McpComponent,
): Map<String, Any?> {
  val workflowIdOrIssueKey = arguments.optionalString(SharedPayloadKeys.WORKFLOW_ID)
    ?: arguments.optionalString(McpToolPayloadKeys.ISSUE_KEY)
    ?: throw InvalidMcpToolArgumentError(
      "feature_verify_workflow_continue",
      "workflow_id",
      "or issue_key is required",
    )
  return McpWorkflowRuntime.continueWorkflow(
    kind,
    workflowIdOrIssueKey,
    subtaskId = arguments.optionalInt(SharedPayloadKeys.SUBTASK_ID),
    component = component,
  )
}
