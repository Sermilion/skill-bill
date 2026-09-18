package skillbill.mcp.core

import skillbill.contracts.SharedPayloadKeys
import skillbill.workflow.model.WorkflowStepStatus

internal fun stringSchema(
  enum: List<String> = emptyList(),
  minLength: Int? = null,
  description: String? = null,
  pattern: String? = null,
): Map<String, Any?> = buildMap {
  put(McpProtocolFramer.SCHEMA_TYPE_KEY, "string")
  if (enum.isNotEmpty()) {
    put(McpProtocolFramer.SCHEMA_ENUM_KEY, enum)
  }
  minLength?.let { put(McpProtocolFramer.SCHEMA_MIN_LENGTH_KEY, it) }
  description?.let { put(McpProtocolFramer.DESCRIPTION_KEY, it) }
  pattern?.let { put(McpProtocolFramer.SCHEMA_PATTERN_KEY, it) }
}

internal fun arraySchema(items: Map<String, Any?>): Map<String, Any?> = mapOf(
  McpProtocolFramer.SCHEMA_TYPE_KEY to "array",
  McpProtocolFramer.SCHEMA_ITEMS_KEY to items,
)

internal fun stepUpdateSchema(stepIdEnum: List<String>): Map<String, Any?> = McpToolSpec.strictObjectSchema(
  required = listOf("step_id", "status", "attempt_count"),
  properties = mapOf(
    SharedPayloadKeys.STEP_ID to stringSchema(enum = stepIdEnum),
    SharedPayloadKeys.STATUS to stringSchema(
      enum = WorkflowStepStatus.entries.map(WorkflowStepStatus::wireValue),
    ),
    "attempt_count" to integerSchema,
  ),
)
