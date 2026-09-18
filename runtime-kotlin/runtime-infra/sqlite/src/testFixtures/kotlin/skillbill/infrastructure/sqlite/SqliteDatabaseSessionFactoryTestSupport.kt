package skillbill.infrastructure.sqlite

import skillbill.model.EnvironmentContext
import skillbill.ports.diagnostics.RuntimeDiagnostics
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

fun sqliteDatabaseSessionFactory(
  userHome: Path,
  dbPathOverride: String? = null,
  environment: Map<String, String>,
  clock: Clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
  diagnostics: RuntimeDiagnostics = SqliteTestDiagnostics,
): SQLiteDatabaseSessionFactory = SQLiteDatabaseSessionFactory(
  EnvironmentContext(
    dbPathOverride = dbPathOverride,
    environment = environment,
    userHome = userHome,
  ),
  clock,
  diagnostics,
)

object SqliteTestDiagnostics : RuntimeDiagnostics {
  private val warnings = mutableListOf<String>()

  fun recordedWarnings(): List<String> = warnings.toList()

  fun reset() {
    warnings.clear()
  }

  override fun warning(message: String, error: Throwable?) {
    warnings += message
  }

  override fun error(message: String, error: Throwable?) {
    warnings += message
  }
}
