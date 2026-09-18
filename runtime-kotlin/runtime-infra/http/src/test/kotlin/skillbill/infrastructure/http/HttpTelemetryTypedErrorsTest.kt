package skillbill.infrastructure.http

import skillbill.error.InvalidTelemetryTransportOutcomeError
import skillbill.error.SkillBillRuntimeException
import skillbill.error.TelemetryProxyInvalidResponseError
import skillbill.error.TelemetryProxyRequestFailureError
import skillbill.error.TelemetryRelayUrlUnconfiguredError
import skillbill.model.EnvironmentContext
import skillbill.ports.repository.toFileLocation
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.ports.telemetry.RemoteTransportPort
import skillbill.ports.telemetry.model.RemoteTransportResponse
import skillbill.telemetry.model.RemoteStatsRequest
import skillbill.telemetry.model.TelemetrySettings
import java.nio.file.Files
import java.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HttpTelemetryTypedErrorsTest {
  @Test
  fun `capabilities non-2xx outside 404 and 405 raises typed request failure`() {
    val requester = RemoteTransportPort { _, _, _, _ -> RemoteTransportResponse(statusCode = 500, body = "upstream") }
    val settings = settingsWithProxy("https://telemetry.example.dev/ingest")

    val error =
      assertFailsWith<TelemetryProxyRequestFailureError> {
        client(requester).fetchProxyCapabilities(settings)
      }

    assertTrue(SkillBillRuntimeException::class.java.isInstance(error))
    assertEquals(500, error.statusCode)
  }

  @Test
  fun `capabilities invalid JSON raises typed invalid response`() {
    val requester = RemoteTransportPort { _, _, _, _ -> RemoteTransportResponse(statusCode = 200, body = "not-json") }
    val settings = settingsWithProxy("https://telemetry.example.dev/ingest")

    val error =
      assertFailsWith<TelemetryProxyInvalidResponseError> {
        client(requester).fetchProxyCapabilities(settings)
      }

    assertTrue(SkillBillRuntimeException::class.java.isInstance(error))
    assertTrue(error.detail.contains("invalid JSON"))
  }

  @Test
  fun `capabilities non-object JSON raises typed invalid response`() {
    val requester = RemoteTransportPort { _, _, _, _ -> RemoteTransportResponse(statusCode = 200, body = "[]") }
    val settings = settingsWithProxy("https://telemetry.example.dev/ingest")

    val error =
      assertFailsWith<TelemetryProxyInvalidResponseError> {
        client(requester).fetchProxyCapabilities(settings)
      }

    assertTrue(SkillBillRuntimeException::class.java.isInstance(error))
    assertTrue(error.detail.contains("non-object"))
  }

  @Test
  fun `capabilities blank 2xx body raises typed invalid response`() {
    val requester = RemoteTransportPort { _, _, _, _ -> RemoteTransportResponse(statusCode = 200, body = "") }
    val settings = settingsWithProxy("https://telemetry.example.dev/ingest")

    val error =
      assertFailsWith<TelemetryProxyInvalidResponseError> {
        client(requester).fetchProxyCapabilities(settings)
      }

    assertTrue(SkillBillRuntimeException::class.java.isInstance(error))
    assertEquals("empty response body", error.detail)
  }

  @Test
  fun `blank relay URL raises typed unconfigured error`() {
    val settings = settingsWithProxy("")

    val error =
      assertFailsWith<TelemetryRelayUrlUnconfiguredError> {
    client(RemoteTransportPort { _, _, _, _ -> RemoteTransportResponse(200, "{}") })
          .fetchProxyCapabilities(settings)
      }

    assertTrue(SkillBillRuntimeException::class.java.isInstance(error))
  }

  @Test
  fun `remote stats non-2xx raises typed request failure`() {
    val requester =
      RemoteTransportPort { _, url, _, _ ->
        if (url.endsWith("/capabilities")) {
          RemoteTransportResponse(statusCode = 200, body = capabilitiesBody())
        } else {
          RemoteTransportResponse(statusCode = 503, body = "unavailable")
        }
      }
    val settings = settingsWithProxy("https://telemetry.example.dev/ingest")

    val error =
      assertFailsWith<TelemetryProxyRequestFailureError> {
        client(requester).fetchRemoteStats(
          settings = settings,
          request =
          RemoteStatsRequest(
            workflow = "bill-feature-verify",
            dateFrom = "2026-04-01",
            dateTo = "2026-04-22",
          ),
        )
      }

    assertTrue(SkillBillRuntimeException::class.java.isInstance(error))
    assertEquals(503, error.statusCode)
  }

  @Test
  fun `sendBatch with out-of-range status raises InvalidTelemetryTransportOutcomeError`() {
    val requester = RemoteTransportPort { _, _, _, _ -> RemoteTransportResponse(statusCode = 99, body = "") }
    val settings = settingsWithProxy("https://telemetry.example.dev/ingest")

    val error =
      assertFailsWith<InvalidTelemetryTransportOutcomeError> {
        client(requester).sendBatch(settings, emptyList())
      }

    assertTrue(SkillBillRuntimeException::class.java.isInstance(error))
    assertEquals(99, error.statusCode)
  }
}

private fun settingsWithProxy(proxyUrl: String): TelemetrySettings =
  TelemetrySettings(
    configPath = Files.createTempFile("telemetry-typed-errors", ".json").toFileLocation(),
    level = "anonymous",
    enabled = true,
    installId = "test-install-id",
    proxyUrl = proxyUrl,
    customProxyUrl = proxyUrl.ifBlank { null },
    batchSize = 50,
  )

private fun capabilitiesBody(): String =
  """
  {
    "contract_version": "2",
    "supports_ingest": true,
    "supports_stats": true,
    "supports_event_deduplication": true
  }
  """.trimIndent()

private fun client(requester: RemoteTransportPort): HttpTelemetryClient =
  HttpTelemetryClient(
    requester = requester,
    environmentContext = EnvironmentContext(environment = emptyMap()),
    clock = Clock.systemUTC(),
    diagnostics = SilentTypedErrorsDiagnostics,
  )

private object SilentTypedErrorsDiagnostics : RuntimeDiagnostics {
  override fun warning(message: String, error: Throwable?) = Unit

  override fun error(message: String, error: Throwable?) = Unit
}
