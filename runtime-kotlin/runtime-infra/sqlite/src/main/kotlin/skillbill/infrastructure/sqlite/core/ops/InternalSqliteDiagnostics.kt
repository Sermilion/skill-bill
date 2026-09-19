package skillbill.infrastructure.sqlite.core.ops
import skillbill.ports.diagnostics.RuntimeDiagnostics
import java.sql.Connection
import java.util.concurrent.ConcurrentHashMap

internal object InternalSqliteDiagnostics : RuntimeDiagnostics {
  override fun warning(message: String, error: Throwable?) = Unit

  override fun error(message: String, error: Throwable?) = Unit
}

private val diagnosticsByConnection = ConcurrentHashMap<Connection, RuntimeDiagnostics>()

internal fun Connection.attachSqliteDiagnostics(diagnostics: RuntimeDiagnostics) {
  diagnosticsByConnection[this] = diagnostics
}

internal fun Connection.sqliteDiagnostics(): RuntimeDiagnostics =
  diagnosticsByConnection[this] ?: InternalSqliteDiagnostics

internal fun Connection.detachSqliteDiagnostics() {
  diagnosticsByConnection.remove(this)
}

internal fun RuntimeDiagnostics.recordMigrationNormalization(
  seam: String,
  parentWorkflowId: String,
  movedArtifactKeys: List<String>,
) {
  warning(
    "skillbill sqlite: record_kind=migration; seam=$seam; parent_workflow_id=$parentWorkflowId; " +
      "moved_artifact_keys=${movedArtifactKeys.joinToString(",")}",
  )
}

internal fun RuntimeDiagnostics.recordDegradedValue(
  seam: String,
  expected: String,
  used: String,
  error: Throwable? = null,
) {
  warning(
    "skillbill sqlite: degraded $seam; expected=$expected; used=$used",
    error,
  )
}
