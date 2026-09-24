package skillbill.goalrunner

import skillbill.goalrunner.model.DurableDecodeSubstitutionObservations

internal fun recordDurableDecodeSubstitution(
  seam: String,
  valueUsed: String,
  expectedValue: String,
  reason: String,
) {
  DurableDecodeSubstitutionObservations.record(seam, valueUsed, expectedValue, reason)
}
