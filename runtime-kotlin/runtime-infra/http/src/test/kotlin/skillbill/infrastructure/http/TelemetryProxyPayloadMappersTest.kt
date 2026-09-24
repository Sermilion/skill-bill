package skillbill.infrastructure.http

import skillbill.contracts.telemetry.TelemetryProxyPayloadKeys
import skillbill.ports.repository.toFileLocation
import skillbill.ports.telemetry.model.TelemetryOutboxRecord
import skillbill.telemetry.model.TelemetrySettings
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelemetryProxyPayloadMappersTest {
  @Test
  fun `a recorded version is injected next to install_id`() {
    val payload = telemetryProxyBatchPayload(settings(), listOf(row(id = 1, version = "1.2.3")))

    val properties = payload.batch.single().properties
    assertEquals("1.2.3", properties["skill_bill_version"])
    assertEquals("test-install-id", properties["install_id"])
  }

  @Test
  fun `rows with no recorded version still produce well-formed events`() {
    val rows =
      listOf(
        row(id = 1, version = null),
        row(id = 2, version = ""),
        row(id = 3, version = "1.2.3"),
      )

    val payload = telemetryProxyBatchPayload(settings(), rows)

    assertEquals(rows.size, payload.batch.size, "No row may be dropped for lacking a version.")
    payload.batch.take(2).forEach { event ->
      assertEquals("skillbill_goal_finished", event.event)
      assertEquals("test-install-id", event.properties["install_id"])
      assertFalse("skill_bill_version" in event.properties, "An absent version must omit the property.")
    }
    assertTrue("skill_bill_version" in payload.batch.last().properties)
  }

  @Test
  fun `the row identity rides as the top-level event uuid the receiver deduplicates on`() {
    val rows =
      listOf(
        row(id = 1, version = "1.2.3", eventUuid = "11111111-1111-4111-8111-111111111111"),
        row(id = 2, version = "1.2.3", eventUuid = "22222222-2222-4222-8222-222222222222"),
      )

    val wireEvents = telemetryProxyBatchPayload(settings(), rows).batch.map { it.toPayload() }

    assertEquals(
      listOf("11111111-1111-4111-8111-111111111111", "22222222-2222-4222-8222-222222222222"),
      wireEvents.map { it[TelemetryProxyPayloadKeys.EVENT_IDENTITY] },
    )
    assertEquals(
      rows.map { it.createdAt }.distinct().size,
      1,
      "Identical timestamps are the case the identity has to separate.",
    )
  }

  @Test
  fun `a row without a recorded identity sends no event uuid rather than minting one`() {
    val wireEvent = telemetryProxyBatchPayload(settings(), listOf(row(id = 1, version = "1.2.3"))).batch.single()

    assertFalse(TelemetryProxyPayloadKeys.EVENT_IDENTITY in wireEvent.toPayload())
    assertFalse(TelemetryProxyPayloadKeys.EVENT_DEDUPLICATION_ID in wireEvent.properties)
  }

  private fun row(
    id: Long,
    version: String?,
    eventUuid: String = "",
  ): TelemetryOutboxRecord =
    TelemetryOutboxRecord(
      id = id,
      eventName = "skillbill_goal_finished",
      payloadJson = """{"name":"ok"}""",
      createdAt = "2026-04-23 00:00:00",
      syncedAt = null,
      lastError = "",
      skillBillVersion = version,
      eventUuid = eventUuid,
    )

  private fun settings(): TelemetrySettings =
    TelemetrySettings(
      configPath = Files.createTempFile("telemetry-mapper", ".json").toFileLocation(),
      level = "anonymous",
      enabled = true,
      installId = "test-install-id",
      proxyUrl = "https://telemetry.example.dev/ingest",
      customProxyUrl = "https://telemetry.example.dev/ingest",
      batchSize = 50,
    )
}
