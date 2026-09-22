package skillbill.ports.telemetry.transport
import skillbill.ports.telemetry.model.TelemetryOutboxClaimRequest
import skillbill.ports.telemetry.model.TelemetryOutboxRecord
import skillbill.ports.telemetry.model.TelemetryOutboxSettlementResult

interface TelemetryOutboxRepository {
  fun enqueue(
    eventName: String,
    payloadJson: String,
  ): Long

  fun claimPending(request: TelemetryOutboxClaimRequest): List<TelemetryOutboxRecord>

  fun pendingCount(): Int

  fun blockedCount(attemptBudget: Int): Int

  fun latestError(): String?

  fun lastSyncedAt(): String?

  fun markSynced(
    eventIds: List<Long>,
    claimToken: String,
  ): TelemetryOutboxSettlementResult

  fun markFailed(
    eventIds: List<Long>,
    claimToken: String,
    lastError: String,
  ): TelemetryOutboxSettlementResult

  fun markUnconfirmed(
    eventIds: List<Long>,
    claimToken: String,
    lastError: String,
  ): TelemetryOutboxSettlementResult

  fun clear(): Int
}
