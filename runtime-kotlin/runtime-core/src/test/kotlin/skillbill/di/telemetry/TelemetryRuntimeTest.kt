package skillbill.di.telemetry

import skillbill.application.telemetry.sync.TelemetrySyncRuntime
import skillbill.contracts.JsonCodec
import skillbill.contracts.telemetry.TelemetryOutboxEvent
import skillbill.infrastructure.host.concurrency.JvmInterruptSignalPort
import skillbill.infrastructure.sqlite.withTelemetryOutboxStore
import skillbill.ports.repository.toFileLocation
import skillbill.ports.telemetry.model.TelemetryOutboxRecord
import skillbill.ports.telemetry.transport.TelemetryClient
import skillbill.telemetry.model.RemoteStatsRequest
import skillbill.telemetry.model.TelemetryDeliveryOutcome
import skillbill.telemetry.model.TelemetryDeliveryReport
import skillbill.telemetry.model.TelemetryProxyCapabilities
import skillbill.telemetry.model.TelemetryRemoteStatsResult
import skillbill.telemetry.model.TelemetrySettings
import skillbill.telemetry.model.TelemetrySyncStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val SYNC_NOW: Instant = Instant.parse("2026-09-15T10:00:00Z")

class TelemetryRuntimeTest {
  @Test
  fun `syncTelemetry marks batch synced and failed batches`() {
    val tempDir = Files.createTempDirectory("telemetry-sync")
    val dbPath = tempDir.resolve("metrics.db")
    val settings =
      telemetrySettings(
        configPath = tempDir.resolve("config.json"),
        proxyUrl = "http://127.0.0.1:0",
        customProxyUrl = "http://127.0.0.1:0",
      )

    withTelemetryOutboxStore(tempDir, dbPath) { outboxStore ->
      outboxStore.enqueue(TelemetryOutboxEvent.GOAL_STARTED, JsonCodec.mapToJsonString(mapOf("name" to "ok")))
      outboxStore.enqueue(TelemetryOutboxEvent.GOAL_FINISHED, JsonCodec.mapToJsonString(mapOf("name" to "fail")))

      val successClient = RecordingTelemetryClient()
      val successResult =
        TelemetrySyncRuntime.syncTelemetry(settings, outboxStore, successClient, {
          SYNC_NOW
        }, JvmInterruptSignalPort)
      assertEquals(TelemetrySyncStatus.SYNCED, successResult.status)
      assertEquals(2, successResult.syncedEvents)
      assertEquals(listOf(listOf(1L, 2L)), successClient.sentBatchIds)
    }

    withTelemetryOutboxStore(tempDir, dbPath) { outboxStore ->
      outboxStore.enqueue(
        TelemetryOutboxEvent.FEATURE_VERIFY_STARTED,
        JsonCodec.mapToJsonString(mapOf("name" to "retry")),
      )

      val failingClient = RecordingTelemetryClient(failure = IOException("blocked by network isolation sentinel"))
      val failedResult =
        TelemetrySyncRuntime.syncTelemetry(settings, outboxStore, failingClient, {
          SYNC_NOW
        }, JvmInterruptSignalPort)
      assertEquals(TelemetrySyncStatus.FAILED, failedResult.status)
      assertTrue(failedResult.message.orEmpty().contains("blocked by network isolation sentinel"))
      assertTrue(outboxStore.latestError().orEmpty().contains("blocked by network isolation sentinel"))
    }
  }

