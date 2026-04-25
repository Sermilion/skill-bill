package skillbill.mcp

import skillbill.contracts.JsonSupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class McpStdioServerTest {
  @Test
  fun `initialize returns MCP server capabilities`() {
    val rawResponse =
      McpStdioServer.handleLine(
        """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""",
      )
    val response =
      decodeResponse(
        rawResponse,
      )
    val result = response.map("result")

    assertTrue(requireNotNull(rawResponse).contains(""""jsonrpc":"2.0""""))
    assertEquals(1, response["id"])
    assertEquals("2025-11-25", result["protocolVersion"])
    assertEquals("skill-bill", result.map("serverInfo")["name"])
    assertTrue(result.map("capabilities").containsKey("tools"))
  }

  @Test
  fun `tools list exposes the Python-compatible inventory`() {
    val response =
      decodeResponse(
        McpStdioServer.handleLine(
          """{"jsonrpc":"2.0","id":"tools","method":"tools/list","params":{}}""",
        ),
      )
    val tools = response.map("result")["tools"] as List<*>
    val names = tools.map { tool -> requireNotNull(JsonSupport.anyToStringAnyMap(tool))["name"] }

    assertEquals(
      listOf(
        "doctor",
        "feature_implement_finished",
        "feature_implement_stats",
        "feature_implement_started",
        "feature_implement_workflow_get",
        "feature_implement_workflow_latest",
        "feature_implement_workflow_list",
        "feature_implement_workflow_continue",
        "feature_implement_workflow_open",
        "feature_implement_workflow_resume",
        "feature_implement_workflow_update",
        "feature_verify_finished",
        "feature_verify_stats",
        "feature_verify_started",
        "feature_verify_workflow_get",
        "feature_verify_workflow_latest",
        "feature_verify_workflow_list",
        "feature_verify_workflow_continue",
        "feature_verify_workflow_open",
        "feature_verify_workflow_resume",
        "feature_verify_workflow_update",
        "import_review",
        "new_skill_scaffold",
        "pr_description_generated",
        "quality_check_finished",
        "quality_check_started",
        "resolve_learnings",
        "review_stats",
        "telemetry_proxy_capabilities",
        "telemetry_remote_stats",
        "triage_findings",
      ),
      names,
    )
  }

  @Test
  fun `tools call wraps native payloads as text content`() {
    val response =
      decodeResponse(
        McpStdioServer.handleLine(
          """{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"doctor","arguments":{}}}""",
        ),
      )
    val result = response.map("result")
    val content = result["content"] as List<*>
    val textContent = requireNotNull(JsonSupport.anyToStringAnyMap(content.first()))
    val payload = decodeStdioJsonObject(textContent["text"].toString())

    assertEquals(false, result["isError"])
    assertEquals("text", textContent["type"])
    assertEquals("0.1.0", payload["version"])
  }
}

private fun decodeResponse(rawJson: String?): Map<String, Any?> {
  assertNotNull(rawJson)
  return decodeStdioJsonObject(rawJson)
}

private fun decodeStdioJsonObject(rawJson: String): Map<String, Any?> {
  val parsed = JsonSupport.parseObjectOrNull(rawJson)
  require(parsed != null) { "Expected JSON object but got: $rawJson" }
  val decoded = JsonSupport.anyToStringAnyMap(JsonSupport.jsonElementToValue(parsed))
  require(decoded != null) { "Expected decoded JSON object but got: $rawJson" }
  return decoded
}
