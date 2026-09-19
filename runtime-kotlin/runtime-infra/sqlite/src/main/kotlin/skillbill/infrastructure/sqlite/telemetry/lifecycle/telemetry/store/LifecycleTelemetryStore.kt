package skillbill.infrastructure.sqlite.telemetry.lifecycle.telemetry.store
import skillbill.infrastructure.sqlite.telemetry.lifecycle.telemetry.emit.enqueueTelemetry
import skillbill.infrastructure.sqlite.telemetry.lifecycle.telemetry.feature.LifecycleTelemetryFeatureTaskSessionAdapter
import skillbill.infrastructure.sqlite.telemetry.lifecycle.telemetry.feature.LifecycleTelemetryFeatureVerifySessionAdapter
import skillbill.infrastructure.sqlite.telemetry.lifecycle.telemetry.goal.LifecycleTelemetryGoalSessionAdapter
import skillbill.infrastructure.sqlite.telemetry.lifecycle.telemetry.measurement.LifecycleTelemetryMeasurementAdapter
import skillbill.infrastructure.sqlite.telemetry.lifecycle.telemetry.payloads.prDescriptionPayload
import skillbill.infrastructure.sqlite.telemetry.lifecycle.telemetry.quality.LifecycleTelemetryQualityCheckSessionAdapter
import skillbill.ports.telemetry.lifecycle.FeatureTaskRuntimeLifecycleTelemetryRepository
import skillbill.ports.telemetry.lifecycle.FeatureTaskRuntimeTelemetryMeasurementRepository
import skillbill.ports.telemetry.lifecycle.FeatureVerifyLifecycleTelemetryRepository
import skillbill.ports.telemetry.lifecycle.GoalLifecycleTelemetryRepository
import skillbill.ports.telemetry.lifecycle.LifecycleTelemetryRepository
import skillbill.ports.telemetry.lifecycle.PrDescriptionLifecycleTelemetryRepository
import skillbill.ports.telemetry.lifecycle.QualityCheckLifecycleTelemetryRepository
import skillbill.ports.telemetry.lifecycle.ReviewStageTelemetryMeasurementRepository
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
