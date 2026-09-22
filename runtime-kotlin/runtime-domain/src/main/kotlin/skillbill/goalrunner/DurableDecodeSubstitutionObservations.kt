package skillbill.goalrunner

import skillbill.goalrunner.model.DurableDecodeSubstitutionObservations as ModelDurableDecodeSubstitutionObservations
import skillbill.goalrunner.model.DurableDecodeSubstitutionRecord as ModelDurableDecodeSubstitutionRecord

typealias DurableDecodeSubstitutionRecord =
  ModelDurableDecodeSubstitutionRecord
typealias DurableDecodeSubstitutionObservations =
  ModelDurableDecodeSubstitutionObservations

internal fun recordDurableDecodeSubstitution(
  seam: String,
  valueUsed: String,
  expectedValue: String,
  reason: String,
) {
  ModelDurableDecodeSubstitutionObservations.record(seam, valueUsed, expectedValue, reason)
}
