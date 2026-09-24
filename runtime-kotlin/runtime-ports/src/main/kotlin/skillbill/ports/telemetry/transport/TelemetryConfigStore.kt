package skillbill.ports.telemetry.transport

import skillbill.telemetry.model.TelemetryConfigDocument
import skillbill.telemetry.telemetryLevels
import skillbill.telemetry.withTelemetryLevel
import java.nio.file.Path

interface TelemetryConfigStore {
  fun stateDir(): Path

  fun configPath(): Path

  fun read(): TelemetryConfigDocument?

  fun ensure(): TelemetryConfigDocument

  fun write(document: TelemetryConfigDocument)
}

fun TelemetryConfigStore.writeTelemetryLevel(level: String): Boolean {
  val document = if (level == telemetryLevels.first()) read() ?: return false else ensure()
  write(document.withTelemetryLevel(level, configPath().toString()))
  return true
}
