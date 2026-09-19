package skillbill.cli

import skillbill.cli.kernel.cli.CliOutput
import skillbill.cli.model.CliFormat
import skillbill.cli.telemetry.toCliMap
import skillbill.telemetry.model.TelemetryOpenDocument
import skillbill.telemetry.model.TelemetryProxyCapabilities
import skillbill.telemetry.model.TelemetryRemoteStatsResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
class TelemetryCliResultMappersTest {
  @Test
  fun `remote stats mapper preserves explicit null capabilities`() {
    val result =
      TelemetryRemoteStatsResult(
        workflow = "bill-feature-verify",
        dateFrom = "2026-04-01",
        dateTo = "2026-04-22",
        source = "remote_proxy",
        statsUrl = "https://telemetry.example.dev/ingest/stats",
        groupBy = null,
        capabilities =
        TelemetryProxyCapabilities(
          contractVersion = "1",
          source = "remote_proxy",
          proxyUrl = "https://telemetry.example.dev/ingest",
          capabilitiesUrl = "https://telemetry.example.dev/ingest/capabilities",
          supportsIngest = true,
          supportsStats = true,
          supportedWorkflows = listOf("bill-feature-verify"),
        ),
        metrics = TelemetryOpenDocument.from(linkedMapOf("status" to "ok", "capabilities" to null)),
      )

    val payload = result.toCliMap()

    assertTrue("capabilities" in payload)
    assertEquals(null, payload["capabilities"])
  }

  @Test
  fun `remote stats CLI JSON preserves wire output bytes`() {
    val result =
      TelemetryRemoteStatsResult(
        workflow = "bill-feature-verify",
        dateFrom = "2026-04-01",
        dateTo = "2026-04-22",
        source = "remote_proxy",
        statsUrl = "https://telemetry.example.dev/ingest/stats",
        groupBy = null,
        capabilities =
        TelemetryProxyCapabilities(
          contractVersion = "1",
          source = "remote_proxy",
          proxyUrl = "https://telemetry.example.dev/ingest",
          capabilitiesUrl = "https://telemetry.example.dev/ingest/capabilities",
          supportsIngest = true,
          supportsStats = true,
          supportedWorkflows = listOf("bill-feature-verify"),
        ),
        metrics = TelemetryOpenDocument.from(linkedMapOf("status" to "ok")),
      )

    assertEquals(
      """
      {
        "capabilities": {
          "capabilities_url": "https://telemetry.example.dev/ingest/capabilities",
          "contract_version": "1",
          "proxy_url": "https://telemetry.example.dev/ingest",
          "source": "remote_proxy",
          "supported_workflows": [
            "bill-feature-verify"
          ],
          "supports_event_deduplication": true,
          "supports_ingest": true,
          "supports_stats": true
        },
        "date_from": "2026-04-01",
        "date_to": "2026-04-22",
        "source": "remote_proxy",
        "stats_url": "https://telemetry.example.dev/ingest/stats",
        "status": "ok",
        "workflow": "bill-feature-verify"
      }
      """.trimIndent(),
      CliOutput.emit(result.toCliMap(), CliFormat.JSON),
    )
  }
}
