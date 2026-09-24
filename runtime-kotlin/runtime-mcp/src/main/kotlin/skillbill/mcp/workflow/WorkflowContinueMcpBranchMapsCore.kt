package skillbill.mcp.workflow

import skillbill.application.workflow.model.WorkflowContinueResult
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.mcp.McpToolPayloadKeys
import skillbill.contracts.workflow.workflow.WorkflowWirePayloadKeys

internal fun WorkflowContinueResult.Standard.toStandardMcpMap(): Map<String, Any?> =
  standardMcpContinueMap(view, dbPath)

internal fun WorkflowContinueResult.UnknownWorkflow.toUnknownWorkflowMcpMap(): Map<String, Any?> =
  linkedMapOf(
    SharedPayloadKeys.STATUS to "error",
    SharedPayloadKeys.WORKFLOW_ID to workflowId,
    McpToolPayloadKeys.ERROR to "Unknown workflow_id '$workflowId'.",
    WorkflowWirePayloadKeys.DB_PATH to dbPath,
  )

internal fun WorkflowContinueResult.Error.toErrorMcpMap(): Map<String, Any?> =
  linkedMapOf(
    SharedPayloadKeys.STATUS to "error",
    SharedPayloadKeys.WORKFLOW_ID to workflowId,
    McpToolPayloadKeys.ERROR to error,
    WorkflowWirePayloadKeys.DB_PATH to dbPath,
  )
