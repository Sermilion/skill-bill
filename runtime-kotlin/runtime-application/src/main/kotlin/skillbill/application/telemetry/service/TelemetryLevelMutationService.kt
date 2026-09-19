package skillbill.application.telemetry.service
import me.tatarka.inject.annotations.Inject
import skillbill.application.telemetry.config.TelemetryConfigMutations
import skillbill.application.telemetry.config.clearsPendingOutbox
import skillbill.application.telemetry.lifecycle.level
import skillbill.application.telemetry.lifecycle.settings
import skillbill.application.telemetry.telemetry.outbox
import skillbill.application.telemetry.telemetry.service
import skillbill.application.telemetry.telemetry.settings
import skillbill.application.telemetry.telemetry.unitOfWork
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.telemetry.model.TelemetryLevelMutationResult
import skillbill.ports.telemetry.transport.TelemetryConfigStore
import skillbill.ports.telemetry.transport.TelemetryLevelMutator
import skillbill.ports.telemetry.transport.TelemetrySettingsProvider

@Inject
class TelemetryLevelMutationService(
  private val database: DatabaseSessionFactory,
  private val settingsProvider: TelemetrySettingsProvider,
  private val configStore: TelemetryConfigStore,
) : TelemetryLevelMutator {
  override fun setLevel(level: String): TelemetryLevelMutationResult {
    val currentLevel = settingsProvider.load(materialize = false).level
    val (settings, clearedEvents) =
      if (clearsPendingOutbox(currentLevel, level) && database.databaseExists()) {
        database.transaction { unitOfWork ->
          TelemetryConfigMutations.setTelemetryLevel(
            level = level,
            configStore = configStore,
            settingsProvider = settingsProvider,
            outbox = unitOfWork.telemetryOutbox,
          )
        }
      } else {
        TelemetryConfigMutations.setTelemetryLevel(
          level = level,
          configStore = configStore,
          settingsProvider = settingsProvider,
        )
      }
    return TelemetryLevelMutationResult(settings, clearedEvents)
  }
}