  @Test
  fun `autoSyncTelemetry returns disabled when telemetry is off`() {
    val disabledSettings =
      TelemetrySettings(
        configPath = Files.createTempFile("telemetry-invalid-config", ".json").toFileLocation(),
        level = "off",
        enabled = false,
        installId = "",
        proxyUrl = "",
        customProxyUrl = null,
        batchSize = 50,
      )

    val disabledTempDir = Files.createTempDirectory("telemetry-disabled")
    val disabledDbPath = disabledTempDir.resolve("metrics.db")
    withTelemetryOutboxStore(disabledTempDir, disabledDbPath) { outboxStore ->
      val result =
        TelemetrySyncRuntime.autoSyncTelemetry(
          settings = disabledSettings,
          outboxRepository = outboxStore,
          client = RecordingTelemetryClient(failure = IOException("must not call client")),
          nowSupplier = { SYNC_NOW },
          interruptSignal = JvmInterruptSignalPort,
        )

      assertEquals(TelemetrySyncStatus.DISABLED, result?.status)
      assertEquals(
        false,
        TelemetrySyncRuntime.telemetryStatusPayload(disabledDbPath, disabledSettings).telemetryEnabled,
      )
    }
  }

  @Test
  fun `syncTelemetry returns noop when outbox is empty`() {
    val noopTempDir = Files.createTempDirectory("telemetry-noop-run")
    val noopDbPath = noopTempDir.resolve("metrics.db")
    withTelemetryOutboxStore(noopTempDir, noopDbPath) { outboxStore ->
      val noopResult =
        TelemetrySyncRuntime.syncTelemetry(
          telemetrySettings(Files.createTempFile("telemetry-noop", ".json")),
          outboxStore,
          RecordingTelemetryClient(),
          { SYNC_NOW },
          JvmInterruptSignalPort,
        )

      assertEquals(TelemetrySyncStatus.NOOP, noopResult.status)
    }
  }

  @Test
  fun `syncTelemetry returns unconfigured when proxy url is blank`() {
    val unconfiguredTempDir = Files.createTempDirectory("telemetry-unconfigured-run")
    val unconfiguredDbPath = unconfiguredTempDir.resolve("metrics.db")
    withTelemetryOutboxStore(unconfiguredTempDir, unconfiguredDbPath) { outboxStore ->
      outboxStore.enqueue(
        TelemetryOutboxEvent.FEATURE_VERIFY_STARTED,
        JsonCodec.mapToJsonString(mapOf("name" to "pending")),
      )
      val unconfiguredResult =
        TelemetrySyncRuntime.syncTelemetry(
          telemetrySettings(
            configPath = Files.createTempFile("telemetry-unconfigured", ".json"),
            proxyUrl = "",
            customProxyUrl = null,
          ),
          outboxStore,
          RecordingTelemetryClient(failure = IOException("must not call client")),
          { SYNC_NOW },
          JvmInterruptSignalPort,
        )

      assertEquals(TelemetrySyncStatus.UNCONFIGURED, unconfiguredResult.status)
      assertEquals(1, unconfiguredResult.pendingEvents)
    }
  }
}

private class RecordingTelemetryClient(
  private val failure: IOException? = null,
) : TelemetryClient {
  val sentBatchIds = mutableListOf<List<Long>>()

  override fun sendBatch(
    settings: TelemetrySettings,
    rows: List<TelemetryOutboxRecord>,
  ): TelemetryDeliveryReport {
    failure?.let { throw it }
    sentBatchIds += rows.map { it.id }
    return TelemetryDeliveryReport(TelemetryDeliveryOutcome.ACCEPTED)
  }

  override fun fetchProxyCapabilities(settings: TelemetrySettings): TelemetryProxyCapabilities =
    error("Unexpected fetchProxyCapabilities")

  override fun fetchRemoteStats(
    settings: TelemetrySettings,
    request: RemoteStatsRequest,
  ): TelemetryRemoteStatsResult = error("Unexpected fetchRemoteStats")
}

private fun telemetrySettings(
  configPath: Path,
  proxyUrl: String = "https://telemetry.example.dev/ingest",
  customProxyUrl: String? = proxyUrl,
): TelemetrySettings =
  TelemetrySettings(
    configPath = configPath.toFileLocation(),
    level = "anonymous",
    enabled = true,
    installId = "test-install-id",
    proxyUrl = proxyUrl,
    customProxyUrl = customProxyUrl,
    batchSize = 50,
  )
