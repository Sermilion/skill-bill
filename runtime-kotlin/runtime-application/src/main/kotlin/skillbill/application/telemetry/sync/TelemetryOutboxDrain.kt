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
import kotlin.coroutines.cancellation.CancellationException

internal const val CLAIM_LEASE_MINUTES: Long = 5

private val CLAIM_LEASE: Duration = Duration.ofMinutes(CLAIM_LEASE_MINUTES)

private const val LOST_CLAIM_MESSAGE =
  "delivery settlement lost the outbox claim before the result could be recorded locally"

internal data class DrainRequest(
  val outboxRepository: TelemetryOutboxRepository,
  val settings: TelemetrySettings,
  val client: TelemetryClient,
  val syncContext: SyncContext,
  val nowSupplier: () -> Instant,
)

internal fun drainPendingBatches(request: DrainRequest): SyncResult {
  val claimToken = UUID.randomUUID().toString()
  var syncedTotal = 0
  repeat(batchBudget(request.syncContext.pendingEvents, request.settings.batchSize)) {
    val rows = claimBatch(request, claimToken)
    if (rows.isEmpty()) {
      return drainedSyncResult(request, syncedTotal)
    }
    val failure = deliverBatch(request, rows, claimToken, syncedTotal)
    if (failure != null) {
      return failure
    }
    syncedTotal += rows.size
  }
  return drainedSyncResult(request, syncedTotal)
}

private fun claimBatch(request: DrainRequest, claimToken: String): List<TelemetryOutboxRecord> {
  val batchNow = request.nowSupplier()
  return request.outboxRepository.claimPending(
    TelemetryOutboxClaimRequest(
      claimToken = claimToken,
      limit = request.settings.batchSize,
      claimedAt = batchNow,
      reclaimBefore = batchNow.minus(CLAIM_LEASE),
    ),
  )
}

private fun deliverBatch(
  request: DrainRequest,
  rows: List<TelemetryOutboxRecord>,
  claimToken: String,
  syncedTotal: Int,
): SyncResult? {
  val eventIds = rows.map { it.id }
  val report = attemptDelivery(request, rows)
  if (report.outcome != TelemetryDeliveryOutcome.ACCEPTED) {
    val settlement =
      settleFailedDelivery(
        request,
        eventIds,
        claimToken,
        report.detail,
        consumesAttempt = report.outcome == TelemetryDeliveryOutcome.REJECTED,
        syncedTotal = syncedTotal,
      )
    if (settlement != null) {
      return settlement
    }
    return failedBatchResult(
      request,
      syncedTotal,
      report.detail,
    )
  }
  val acknowledgement =
    runCatching { request.outboxRepository.markSynced(eventIds, claimToken) }
      .fold(
        onSuccess = { it },
        onFailure = { thrown ->
          if (thrown.isCooperativeCancellation()) {
            throw thrown
          }
          if (thrown is InterruptedException) {
            Thread.currentThread().interrupt()
            throw thrown
          }
          if (thrown !is Exception) {
            throw thrown
          }
          return failedBatchResult(
            request,
            syncedTotal,
            "delivery accepted but the local acknowledgement failed: ${thrown.message.orEmpty()}",
            markFailure = {
              request.outboxRepository.markFailed(
                eventIds,
                claimToken,
                "delivery accepted but the local acknowledgement failed: ${thrown.message.orEmpty()}",
              )
            },
          )
        },
      )
  if (acknowledgement.lostClaim) {
    return lostClaimBatchResult(request, syncedTotal)
  }
  return null
}

private fun settleFailedDelivery(
  request: DrainRequest,
  eventIds: List<Long>,
  claimToken: String,
  message: String,
  consumesAttempt: Boolean,
  syncedTotal: Int,
): SyncResult? {
  val settlement =
    runCatching {
      if (consumesAttempt) {
        request.outboxRepository.markFailed(eventIds, claimToken, message)
      } else {
        request.outboxRepository.markUnconfirmed(eventIds, claimToken, message)
      }
    }.getOrElse { thrown ->
      if (thrown.isCooperativeCancellation()) {
        throw thrown
      }
      if (thrown is InterruptedException) {
        Thread.currentThread().interrupt()
        throw thrown
      }
      if (thrown !is Exception) {
        throw thrown
      }
      return failedBatchResult(request, syncedTotal, message = thrown.message.orEmpty())
    }
  if (settlement.lostClaim) {
    return lostClaimBatchResult(request, syncedTotal)
  }
  return null
}

private fun attemptDelivery(request: DrainRequest, rows: List<TelemetryOutboxRecord>): TelemetryDeliveryReport {
  val report =
    runCatching { request.client.sendBatch(request.settings, rows) }
      .getOrElse { thrown ->
        if (thrown.isCooperativeCancellation()) {
          throw thrown
        }
        if (thrown is InterruptedException) {
          Thread.currentThread().interrupt()
          throw thrown
        }
        if (thrown !is Exception) {
          throw thrown
        }
        return TelemetryDeliveryReport(TelemetryDeliveryOutcome.UNKNOWN, unconfirmedMessage(failureDetail(thrown)))
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
  syncedTotal: Int,
  message: String,
  markFailure: (() -> Unit)? = null,
): SyncResult {
  markFailure?.invoke()
  return syncResult(
    status = TelemetrySyncStatus.FAILED,
    syncedEvents = syncedTotal,
    pendingEvents = request.outboxRepository.pendingCount(),
    syncContext = request.syncContext,
    message = message,
  )
}

private fun lostClaimBatchResult(request: DrainRequest, syncedTotal: Int): SyncResult =
  syncResult(
    status = TelemetrySyncStatus.FAILED,
    syncedEvents = syncedTotal,
    pendingEvents = request.outboxRepository.pendingCount(),
    syncContext = request.syncContext,
    message = LOST_CLAIM_MESSAGE,
  )

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

private fun Throwable.isCooperativeCancellation(): Boolean =
  this is CancellationException || this is java.util.concurrent.CancellationException
