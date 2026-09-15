package skillbill.application.telemetry.sync

import skillbill.ports.telemetry.TelemetryClient
import skillbill.ports.telemetry.TelemetryOutboxRepository
import skillbill.ports.telemetry.model.TELEMETRY_DELIVERY_ATTEMPT_BUDGET
import skillbill.ports.telemetry.model.TelemetryOutboxClaimRequest
import skillbill.ports.telemetry.model.TelemetryOutboxRecord
import skillbill.telemetry.model.SyncResult
import skillbill.telemetry.model.TelemetryDeliveryOutcome
import skillbill.telemetry.model.TelemetryDeliveryReport
import skillbill.telemetry.model.TelemetrySettings
import skillbill.telemetry.model.TelemetrySyncStatus
import java.time.Duration
import java.time.Instant
import java.util.UUID

private val CLAIM_LEASE: Duration = Duration.ofMinutes(5)

internal data class DrainRequest(
  val outboxRepository: TelemetryOutboxRepository,
  val settings: TelemetrySettings,
  val client: TelemetryClient,
  val syncContext: SyncContext,
  val now: Instant,
)

internal fun drainPendingBatches(request: DrainRequest): SyncResult {
  val claimToken = UUID.randomUUID().toString()
  var syncedTotal = 0
  repeat(batchBudget(request.syncContext.pendingEvents, request.settings.batchSize)) {
    val rows = claimBatch(request, claimToken)
    if (rows.isEmpty()) {
      return drainedSyncResult(request, syncedTotal)
    }
    val failure = deliverBatch(request, rows, syncedTotal)
    if (failure != null) {
      return failure
    }
    syncedTotal += rows.size
  }
  return drainedSyncResult(request, syncedTotal)
}

private fun claimBatch(request: DrainRequest, claimToken: String): List<TelemetryOutboxRecord> =
  request.outboxRepository.claimPending(
    TelemetryOutboxClaimRequest(
      claimToken = claimToken,
      limit = request.settings.batchSize,
      claimedAt = request.now,
      reclaimBefore = request.now.minus(CLAIM_LEASE),
    ),
  )

private fun deliverBatch(request: DrainRequest, rows: List<TelemetryOutboxRecord>, syncedTotal: Int): SyncResult? {
  val eventIds = rows.map { it.id }
  val report = attemptDelivery(request, rows)
  if (report.outcome != TelemetryDeliveryOutcome.ACCEPTED) {
    return failedBatchResult(
      request,
      eventIds,
      syncedTotal,
      report.detail,
      consumesAttempt = report.outcome == TelemetryDeliveryOutcome.REJECTED,
    )
  }
  try {
    request.outboxRepository.markSynced(eventIds)
  } catch (error: Exception) {
    return failedBatchResult(
      request,
      eventIds,
      syncedTotal,
      "delivery accepted but the local acknowledgement failed: ${error.message.orEmpty()}",
      consumesAttempt = true,
    )
  }
  return null
}

private fun attemptDelivery(request: DrainRequest, rows: List<TelemetryOutboxRecord>): TelemetryDeliveryReport {
  val report =
    try {
      request.client.sendBatch(request.settings, rows)
    } catch (error: Exception) {
      return TelemetryDeliveryReport(TelemetryDeliveryOutcome.UNKNOWN, unconfirmedMessage(failureDetail(error)))
    }
  return when (report.outcome) {
    TelemetryDeliveryOutcome.ACCEPTED -> report
    TelemetryDeliveryOutcome.REJECTED -> report.copy(detail = rejectedMessage(report.detail))
    TelemetryDeliveryOutcome.UNKNOWN -> report.copy(detail = unconfirmedMessage(report.detail))
  }
}

private fun failureDetail(error: Exception): String =
  error.message.orEmpty().ifBlank { error::class.simpleName.orEmpty() }

private fun rejectedMessage(detail: String): String {
  val suffix = detail.ifBlank { "the receiver returned no reason" }
  return "receiver rejected the batch: $suffix"
}

private fun unconfirmedMessage(detail: String): String {
  val suffix = detail.ifBlank { "no acknowledgement was received" }
  return "delivery unconfirmed, the receiver may already hold this batch: $suffix"
}

private fun failedBatchResult(
  request: DrainRequest,
  eventIds: List<Long>,
  syncedTotal: Int,
  message: String,
  consumesAttempt: Boolean,
): SyncResult {
  if (consumesAttempt) {
    request.outboxRepository.markFailed(eventIds, message)
  } else {
    request.outboxRepository.markUnconfirmed(eventIds, message)
  }
  return syncResult(
    status = TelemetrySyncStatus.FAILED,
    syncedEvents = syncedTotal,
    pendingEvents = request.outboxRepository.pendingCount(),
    syncContext = request.syncContext,
    message = message,
  )
}

private fun drainedSyncResult(request: DrainRequest, syncedTotal: Int): SyncResult {
  val pending = request.outboxRepository.pendingCount()
  val blocked = request.outboxRepository.blockedCount(TELEMETRY_DELIVERY_ATTEMPT_BUDGET)
  if (blocked > 0) {
    return syncResult(
      status = TelemetrySyncStatus.FAILED,
      syncedEvents = syncedTotal,
      pendingEvents = pending,
      syncContext = request.syncContext,
      message = blockedMessage(blocked, request.outboxRepository.latestError()),
    )
  }
  if (pending > 0) {
    return syncResult(
      status = if (syncedTotal == 0) TelemetrySyncStatus.NOOP else TelemetrySyncStatus.SYNCED,
      syncedEvents = syncedTotal,
      pendingEvents = pending,
      syncContext = request.syncContext,
      message = undrainedMessage(pending),
    )
  }
  return completedSyncResult(request.syncContext, syncedTotal, pending)
}

private fun blockedMessage(blocked: Int, latestError: String?): String =
  "$blocked telemetry event(s) exceeded the $TELEMETRY_DELIVERY_ATTEMPT_BUDGET-attempt delivery budget and stay " +
    "queued without further delivery attempts. There is no redelivery command: `skill-bill telemetry status` " +
    "reports them as blocked_events and `skill-bill telemetry clear` discards them. Latest error: " +
    (latestError ?: "unknown")

private fun undrainedMessage(pending: Int): String =
  "$pending telemetry event(s) stayed queued through this drain, held by a concurrent drain's claim or " +
    "enqueued while it ran. The next sync delivers them."

private fun batchBudget(pendingEvents: Int, batchSize: Int): Int {
  if (batchSize <= 0) {
    return 1
  }
  return pendingEvents / batchSize + 1
}
