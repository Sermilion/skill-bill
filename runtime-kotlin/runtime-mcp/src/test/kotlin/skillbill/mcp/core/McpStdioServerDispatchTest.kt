package skillbill.mcp.core

import skillbill.contracts.JsonCodec
import skillbill.di.core.SkillBillVersion
import skillbill.mcp.shared.McpRuntimeContext
import skillbill.mcp.shared.SAMPLE_REVIEW
import skillbill.mcp.shared.callTool
import skillbill.mcp.shared.callToolPayload
import skillbill.mcp.shared.decodeToolArguments
import skillbill.mcp.shared.descriptionFor
import skillbill.mcp.shared.enabledTelemetryEnvironment
import skillbill.mcp.shared.removedToolNames
import skillbill.mcp.shared.seedGoalBlockedRun
import skillbill.mcp.shared.toolCallRequest
import skillbill.mcp.shared.toolNamedOrNull
import skillbill.mcp.shared.toolPayload
import skillbill.mcp.shared.toolsList
import skillbill.mcp.shared.verifyLifecycleToolNames
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class McpStdioServerDispatchTest {
  @Test
  fun `canonical leaves carry no deprecation language and legacy families are absent from the registry`() {
    val tools = toolsList()

    verifyLifecycleToolNames.forEach { verify ->
      assertFalse(tools.descriptionFor(verify).contains("Deprecated"), verify)
      assertFalse(tools.descriptionFor(verify).contains("EXPERIMENTAL"), verify)
    }

    assertNull(tools.toolNamedOrNull("feature_task_started"))
    assertNull(tools.toolNamedOrNull("feature_task_workflow_update"))
  }

  @Test
  fun `SKILL-175 the advertised surface carries no prose family name`() {
    val advertised = toolsList().map { tool -> requireNotNull(JsonCodec.anyToStringAnyMap(tool))["name"].toString() }

    val retired =
      advertised.filter { name ->
        name.startsWith("feature_task_prose_") ||
          name.startsWith("feature_implement_") ||
          name.startsWith("goal_prose_")
      }
    assertEquals(emptyList(), retired, advertised.toString())
  }

  @Test
  fun `SKILL-132 removed tools are absent from discovery`() {
    val tools = toolsList()

    removedToolNames.forEach { removed ->
      assertNull(tools.toolNamedOrNull(removed), removed)
    }
  }

  @Test
  fun `SKILL-132 removed tools report the typed unknown-tool error on dispatch`() {
    val context = McpRuntimeContext()

    removedToolNames.forEach { removed ->
      val result = context.callTool(removed)

      assertEquals(true, result["isError"], removed)
      assertContains(
        toolPayload(result)["error"].toString(),
        "MCP tool '$removed' argument 'tool': unknown tool",
      )
    }
  }

  @Test
  fun `tools call wraps native payloads as text content`() {
    val result = McpRuntimeContext().callTool("doctor")
    val content = result["content"] as List<*>
    val textContent = requireNotNull(JsonCodec.anyToStringAnyMap(content.first()))

    assertEquals(false, result["isError"])
    assertEquals("text", textContent["type"])
    assertEquals(SkillBillVersion.VALUE, toolPayload(result)["version"])
  }

  @Test
  fun `tools call triage accepts individual numbered decisions`() {
    val tempDir = Files.createTempDirectory("skillbill-stdio-triage")
    val context = McpRuntimeContext(environment = enabledTelemetryEnvironment(tempDir), userHome = tempDir)

    context.callToolPayload("import_review", mapOf("review_text" to SAMPLE_REVIEW.trimIndent()))

    val triageArguments =
      mapOf(
        "review_run_id" to "rvw-20260402-001",
        "decisions" to listOf("1 fix", "2 reject"),
      )
    val decodedTriageArguments = decodeToolArguments(toolCallRequest(2, "triage_findings", triageArguments))
    assertEquals(
      listOf("1 fix", "2 reject"),
      decodedTriageArguments["decisions"],
      decodedTriageArguments.toString(),
    )

    val payload = context.callToolPayload("triage_findings", triageArguments)
    val recorded = payload["recorded"] as List<*>

    assertEquals(2, recorded.size)
    assertEquals("fix_applied", requireNotNull(JsonCodec.anyToStringAnyMap(recorded[0]))["outcome_type"])
    assertEquals("fix_rejected", requireNotNull(JsonCodec.anyToStringAnyMap(recorded[1]))["outcome_type"])
  }

  @Test
  fun `goal_stats dispatch returns populated payload for a seeded store`() {
    val tempDir = Files.createTempDirectory("skillbill-stdio-goal-stats-seeded")
    val context = McpRuntimeContext(environment = enabledTelemetryEnvironment(tempDir), userHome = tempDir)
    seedGoalBlockedRun(tempDir.resolve("metrics.db"), workflowId = "wf-stdio-1")

    val payload = context.callToolPayload("goal_stats")

    assertEquals("bill-goal-run", payload["workflow"])
    assertEquals(1, payload["total_runs"])
    assertEquals(1, payload["blocked_runs"])
    val topBlocked = payload["top_blocked_subtasks"] as List<*>
    assertEquals(1, topBlocked.size)
    val blockedEntry = requireNotNull(JsonCodec.anyToStringAnyMap(topBlocked.first()))
    assertEquals("test failure", blockedEntry["blocked_reason"])
  }

  @Test
  fun `goal_stats dispatch returns zero-count payload for empty store`() {
    val tempDir = Files.createTempDirectory("skillbill-stdio-goal-stats-empty")
    val context = McpRuntimeContext(environment = enabledTelemetryEnvironment(tempDir), userHome = tempDir)

    val payload = context.callToolPayload("goal_stats")

    assertEquals("bill-goal-run", payload["workflow"])
    assertEquals(0, payload["total_runs"])
    assertEquals(null, payload["most_recent_run"])
    val topBlocked = payload["top_blocked_subtasks"] as List<*>
    assertTrue(topBlocked.isEmpty())
  }
}
