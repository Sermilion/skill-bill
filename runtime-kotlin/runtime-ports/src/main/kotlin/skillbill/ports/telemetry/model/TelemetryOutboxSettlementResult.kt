package skillbill.ports.telemetry.model

data class TelemetryOutboxSettlementResult(
  val updatedRows: Int,
  val lostClaim: Boolean,
) {
  companion object {
    fun forRequest(eventIds: List<Long>, updatedRows: Int): TelemetryOutboxSettlementResult {
      if (eventIds.isEmpty()) {
        return TelemetryOutboxSettlementResult(updatedRows = 0, lostClaim = false)
      }
      return TelemetryOutboxSettlementResult(
        updatedRows = updatedRows,
        lostClaim = updatedRows < eventIds.distinct().size,
      )
    }
  }
}
