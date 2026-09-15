package skillbill.ports.telemetry

import skillbill.ports.telemetry.model.TelemetryOutboxClaimRequest
import skillbill.ports.telemetry.model.TelemetryOutboxRecord

interface TelemetryOutboxRepository {
  fun enqueue(eventName: String, payloadJson: String): Long

  fun listPending(limit: Int? = null): List<TelemetryOutboxRecord>

  fun claimPending(request: TelemetryOutboxClaimRequest): List<TelemetryOutboxRecord>

  fun pendingCount(): Int

  fun blockedCount(attemptBudget: Int): Int

  fun latestError(): String?

  fun lastSyncedAt(): String?

  fun markSynced(id: Long, syncedAt: String)

  fun markSynced(eventIds: List<Long>)

  fun markFailed(id: Long, lastError: String)

  fun markFailed(eventIds: List<Long>, lastError: String)

  fun markUnconfirmed(eventIds: List<Long>, lastError: String)

  fun clear(): Int
}
