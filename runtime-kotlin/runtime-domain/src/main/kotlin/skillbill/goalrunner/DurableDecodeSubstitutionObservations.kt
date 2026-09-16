package skillbill.goalrunner

data class DurableDecodeSubstitutionRecord(
  val seam: String,
  val valueUsed: String,
  val expectedValue: String,
  val reason: String,
)

object DurableDecodeSubstitutionObservations {
  private val records = mutableListOf<DurableDecodeSubstitutionRecord>()

  @Synchronized
  fun record(seam: String, valueUsed: String, expectedValue: String, reason: String) {
    records.add(
      DurableDecodeSubstitutionRecord(
        seam = seam,
        valueUsed = valueUsed,
        expectedValue = expectedValue,
        reason = reason,
      ),
    )
  }

  @Synchronized
  fun drain(): List<DurableDecodeSubstitutionRecord> = records.toList().also { records.clear() }
}

internal fun recordDurableDecodeSubstitution(
  seam: String,
  valueUsed: String,
  expectedValue: String,
  reason: String,
) {
  DurableDecodeSubstitutionObservations.record(seam, valueUsed, expectedValue, reason)
}
