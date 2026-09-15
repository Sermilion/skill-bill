package skillbill.mcp.featuretask

import skillbill.contracts.SharedPayloadKeys
import skillbill.engine.featuretask.model.FeatureTaskPhaseSettlementAcknowledgment
import skillbill.engine.featuretask.model.FeatureTaskPhaseSettlementBlockRequest
import skillbill.engine.featuretask.model.FeatureTaskPhaseSettlementCompleteRequest
import skillbill.mcp.shared.McpRuntimeContext
import skillbill.mcp.shared.optionalInt
import skillbill.mcp.shared.optionalString
import skillbill.mcp.shared.services
import skillbill.mcp.shared.string

internal fun featureTaskPhaseComplete(arguments: Map<String, Any?>, context: McpRuntimeContext): Map<String, Any?> =
  services(context).featureTaskPhaseSettlementService.complete(
    FeatureTaskPhaseSettlementCompleteRequest(
      workflowId = arguments.string(SharedPayloadKeys.WORKFLOW_ID),
      phaseId = arguments.string(SharedPayloadKeys.PHASE_ID),
      attempt = requireNotNull(arguments.optionalInt("attempt")) { "attempt is required." },
      value = arguments.string(SharedPayloadKeys.VALUE),
      prompt = arguments.optionalString(SharedPayloadKeys.PROMPT),
      summary = arguments.optionalString(SharedPayloadKeys.SUMMARY),
    ),
  ).toWireMap()

internal fun featureTaskPhaseBlock(arguments: Map<String, Any?>, context: McpRuntimeContext): Map<String, Any?> =
  services(context).featureTaskPhaseSettlementService.block(
    FeatureTaskPhaseSettlementBlockRequest(
      workflowId = arguments.string(SharedPayloadKeys.WORKFLOW_ID),
      phaseId = arguments.string(SharedPayloadKeys.PHASE_ID),
      attempt = requireNotNull(arguments.optionalInt("attempt")) { "attempt is required." },
      reason = arguments.string("reason"),
      failureDisposition = arguments.optionalString(SharedPayloadKeys.FAILURE_DISPOSITION) ?: "needs_user_action",
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
