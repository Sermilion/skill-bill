package skillbill.engine.featuretask.model

import skillbill.application.telemetry.model.FeatureTaskRuntimeAgentContext
import skillbill.application.telemetry.model.FeatureTaskRuntimeFindingVerificationTelemetry
import skillbill.application.telemetry.model.FeatureTaskRuntimeRegenerationTelemetry
data class FeatureTaskRuntimeFinishedTelemetryContext(
  val telemetrySessionId: String,
  val phaseOutcomes: () -> Map<String, String>,
  val reviewFixIterationCount: () -> Int,
  val auditGapIterationCount: () -> Int? = { null },
  val agentContext: () -> FeatureTaskRuntimeAgentContext = { FeatureTaskRuntimeAgentContext() },
  val findingVerificationTelemetry: () -> FeatureTaskRuntimeFindingVerificationTelemetry = {
    FeatureTaskRuntimeFindingVerificationTelemetry()
  },
  val regenerationTelemetry: () -> FeatureTaskRuntimeRegenerationTelemetry = {
    FeatureTaskRuntimeRegenerationTelemetry()
  },
  val phaseTokenData: () -> Pair<String?, Int?> = { null to null },
  val crashReconciliation: () -> FeatureTaskRuntimeCrashReconciliationResult = {
    FeatureTaskRuntimeCrashReconciliationResult.NONE
  },
)
