package skillbill.cli.workflow

import skillbill.application.workflow.model.WorkflowContinueResult
import skillbill.contracts.SharedPayloadKeys

internal fun WorkflowContinueResult.toCliMap(): Map<String, Any?> =
  when (this) {
    is WorkflowContinueResult.Standard -> toStandardCliMap()
    is WorkflowContinueResult.UnknownWorkflow -> toUnknownWorkflowCliMap()
    is WorkflowContinueResult.Error -> toErrorCliMap()
    else -> throw IllegalStateException("Workflow continuation result is not supported by this CLI.")
  }

internal fun WorkflowContinueResult.Standard.toStandardCliMap(): Map<String, Any?> =
  standardContinueMap(view, dbPath, decompositionExtras = emptyMap())

internal fun WorkflowContinueResult.UnknownWorkflow.toUnknownWorkflowCliMap(): Map<String, Any?> =
  linkedMapOf(
    SharedPayloadKeys.STATUS to "error",
    SharedPayloadKeys.WORKFLOW_ID to workflowId,
    "error" to "Unknown workflow_id '$workflowId'.",
    "db_path" to dbPath,
  )

internal fun WorkflowContinueResult.Error.toErrorCliMap(): Map<String, Any?> =
  linkedMapOf(
    SharedPayloadKeys.STATUS to "error",
    SharedPayloadKeys.WORKFLOW_ID to workflowId,
    "error" to error,
    "db_path" to dbPath,
  )
