package skillbill.ports.telemetry.model

import java.time.Instant

const val TELEMETRY_DELIVERY_ATTEMPT_BUDGET: Int = 5

data class TelemetryOutboxClaimRequest(
  val claimToken: String,
  val limit: Int,
  val claimedAt: Instant,
  val reclaimBefore: Instant,
  val attemptBudget: Int = TELEMETRY_DELIVERY_ATTEMPT_BUDGET,
)
