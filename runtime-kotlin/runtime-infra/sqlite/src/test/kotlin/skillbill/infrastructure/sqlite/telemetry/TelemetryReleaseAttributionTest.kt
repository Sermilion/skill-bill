package skillbill.infrastructure.sqlite.telemetry

import skillbill.infrastructure.sqlite.ensureTestDatabase
import skillbill.infrastructure.sqlite.telemetryOutboxOnConnection
import java.nio.file.Files
import java.sql.Connection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val ENQUEUE_TIME_VERSION = "7.7.7-enqueue"
private const val UPLOAD_TIME_VERSION = "8.8.8-upload"

class TelemetryReleaseAttributionTest {
  @Test
  fun `an event is uploaded with the version that was running when it was enqueued`() {
    withOutboxDatabase { connection ->
      telemetryOutboxOnConnection(connection, ENQUEUE_TIME_VERSION)
        .enqueue(eventName = "skillbill_goal_finished", payloadJson = """{"name":"ok"}""")

      val uploadTimeStore = telemetryOutboxOnConnection(connection, UPLOAD_TIME_VERSION)
      val pending = uploadTimeStore.listPending()

      assertEquals(ENQUEUE_TIME_VERSION, pending.single().skillBillVersion)
    }
  }

  @Test
  fun `a pre-migration row carrying no version uploads without error`() {
    withOutboxDatabase { connection ->
      connection.createStatement().use { statement ->
        statement.executeUpdate(
          """
          INSERT INTO telemetry_outbox (event_name, payload_json)
          VALUES ('skillbill_goal_finished', '{"name":"legacy"}')
          """.trimIndent(),
        )
      }

      val pending = telemetryOutboxOnConnection(connection, UPLOAD_TIME_VERSION).listPending()
      assertEquals(1, pending.size, "The version-less row must stay pending, not be dropped by the reader.")

      val event = pending.single()
      assertEquals("skillbill_goal_finished", event.eventName)
      assertEquals(null, event.skillBillVersion)
      assertTrue(event.payloadJson.contains("name"))
    }
  }

  private fun withOutboxDatabase(block: (Connection) -> Unit) {
    val dbPath = Files.createTempDirectory("telemetry-release-attribution").resolve("metrics.db")
    ensureTestDatabase(dbPath).use(block)
  }
}
