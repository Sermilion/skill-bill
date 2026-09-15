package skillbill.ports.telemetry

import skillbill.ports.telemetry.model.TelemetryOutboxClaimRequest
import skillbill.ports.telemetry.model.TelemetryOutboxRecord

interface TelemetryOutboxRepository {
  fun enqueue(eventName: String, payloadJson: String): Long

  fun claimPending(request: TelemetryOutboxClaimRequest): List<TelemetryOutboxRecord>

  fun pendingCount(): Int

  fun blockedCount(attemptBudget: Int): Int

  fun latestError(): String?

  fun lastSyncedAt(): String?

  fun markSynced(eventIds: List<Long>)

  fun markFailed(eventIds: List<Long>, lastError: String)

  fun markUnconfirmed(eventIds: List<Long>, lastError: String)

  fun clear(): Int
}
