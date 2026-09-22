package skillbill.infrastructure.sqlite.telemetry.lifecycle.telemetry.feature
import skillbill.infrastructure.sqlite.telemetry.lifecycle.telemetry.emit.emitFeatureTaskRuntimeFinished
import skillbill.infrastructure.sqlite.telemetry.lifecycle.telemetry.emit.emitFeatureTaskRuntimeStarted
import skillbill.infrastructure.sqlite.telemetry.lifecycle.telemetry.runtime.saveFeatureTaskRuntimeFinished
import skillbill.infrastructure.sqlite.telemetry.lifecycle.telemetry.runtime.saveFeatureTaskRuntimeStarted
import skillbill.infrastructure.sqlite.telemetry.lifecycle.telemetry.save.TerminalSaveOutcome
import skillbill.ports.telemetry.lifecycle.FeatureTaskRuntimeLifecycleTelemetryRepository
import skillbill.telemetry.model.FeatureTaskRuntimeFinishedRecord
import skillbill.telemetry.model.FeatureTaskRuntimeStartedRecord
import java.sql.Connection

internal class LifecycleTelemetryFeatureTaskSessionAdapter(
  private val connection: Connection,
) : FeatureTaskRuntimeLifecycleTelemetryRepository {
  override fun featureTaskRuntimeStarted(
    record: FeatureTaskRuntimeStartedRecord,
    level: String,
  ) {
    saveFeatureTaskRuntimeStarted(connection, record)
    emitFeatureTaskRuntimeStarted(connection, record.sessionId, level)
  }

  override fun featureTaskRuntimeFinished(
    record: FeatureTaskRuntimeFinishedRecord,
    level: String,
  ) {
    if (saveFeatureTaskRuntimeFinished(connection, record) == TerminalSaveOutcome.FIRST_TERMINAL) {
      emitFeatureTaskRuntimeFinished(connection, record.sessionId, level)
    }
  }
}
