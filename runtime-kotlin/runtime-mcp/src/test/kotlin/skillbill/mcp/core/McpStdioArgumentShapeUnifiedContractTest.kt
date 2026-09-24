package skillbill.mcp.core

import skillbill.contracts.JsonCodec
import skillbill.mcp.shared.McpRuntimeContext
import skillbill.mcp.shared.decodeResponse
import skillbill.mcp.shared.toolCallRequest
import skillbill.mcp.shared.toolPayload
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class McpStdioArgumentShapeUnifiedContractTest {
  private val context = McpRuntimeContext()

  @Test
  fun `strict-args unknown property surfaces as isError=true`() {
    val response =
      handleToolCall(
        name = "resolve_learnings",
        arguments = mapOf("repo" to "skill-bill", "unexpected" to true),
      )

    assertNull(response["error"], "argument-shape failure must not use the JSON-RPC transport error envelope")
    val result = JsonCodec.anyToStringAnyMap(response["result"])
    assertNotNull(result, "argument-shape failure must surface as a JSON-RPC `result` envelope (not `error`)")
    assertEquals(true, result["isError"])
    val payload = toolPayload(result)
    assertEquals("error", payload["status"])
    assertEquals("resolve_learnings", payload["tool"])
    assertContains(payload["error"].toString(), "unexpected")
  }

  @Test
  fun `schema validator missing required field surfaces as isError=true`() {
    val response = handleToolCall(name = "feature_verify_started", arguments = emptyMap())

    assertNull(response["error"], "argument-shape failure must not use the JSON-RPC transport error envelope")
    val result = JsonCodec.anyToStringAnyMap(response["result"])
    assertNotNull(result, "argument-shape failure must surface as a JSON-RPC `result` envelope (not `error`)")
    assertEquals(true, result["isError"])
    val payload = toolPayload(result)
    assertEquals("error", payload["status"])
    assertEquals("feature_verify_started", payload["tool"])

    val lowerError = payload["error"].toString().lowercase()
    assertContains(lowerError, "feature_verify_started")
    assertTrue(
      lowerError.contains("acceptance_criteria_count") ||
        lowerError.contains("required") ||
        lowerError.contains("spec_summary"),
      "schema validator error should name a required field or the `required` keyword — got '$lowerError'",
    )
  }

  private fun handleToolCall(
    name: String,
    arguments: Map<String, Any?>,
  ): Map<String, Any?> =
    decodeResponse(
      McpStdioServer.handleLine(toolCallRequest(401, name, arguments), context.mcpComponent()),
    )
}
