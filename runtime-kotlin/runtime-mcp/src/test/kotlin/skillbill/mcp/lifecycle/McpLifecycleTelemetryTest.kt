package skillbill.mcp.lifecycle

import skillbill.infrastructure.sqlite.ensureTestDatabase
import skillbill.mcp.shared.McpRuntimeContext
import skillbill.mcp.shared.callToolPayload
import skillbill.mcp.shared.enabledTelemetryEnvironment
import skillbill.mcp.shared.scalarInt
import skillbill.mcp.shared.scalarString
import java.nio.file.Files
import java.sql.Connection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class McpLifecycleTelemetryTest {
  @Test
  fun `lifecycle telemetry tools persist natively and emit outbox events`() {
    val tempDir = Files.createTempDirectory("skillbill-mcp-lifecycle")
    val dbPath = tempDir.resolve("metrics.db")
    val context = McpRuntimeContext(environment = enabledTelemetryEnvironment(tempDir), userHome = tempDir)

    recordQualityCheckLifecycle(context)
    recordFeatureVerifyLifecycle(context)
    recordPrDescriptionLifecycle(context)

    ensureTestDatabase(dbPath).use { connection ->
      assertEquals(
        mapOf(
          "skillbill_quality_check_started" to 1,
          "skillbill_quality_check_finished" to 1,
          "skillbill_feature_verify_started" to 1,
          "skillbill_feature_verify_finished" to 1,
          "skillbill_pr_description_generated" to 1,
          "skillbill_runtime_exception" to 5,
        ),
        outboxEventCounts(connection),
      )
      assertEquals(1, scalarInt(connection, "SELECT COUNT(*) FROM quality_check_sessions"))
      assertEquals(
        "bill-code-check",
        scalarString(connection, "SELECT routed_skill FROM quality_check_sessions LIMIT 1"),
      )
      assertEquals(1, scalarInt(connection, "SELECT COUNT(*) FROM feature_verify_sessions"))
    }
  }

  @Test
  fun `orchestrated lifecycle telemetry returns child payloads without outbox events`() {
    val tempDir = Files.createTempDirectory("skillbill-mcp-lifecycle-orchestrated")
    val context = McpRuntimeContext(environment = enabledTelemetryEnvironment(tempDir), userHome = tempDir)

    val started =
      context.callToolPayload(
        "quality_check_started",
        mapOf(
          "routed_skill" to "bill-kotlin-code-check",
          "detected_stack" to "kotlin",
          "fallback" to false,
          "scope_type" to "repo",
          "initial_failure_count" to 0,
          "orchestrated" to true,
        ),
      )
    val finished =
      context.callToolPayload(
        "quality_check_finished",
        mapOf(
          "session_id" to "",
          "final_failure_count" to 0,
          "iterations" to 1,
          "result" to "pass",
          "failing_check_names" to emptyList<String>(),
          "unsupported_reason" to "",
          "orchestrated" to true,
          "routed_skill" to "bill-kotlin-code-check",
          "detected_stack" to "kotlin",
          "fallback" to false,
          "scope_type" to "repo",
          "initial_failure_count" to 0,
          "duration_seconds" to 5,
        ),
      )
    val payload = finished["telemetry_payload"] as Map<*, *>

    assertEquals("skipped_in_orchestrated_mode", started["status"])
    assertEquals("orchestrated", finished["mode"])
    assertEquals("bill-code-check", payload["skill"])
    assertFalse("session_id" in payload)
    assertFalse(Files.exists(tempDir.resolve("metrics.db")))
  }
}

private fun outboxEventCounts(connection: Connection): Map<String, Int> =
  connection.createStatement().use { statement ->
    statement.executeQuery(
      """
      SELECT event_name, COUNT(*) AS count
      FROM telemetry_outbox
      GROUP BY event_name
      ORDER BY event_name
      """.trimIndent(),
    ).use { resultSet ->
      buildMap {
        while (resultSet.next()) {
          put(resultSet.getString("event_name"), resultSet.getInt("count"))
        }
      }
    }
  }
