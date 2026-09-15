package skillbill.engine.featuretask.model

data class FeatureTaskRuntimeCrashReconciliationResult(
  val reconciledCount: Int = 0,
  val reasonClassCounts: Map<String, Int> = emptyMap(),
) {
  companion object {
    val NONE = FeatureTaskRuntimeCrashReconciliationResult()
  }
}

enum class FeatureTaskRuntimeCrashReconciliationReason(val wireValue: String) {
  LEASE_EXPIRED("lease_expired"),
}
