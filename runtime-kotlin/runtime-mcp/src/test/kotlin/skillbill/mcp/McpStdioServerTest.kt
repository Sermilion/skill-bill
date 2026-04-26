package skillbill.mcp

import skillbill.SAMPLE_REVIEW
import skillbill.contracts.JsonSupport
import skillbill.telemetry.CONFIG_ENVIRONMENT_KEY
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        "readian_auth_status",
        "readian_get_article",
        "readian_get_articles_for_topic_query",
        "readian_get_spotlight",
        "readian_mark_story_status",
        "readian_save_candidate",
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
  fun `feature implement started tool advertises required assessment arguments`() {
    val schema = toolSchema("feature_implement_started")
    val properties = requireNotNull(JsonSupport.anyToStringAnyMap(schema["properties"]))

    assertEquals(
      listOf(
        "feature_size",
        "acceptance_criteria_count",
        "open_questions_count",
        "spec_input_types",
        "spec_word_count",
        "rollout_needed",
      ),
      schema["required"],
    )
    assertEquals(
      listOf("SMALL", "MEDIUM", "LARGE"),
      requireNotNull(JsonSupport.anyToStringAnyMap(properties["feature_size"]))["enum"],
    )
  }

  @Test
  fun `feature implement workflow update schema requires attempt counts in every step update`() {
    val schema = toolSchema("feature_implement_workflow_update")
    val properties = requireNotNull(JsonSupport.anyToStringAnyMap(schema["properties"]))
    val workflowStatus = requireNotNull(JsonSupport.anyToStringAnyMap(properties["workflow_status"]))
    val currentStep = requireNotNull(JsonSupport.anyToStringAnyMap(properties["current_step_id"]))
    val stepUpdates = requireNotNull(JsonSupport.anyToStringAnyMap(properties["step_updates"]))
    val stepItem = requireNotNull(JsonSupport.anyToStringAnyMap(stepUpdates["items"]))
    val stepProperties = requireNotNull(JsonSupport.anyToStringAnyMap(stepItem["properties"]))
    val stepId = requireNotNull(JsonSupport.anyToStringAnyMap(stepProperties["step_id"]))
    val attemptCount = requireNotNull(JsonSupport.anyToStringAnyMap(stepProperties["attempt_count"]))

    assertEquals(listOf("workflow_id", "workflow_status", "current_step_id"), schema["required"])
    assertEquals(listOf("step_id", "status", "attempt_count"), stepItem["required"])
    assertTrue((workflowStatus["enum"] as List<*>).contains("blocked"))
    assertTrue((currentStep["enum"] as List<*>).contains("create_branch"))
    assertTrue((stepId["enum"] as List<*>).contains("commit_push"))
    assertEquals("integer", attemptCount["type"])
  }

  @Test
  fun `feature verify workflow update schema requires attempt counts in every step update`() {
    val schema = toolSchema("feature_verify_workflow_update")
    val properties = requireNotNull(JsonSupport.anyToStringAnyMap(schema["properties"]))
    val workflowStatus = requireNotNull(JsonSupport.anyToStringAnyMap(properties["workflow_status"]))
    val currentStep = requireNotNull(JsonSupport.anyToStringAnyMap(properties["current_step_id"]))
    val stepUpdates = requireNotNull(JsonSupport.anyToStringAnyMap(properties["step_updates"]))
    val stepItem = requireNotNull(JsonSupport.anyToStringAnyMap(stepUpdates["items"]))
    val stepProperties = requireNotNull(JsonSupport.anyToStringAnyMap(stepItem["properties"]))
    val stepStatus = requireNotNull(JsonSupport.anyToStringAnyMap(stepProperties["status"]))
    val attemptCount = requireNotNull(JsonSupport.anyToStringAnyMap(stepProperties["attempt_count"]))

    assertEquals(listOf("workflow_id", "workflow_status", "current_step_id"), schema["required"])
    assertEquals(listOf("step_id", "status", "attempt_count"), stepItem["required"])
    assertTrue((workflowStatus["enum"] as List<*>).contains("abandoned"))
    assertTrue((currentStep["enum"] as List<*>).contains("verdict"))
    assertTrue((stepStatus["enum"] as List<*>).contains("skipped"))
    assertEquals("integer", attemptCount["type"])
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

  @Test
  fun `tools call triage accepts individual numbered decisions`() {
    val tempDir = Files.createTempDirectory("skillbill-stdio-triage")
    val context = McpRuntimeContext(environment = enabledStdioTelemetryEnvironment(tempDir), userHome = tempDir)

    val importResponse =
      decodeResponse(
        McpStdioServer.handleLine(
          toolCallRequest(
            id = 1,
            name = "import_review",
            arguments = mapOf("review_text" to SAMPLE_REVIEW.trimIndent()),
          ),
          context,
        ),
      )
    assertEquals(false, importResponse.map("result")["isError"])

    val triageRequest =
      toolCallRequest(
        id = 2,
        name = "triage_findings",
        arguments = mapOf(
          "review_run_id" to "rvw-20260402-001",
          "decisions" to listOf("1 fix", "2 reject"),
        ),
      )
    val decodedTriageArguments = decodeToolArguments(triageRequest)
    assertEquals(
      listOf("1 fix", "2 reject"),
      decodedTriageArguments.stringList("decisions"),
      decodedTriageArguments.toString(),
    )
    val triageResponse =
      decodeResponse(
        McpStdioServer.handleLine(
          triageRequest,
          context,
        ),
      )
    val result = triageResponse.map("result")
    val payload = toolPayload(result)

    assertEquals(false, result["isError"], payload.toString())
    val recorded = payload["recorded"] as List<*>
    assertEquals(2, recorded.size)
    assertEquals("fix_applied", requireNotNull(JsonSupport.anyToStringAnyMap(recorded[0]))["outcome_type"])
    assertEquals("fix_rejected", requireNotNull(JsonSupport.anyToStringAnyMap(recorded[1]))["outcome_type"])
  }

  @Test
  fun `readian spotlight tool reports unavailable when standalone boundary cannot launch`() {
    val tempDir = Files.createTempDirectory("skillbill-readian-auth-required")
    val context =
      McpRuntimeContext(
        environment = mapOf("SKILL_BILL_READIAN_MCP_COMMAND" to tempDir.resolve("missing-readian-mcp").toString()),
        userHome = tempDir,
      )

    val response =
      decodeResponse(
        McpStdioServer.handleLine(
          toolCallRequest(
            id = 1,
            name = "readian_get_spotlight",
            arguments = mapOf("beat" to "pc-games"),
          ),
          context,
        ),
      )
    val result = response.map("result")
    val payload = toolPayload(result)

    assertEquals(false, result["isError"], payload.toString())
    assertEquals("error", payload["status"])
    assertEquals("readian_mcp_unavailable", payload["error_type"])
  }

  @Test
  fun `readian topic query bridges skill bill arguments to standalone mcp query arguments`() {
    val tempDir = Files.createTempDirectory("skillbill-readian-bridge")
    val capturedInput = tempDir.resolve("captured-stdin.jsonl")
    val bridgeResponse =
      """{"jsonrpc":"2.0","id":2,"result":{"isError":false,"structuredContent":""" +
        """{"status":"ok","tool":"readian_get_articles_for_topic_query","query":"pc gaming"}}}"""
    val script = fakeReadianMcpScript(
      tempDir,
      bridgeResponse,
    )
    val context =
      McpRuntimeContext(
        environment = mapOf(
          "SKILL_BILL_READIAN_MCP_COMMAND" to script.toString(),
          "CAPTURE_FILE" to capturedInput.toString(),
        ),
        userHome = tempDir,
      )

    val response =
      decodeResponse(
        McpStdioServer.handleLine(
          toolCallRequest(
            id = 2,
            name = "readian_get_articles_for_topic_query",
            arguments = mapOf(
              "topic_query" to "pc gaming",
              "date" to "2026-04-26",
              "subscribed_only" to true,
            ),
          ),
          context,
        ),
      )
    val payload = toolPayload(response.map("result"))
    val captured = Files.readString(capturedInput)

    assertEquals("ok", payload["status"])
    assertEquals("pc gaming", payload["query"])
    assertTrue(captured.contains(""""query":"pc gaming""""))
    assertFalse(captured.contains(""""topic_query":"""))
  }

  @Test
  fun `readian topic query tool exposes authenticated topic arguments`() {
    val tempDir = Files.createTempDirectory("skillbill-readian-topic-query")
    val context =
      McpRuntimeContext(
        environment = mapOf("SKILL_BILL_READIAN_AUTHENTICATED" to "true"),
        userHome = tempDir,
      )

    val response =
      decodeResponse(
        McpStdioServer.handleLine(
          toolCallRequest(
            id = 2,
            name = "readian_get_articles_for_topic_query",
            arguments = mapOf(
              "topic_query" to "pc gaming",
              "date" to "2026-04-26",
              "subscribed_only" to true,
            ),
          ),
          context,
        ),
      )
    val payload = toolPayload(response.map("result"))
    val data = requireNotNull(JsonSupport.anyToStringAnyMap(payload["data"]))

    assertEquals("ok", payload["status"])
    assertEquals("readian_get_articles_for_topic_query", payload["tool"])
    assertEquals("topic_query", data["feed_source"])
    assertEquals("pc gaming", data["topic_query"])
    assertEquals(true, data["subscribed_only"])
  }

  @Test
  fun `readian tool responses redact token and session material from log safe payloads`() {
    val tempDir = Files.createTempDirectory("skillbill-readian-redaction")
    val context =
      McpRuntimeContext(
        environment = mapOf("SKILL_BILL_READIAN_AUTHENTICATED" to "true"),
        userHome = tempDir,
      )

    val response =
      decodeResponse(
        McpStdioServer.handleLine(
          toolCallRequest(
            id = 2,
            name = "readian_save_candidate",
            arguments = mapOf(
              "candidate_id" to "candidate-1",
              "refresh_token" to "readian_rt_supersecret",
              "notes" to "authorization=readian_token_should_not_leak Bearer abc.def.ghi",
              "nested" to mapOf("session_cookie" to "readian_session_supersecret"),
            ),
          ),
          context,
        ),
      )
    val payload = toolPayload(response.map("result"))
    val serialized = JsonSupport.mapToJsonString(payload)

    assertEquals("ok", payload["status"])
    assertFalse(serialized.contains("readian_rt_supersecret"), serialized)
    assertFalse(serialized.contains("readian_token_should_not_leak"), serialized)
    assertFalse(serialized.contains("readian_session_supersecret"), serialized)
    assertFalse(serialized.contains("abc.def.ghi"), serialized)
    assertTrue(serialized.contains("[REDACTED]"), serialized)
  }
}

private fun enabledStdioTelemetryEnvironment(tempDir: Path): Map<String, String> {
  val configPath = tempDir.resolve("config.json")
  Files.writeString(
    configPath,
    """
    {
      "install_id": "test-install-id",
      "telemetry": {
        "level": "anonymous",
        "proxy_url": "",
        "batch_size": 50
      }
    }
    """.trimIndent() + "\n",
  )
  return mapOf(
    "SKILL_BILL_REVIEW_DB" to tempDir.resolve("metrics.db").toString(),
    CONFIG_ENVIRONMENT_KEY to configPath.toString(),
  )
}

private fun toolCallRequest(id: Int, name: String, arguments: Map<String, Any?>): String = JsonSupport.mapToJsonString(
  mapOf(
    "jsonrpc" to "2.0",
    "id" to id,
    "method" to "tools/call",
    "params" to mapOf(
      "name" to name,
      "arguments" to arguments,
    ),
  ),
)

private fun fakeReadianMcpScript(directory: Path, response: String): Path {
  val script = directory.resolve("fake-readian-mcp")
  Files.writeString(
    script,
    """
    #!/bin/sh
    : > "${'$'}CAPTURE_FILE"
    count=0
    while [ "${'$'}count" -lt 3 ] && IFS= read -r line; do
      printf '%s\n' "${'$'}line" >> "${'$'}CAPTURE_FILE"
      count="${'$'}((count + 1))"
    done
    printf '%s\n' '$response'
    """.trimIndent() + "\n",
  )
  script.toFile().setExecutable(true)
  return script
}

private fun toolPayload(result: Map<String, Any?>): Map<String, Any?> {
  val content = result["content"] as List<*>
  val textContent = requireNotNull(JsonSupport.anyToStringAnyMap(content.first()))
  return decodeStdioJsonObject(textContent["text"].toString())
}

private fun toolSchema(name: String): Map<String, Any?> {
  val response =
    decodeResponse(
      McpStdioServer.handleLine(
        """{"jsonrpc":"2.0","id":"tools","method":"tools/list","params":{}}""",
      ),
    )
  val tools = response.map("result")["tools"] as List<*>
  val tool =
    tools
      .map { entry -> requireNotNull(JsonSupport.anyToStringAnyMap(entry)) }
      .first { entry -> entry["name"] == name }
  return requireNotNull(JsonSupport.anyToStringAnyMap(tool["inputSchema"]))
}

private fun decodeToolArguments(rawJson: String): Map<String, Any?> {
  val request = decodeStdioJsonObject(rawJson)
  val params = requireNotNull(JsonSupport.anyToStringAnyMap(request["params"]))
  return requireNotNull(JsonSupport.anyToStringAnyMap(params["arguments"]))
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
