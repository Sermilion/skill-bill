package skillbill.infrastructure.sqlite.telemetry

import skillbill.ports.telemetry.FeatureTaskRuntimeLifecycleTelemetryRepository
import skillbill.ports.telemetry.FeatureTaskRuntimeTelemetryMeasurementRepository
import skillbill.ports.telemetry.FeatureVerifyLifecycleTelemetryRepository
import skillbill.ports.telemetry.GoalLifecycleTelemetryRepository
import skillbill.ports.telemetry.LifecycleTelemetryRepository
import skillbill.ports.telemetry.PrDescriptionLifecycleTelemetryRepository
import skillbill.ports.telemetry.QualityCheckLifecycleTelemetryRepository
import skillbill.ports.telemetry.ReviewStageTelemetryMeasurementRepository
import skillbill.telemetry.model.PrDescriptionGeneratedRecord
import java.sql.Connection

internal class LifecycleTelemetryStore private constructor(
  adapters: LifecycleTelemetryStoreAdapters,
) : LifecycleTelemetryRepository,
  FeatureTaskRuntimeTelemetryMeasurementRepository by adapters.measurements,
  ReviewStageTelemetryMeasurementRepository by adapters.measurements,
  FeatureTaskRuntimeLifecycleTelemetryRepository by adapters.featureTaskSessions,
  QualityCheckLifecycleTelemetryRepository by adapters.qualityCheckSessions,
  FeatureVerifyLifecycleTelemetryRepository by adapters.featureVerifySessions,
  PrDescriptionLifecycleTelemetryRepository by adapters.prDescriptionSessions,
  GoalLifecycleTelemetryRepository by adapters.goalSessions {
  companion object {
    operator fun invoke(connection: Connection): LifecycleTelemetryStore =
      LifecycleTelemetryStore(LifecycleTelemetryStoreAdapters(connection))
  }
}

internal class LifecycleTelemetryStoreAdapters(connection: Connection) {
  val measurements = LifecycleTelemetryMeasurementAdapter(connection)
  val featureTaskSessions = LifecycleTelemetryFeatureTaskSessionAdapter(connection)
  val qualityCheckSessions = LifecycleTelemetryQualityCheckSessionAdapter(connection)
  val featureVerifySessions = LifecycleTelemetryFeatureVerifySessionAdapter(connection)
  val prDescriptionSessions = LifecycleTelemetryPrDescriptionSessionAdapter(connection)
  val goalSessions = LifecycleTelemetryGoalSessionAdapter(connection)
}

internal class LifecycleTelemetryPrDescriptionSessionAdapter(
  private val connection: Connection,
) : PrDescriptionLifecycleTelemetryRepository {
  override fun prDescriptionGenerated(record: PrDescriptionGeneratedRecord, level: String) {
    enqueueTelemetry(connection, "skillbill_pr_description_generated", prDescriptionPayload(record, level))
  }
}
