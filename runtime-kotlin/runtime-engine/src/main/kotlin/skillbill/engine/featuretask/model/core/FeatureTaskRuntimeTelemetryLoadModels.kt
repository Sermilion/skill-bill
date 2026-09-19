package skillbill.engine.featuretask.model.core
internal data class FeatureTaskRuntimeRegenerationTelemetry(
  val activationCount: Int = 0,
  val attemptCount: Int = 0,
  val outcomeCounts: Map<String, Int> = emptyMap(),
)

internal data class FeatureTaskRuntimeFindingVerificationTelemetry(
  val verifiedCount: Int = 0,
  val rejectedCount: Int = 0,
  val reviewFixCapExhausted: Boolean? = null,
)
