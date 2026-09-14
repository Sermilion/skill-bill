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
      workflowId = arguments.string("workflow_id"),
      phaseId = arguments.string("phase_id"),
      attempt = requireNotNull(arguments.optionalInt("attempt")) { "attempt is required." },
      value = arguments.string("value"),
      prompt = arguments.optionalString("prompt"),
      summary = arguments.optionalString("summary"),
    ),
  ).toWireMap()

internal fun featureTaskPhaseBlock(arguments: Map<String, Any?>, context: McpRuntimeContext): Map<String, Any?> =
  services(context).featureTaskPhaseSettlementService.block(
    FeatureTaskPhaseSettlementBlockRequest(
      workflowId = arguments.string("workflow_id"),
      phaseId = arguments.string("phase_id"),
      attempt = requireNotNull(arguments.optionalInt("attempt")) { "attempt is required." },
      reason = arguments.string("reason"),
      failureDisposition = arguments.optionalString("failure_disposition") ?: "needs_user_action",
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
