package skillbill.mcp

import skillbill.contracts.JsonCodec
import skillbill.error.InvalidTelemetryEventSchemaError
import skillbill.mcp.core.McpStdioServer
import skillbill.mcp.core.McpToolDispatcher
import skillbill.mcp.shared.McpRuntimeContext
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class McpStdioArgumentShapeUnifiedContractTest {

  @Test
  fun `strict-args unknown property surfaces as isError=true`() {
    val response =
      decodeStdioObject(
        McpStdioServer.handleLine(
          stdioToolCallRequest(
            id = 401,
            name = "resolve_learnings",
            arguments = mapOf("repo" to "skill-bill", "unexpected" to true),
          ),
        ),
      )

    assertNull(response["error"], "argument-shape failure must not use the JSON-RPC transport error envelope")
    val result = JsonCodec.anyToStringAnyMap(response["result"])
    assertNotNull(result, "argument-shape failure must surface as a JSON-RPC `result` envelope (not `error`)")
    assertEquals(true, result["isError"])
    val payload = decodeFirstTextContent(result)
    assertEquals("error", payload["status"])
    assertEquals("resolve_learnings", payload["tool"])
    assertContains(payload["error"].toString(), "unexpected")
  }

  @Test
  fun `dispatcher rejects the same unknown property through the canonical schema validator`() {
    val error = assertFailsWith<InvalidTelemetryEventSchemaError> {
      McpToolDispatcher.call(
        toolName = "resolve_learnings",
        arguments = mapOf("repo" to "skill-bill", "unexpected" to true),
        context = McpRuntimeContext(),
      )
    }

    assertContains(error.message.orEmpty(), "unexpected")
    val response = decodeStdioObject(
      McpStdioServer.handleLine(
        stdioToolCallRequest(
          id = 403,
          name = "resolve_learnings",
          arguments = mapOf("repo" to "skill-bill", "unexpected" to true),
        ),
      ),
    )
    val result = requireNotNull(JsonCodec.anyToStringAnyMap(response["result"]))
    assertEquals(true, result["isError"])
    val payload = decodeFirstTextContent(result)
    assertEquals(error.message, payload["error"])
  }

  @Test
  fun `schema validator missing required field surfaces as isError=true`() {
    val response =
      decodeStdioObject(
        McpStdioServer.handleLine(
          stdioToolCallRequest(
            id = 402,
            name = "feature_verify_started",
            arguments = emptyMap(),
          ),
        ),
      )

    assertNull(response["error"], "argument-shape failure must not use the JSON-RPC transport error envelope")
    val result = JsonCodec.anyToStringAnyMap(response["result"])
    assertNotNull(result, "argument-shape failure must surface as a JSON-RPC `result` envelope (not `error`)")
    assertEquals(true, result["isError"])
    val payload = decodeFirstTextContent(result)
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

  private fun stdioToolCallRequest(id: Int, name: String, arguments: Map<String, Any?>): String =
    JsonCodec.mapToJsonString(
      mapOf(
        "jsonrpc" to "2.0",
        "id" to id,
        "method" to "tools/call",
        "params" to mapOf("name" to name, "arguments" to arguments),
      ),
    )

  private fun decodeStdioObject(rawJson: String?): Map<String, Any?> {
    requireNotNull(rawJson) { "expected JSON-RPC response but got null" }
    val parsed = JsonCodec.parseObjectOrNull(rawJson)
    require(parsed != null) { "expected JSON object but got: $rawJson" }
    val decoded = JsonCodec.anyToStringAnyMap(JsonCodec.jsonElementToValue(parsed))
    require(decoded != null) { "expected decoded JSON object but got: $rawJson" }
    return decoded
  }

  private fun decodeFirstTextContent(result: Map<String, Any?>): Map<String, Any?> {
    val content = result["content"] as List<*>
    val first = requireNotNull(JsonCodec.anyToStringAnyMap(content.first()))
    val text = first["text"].toString()
    val parsed = JsonCodec.parseObjectOrNull(text)
    require(parsed != null) { "expected content[0].text to be a JSON object but got: $text" }
    return requireNotNull(JsonCodec.anyToStringAnyMap(JsonCodec.jsonElementToValue(parsed)))
  }
}
