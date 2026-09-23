package skillbill.application.telemetry.config

import skillbill.ports.telemetry.transport.TelemetryConfigStore
import skillbill.ports.telemetry.transport.TelemetryOutboxRepository
import skillbill.ports.telemetry.transport.TelemetrySettingsProvider
import skillbill.ports.telemetry.transport.writeTelemetryLevel
import skillbill.telemetry.model.TelemetrySettings
import skillbill.telemetry.telemetryLevels

object TelemetryConfigMutations {
  fun setTelemetryLevel(
    level: String,
    configStore: TelemetryConfigStore,
    settingsProvider: TelemetrySettingsProvider,
    outbox: TelemetryOutboxRepository? = null,
  ): Pair<TelemetrySettings, Int> {
    require(level in telemetryLevels) {
      "Telemetry level must be one of: ${telemetryLevels.joinToString(", ")}."
    }
    val currentLevel = settingsProvider.load(materialize = false).level
    return if (level == "off") {
      disableTelemetry(configStore, settingsProvider, outbox)
    } else {
      enableTelemetry(
        configStore = configStore,
        settingsProvider = settingsProvider,
        level = level,
        outbox = outbox.takeIf { clearsPendingOutbox(currentLevel, level) },
      )
    }
  }

  fun setTelemetryEnabled(
    enabled: Boolean,
    configStore: TelemetryConfigStore,
    settingsProvider: TelemetrySettingsProvider,
    outbox: TelemetryOutboxRepository? = null,
  ): Pair<TelemetrySettings, Int> =
    setTelemetryLevel(
      level = if (enabled) "anonymous" else "off",
      configStore = configStore,
      settingsProvider = settingsProvider,
      outbox = outbox,
    )
}

internal fun clearsPendingOutbox(
  currentLevel: String,
  newLevel: String,
): Boolean {
  if (newLevel == "off") return true
  val current = telemetryLevels.indexOf(currentLevel).takeIf { it >= 0 } ?: telemetryLevels.lastIndex
  return telemetryLevels.indexOf(newLevel) < current
}

private fun enableTelemetry(
  configStore: TelemetryConfigStore,
  settingsProvider: TelemetrySettingsProvider,
  level: String,
  outbox: TelemetryOutboxRepository?,
): Pair<TelemetrySettings, Int> {
  configStore.writeTelemetryLevel(level)
  val clearedEvents = outbox?.clear().orEmpty()
  return settingsProvider.load(materialize = true) to clearedEvents
}

private fun disableTelemetry(
  configStore: TelemetryConfigStore,
  settingsProvider: TelemetrySettingsProvider,
  outbox: TelemetryOutboxRepository?,
): Pair<TelemetrySettings, Int> {
  configStore.writeTelemetryLevel("off")
  val clearedEvents = outbox?.clear().orEmpty()
  return settingsProvider.load(materialize = false) to clearedEvents
}

private fun Int?.orEmpty(): Int = this ?: 0
