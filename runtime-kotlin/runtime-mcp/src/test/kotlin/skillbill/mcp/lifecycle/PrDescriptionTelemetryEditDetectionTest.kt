package skillbill.mcp.lifecycle

import skillbill.infrastructure.sqlite.ensureTestDatabase
import skillbill.mcp.shared.McpRuntimeContext
import skillbill.mcp.shared.callToolPayload
import skillbill.mcp.shared.decodeJsonObject
import skillbill.mcp.shared.enabledTelemetryEnvironment
import skillbill.mcp.shared.scalarString
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PrDescriptionTelemetryEditDetectionTest {
  @Test
  fun `pr description telemetry detects edited final body through mcp tool`() {
    val tempDir = Files.createTempDirectory("skillbill-mcp-pr-description-edited")
    val context = McpRuntimeContext(environment = enabledTelemetryEnvironment(tempDir), userHome = tempDir)

    context.callToolPayload(
      "pr_description_generated",
      mapOf(
        "commit_count" to 2,
        "files_changed_count" to 4,
        "was_edited_by_user" to false,
        "pr_created" to true,
        "pr_title" to "SKILL-109 reliable telemetry",
        "orchestrated" to false,
        "generated_description" to "## Summary\n\n- generated body\n",
        "final_pr_body" to "## Summary\n\n- edited body\n",
      ),
    )

    ensureTestDatabase(tempDir.resolve("metrics.db")).use { connection ->
      val payload =
        decodeJsonObject(
          scalarString(
            connection,
            "SELECT payload_json FROM telemetry_outbox WHERE event_name = 'skillbill_pr_description_generated'",
          ),
        )
      assertEquals(true, payload["was_edited_by_user"])
      assertEquals(true, payload["pr_created"])
    }
  }

  @Test
  fun `orchestrated pr description telemetry detects edited final body in returned child payload`() {
    val tempDir = Files.createTempDirectory("skillbill-mcp-pr-description-orchestrated-edited")
    val context = McpRuntimeContext(environment = enabledTelemetryEnvironment(tempDir), userHome = tempDir)

    val result =
      context.callToolPayload(
        "pr_description_generated",
        mapOf(
          "commit_count" to 2,
          "files_changed_count" to 4,
          "was_edited_by_user" to false,
          "pr_created" to true,
          "pr_title" to "SKILL-109 reliable telemetry",
          "orchestrated" to true,
          "generated_description" to "## Summary\r\n\r\n- generated body\r\n",
          "final_pr_body" to "## Summary\n\n- generated body with reviewer edit\n",
        ),
      )

    val payload = result["telemetry_payload"] as Map<*, *>
    assertEquals("orchestrated", result["mode"])
    assertEquals(true, payload["was_edited_by_user"])
    assertFalse(Files.exists(tempDir.resolve("metrics.db")))
  }
}
