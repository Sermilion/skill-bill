package skillbill.mcp.featuretask

import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.mcp.McpToolPayloadKeys
import skillbill.engine.featuretask.model.phase.FeatureTaskPhaseSettlementAcknowledgment
import skillbill.engine.featuretask.model.phase.FeatureTaskPhaseSettlementBlockRequest
import skillbill.engine.featuretask.model.phase.FeatureTaskPhaseSettlementCompleteRequest
import skillbill.error.InvalidMcpToolArgumentError
import skillbill.mcp.shared.McpComponent
import skillbill.mcp.shared.optionalInt
import skillbill.mcp.shared.optionalString
import skillbill.mcp.shared.string
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeFailureDisposition

internal fun featureTaskPhaseComplete(arguments: Map<String, Any?>, component: McpComponent): Map<String, Any?> =
  component.featureTaskPhaseSettlementService.complete(
    FeatureTaskPhaseSettlementCompleteRequest(
      workflowId = arguments.string(SharedPayloadKeys.WORKFLOW_ID),
      phaseId = arguments.string(SharedPayloadKeys.PHASE_ID),
      attempt = arguments.optionalInt(McpToolPayloadKeys.ATTEMPT)
        ?: throw InvalidMcpToolArgumentError("feature_task_phase_complete", "attempt", "is required"),
      value = arguments.string(SharedPayloadKeys.VALUE),
      prompt = arguments.optionalString(SharedPayloadKeys.PROMPT),
      summary = arguments.optionalString(SharedPayloadKeys.SUMMARY),
    ),
  ).toWireMap()

internal fun featureTaskPhaseBlock(arguments: Map<String, Any?>, component: McpComponent): Map<String, Any?> =
  component.featureTaskPhaseSettlementService.block(
    FeatureTaskPhaseSettlementBlockRequest(
      workflowId = arguments.string(SharedPayloadKeys.WORKFLOW_ID),
      phaseId = arguments.string(SharedPayloadKeys.PHASE_ID),
      attempt = arguments.optionalInt(McpToolPayloadKeys.ATTEMPT)
        ?: throw InvalidMcpToolArgumentError("feature_task_phase_block", "attempt", "is required"),
      reason = arguments.string(McpToolPayloadKeys.REASON),
      failureDisposition = arguments.optionalString(SharedPayloadKeys.FAILURE_DISPOSITION)
        ?: FeatureTaskRuntimeFailureDisposition.NEEDS_USER_ACTION.wireValue,
    ),
  ).toWireMap()

private fun FeatureTaskPhaseSettlementAcknowledgment.toWireMap(): Map<String, Any?> = linkedMapOf(
  SharedPayloadKeys.STATUS to status,
  SharedPayloadKeys.WORKFLOW_ID to workflowId,
  SharedPayloadKeys.PHASE_ID to phaseId,
  "attempt" to attempt,
  "kind" to kind.wireValue,
  "envelope" to envelope,
)
