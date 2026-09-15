package skillbill.application.telemetry.sync

import skillbill.application.telemetry.model.TelemetryStatusResult
import skillbill.application.telemetry.model.TelemetrySyncStatusResult
import skillbill.ports.telemetry.TelemetryClient
import skillbill.ports.telemetry.TelemetryOutboxRepository
import skillbill.telemetry.model.SyncResult
import skillbill.telemetry.model.TelemetrySettings
import skillbill.telemetry.model.TelemetrySyncStatus
import java.nio.file.Path
import java.time.Instant

object TelemetrySyncRuntime {
  fun disabledSync(settings: TelemetrySettings): SyncResult = disabledSyncResult(settings)

  fun syncTelemetry(
    settings: TelemetrySettings,
    outboxRepository: TelemetryOutboxRepository,
    client: TelemetryClient,
    now: Instant,
  ): SyncResult = if (!settings.enabled) {
    disabledSyncResult(settings)
  } else {
    syncEnabledTelemetry(settings, outboxRepository, client, now)
  }

  fun syncResult(result: SyncResult): TelemetrySyncStatusResult = TelemetrySyncStatusResult(
    configPath = result.configPath.toString(),
    telemetryEnabled = result.telemetryEnabled,
    telemetryLevel = result.telemetryLevel,
    syncTarget = telemetrySyncTarget(result),
    remoteConfigured = result.remoteConfigured,
    proxyConfigured = result.proxyConfigured,
    proxyUrl = result.proxyUrl,
    customProxyUrl = result.customProxyUrl,
    syncStatus = result.status.wireValue,
    syncedEvents = result.syncedEvents,
    pendingEvents = result.pendingEvents,
    message = result.message,
  )

  fun telemetryStatusPayload(
    dbPath: Path,
    settings: TelemetrySettings,
    pendingEvents: Int = 0,
    latestError: String? = null,
    lastSyncedAt: String? = null,
    blockedEvents: Int = 0,
  ): TelemetryStatusResult = baseStatusResult(dbPath, settings).copy(
    pendingEvents = pendingEvents,
    latestError = latestError,
    lastSyncedAt = lastSyncedAt,
    blockedEvents = blockedEvents,
  )

  fun autoSyncTelemetry(
    settings: TelemetrySettings,
    outboxRepository: TelemetryOutboxRepository,
    client: TelemetryClient,
    now: Instant,
    reportFailures: Boolean = false,
    stderr: (String) -> Unit = {},
  ): SyncResult? {
    val result =
      try {
        syncTelemetry(settings, outboxRepository, client, now)
      } catch (error: Exception) {
        if (reportFailures) {
          stderr("Telemetry sync skipped: ${error.message}")
        }
        return null
      }
    if (reportFailures && result.status == TelemetrySyncStatus.FAILED && result.message != null) {
      stderr("Telemetry sync failed: ${result.message}")
    }
    return result
  }
}

fun telemetrySyncTarget(settings: TelemetrySettings): String = when {
  !settings.enabled -> "disabled"
  settings.customProxyUrl != null -> "custom_proxy"
  else -> "hosted_relay"
}

private fun syncEnabledTelemetry(
  settings: TelemetrySettings,
  outboxRepository: TelemetryOutboxRepository,
  client: TelemetryClient,
  now: Instant,
): SyncResult {
  val pendingBefore = outboxRepository.pendingCount()
  val syncContext = syncContext(settings, pendingBefore)
  return when {
    !syncContext.remoteConfigured -> unconfiguredSyncResult(syncContext)
    pendingBefore == 0 -> noopSyncResult(syncContext)
    else -> drainPendingBatches(
      DrainRequest(
        outboxRepository = outboxRepository,
        settings = settings,
        client = client,
        syncContext = syncContext,
        now = now,
      ),
    )
  }
}
