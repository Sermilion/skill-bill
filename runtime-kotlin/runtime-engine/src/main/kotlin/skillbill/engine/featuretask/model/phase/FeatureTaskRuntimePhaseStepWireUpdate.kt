package skillbill.engine.featuretask.model.phase

import skillbill.contracts.SharedPayloadKeys

internal data class FeatureTaskRuntimePhaseStepWireUpdate(
  val stepId: String,
  val status: String,
  val attemptCount: Int,
) {
  fun toWireMap(): Map<String, Any?> =
    linkedMapOf(
      SharedPayloadKeys.STEP_ID to stepId,
      SharedPayloadKeys.STATUS to status,
      "attempt_count" to attemptCount,
    )
}
