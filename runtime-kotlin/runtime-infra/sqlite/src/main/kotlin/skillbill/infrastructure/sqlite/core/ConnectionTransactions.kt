package skillbill.infrastructure.sqlite.core

import skillbill.error.DatabaseAccessOperation
import skillbill.ports.diagnostics.RuntimeDiagnostics
import java.nio.file.Path
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.Types

private const val ROLLBACK_FAILURE_DETAIL_LIMIT = 240

internal enum class DatabaseTransactionBeginMode(val sql: String) {
  IMMEDIATE("BEGIN IMMEDIATE"),
  DEFERRED("BEGIN DEFERRED"),
}

internal inline fun <T> Connection.inDatabaseTransaction(
  dbPath: Path,
  beginMode: DatabaseTransactionBeginMode,
  operation: DatabaseAccessOperation,
  diagnostics: RuntimeDiagnostics,
  mapSqlExceptions: Boolean = true,
  block: Connection.() -> T,
): T {
  executeTransactionStatement(beginMode.sql)
  var committed = false
  var primaryFailure: Throwable? = null
  return try {
    val result = try {
      block()
    } catch (failure: Throwable) {
      val mappedFailure = if (mapSqlExceptions) {
        mapTransactionBlockFailure(dbPath, operation, failure)
      } else {
        failure
      }
      primaryFailure = mappedFailure
      throw mappedFailure
    }
    try {
      executeTransactionStatement("COMMIT")
    } catch (failure: Throwable) {
      primaryFailure = failure
      throw failure
    }
    committed = true
    result
  } finally {
    if (!committed) {
      rollbackAfterFailedTransaction(dbPath, diagnostics, primaryFailure)
    }
  }
}

private fun mapTransactionBlockFailure(
  dbPath: Path,
  operation: DatabaseAccessOperation,
  failure: Throwable,
): Throwable = when {
  failure is SQLException && operation == DatabaseAccessOperation.WRITE ->
    databaseAccessError(dbPath, DatabaseAccessOperation.WRITE, failure)
  failure is SQLException && operation == DatabaseAccessOperation.READ ->
    databaseAccessError(dbPath, DatabaseAccessOperation.READ, failure)
  else -> failure
}

private fun Connection.executeTransactionStatement(sql: String) {
  createStatement().use { it.execute(sql) }
}

internal fun Connection.rollbackAfterFailedTransaction(
  dbPath: Path,
  diagnostics: RuntimeDiagnostics,
  primaryFailure: Throwable?,
) {
  val rollbackOutcome = runCatching { createStatement().use { it.execute("ROLLBACK") } }
  val rollbackFailure = rollbackOutcome.exceptionOrNull() ?: return
  if (primaryFailure != null && primaryFailure !== rollbackFailure) {
    primaryFailure.addSuppressed(rollbackFailure)
  }
  runCatching { logTransactionRollbackFailure(dbPath, diagnostics, rollbackFailure, primaryFailure) }
}

internal fun logTransactionRollbackFailure(
  dbPath: Path,
  diagnostics: RuntimeDiagnostics,
  rollbackFailure: Throwable,
  primaryFailure: Throwable?,
) {
  val rollbackDetail = boundedTransactionFailureDetail(rollbackFailure)
  val primaryDetail = primaryFailure?.let(::boundedTransactionFailureDetail) ?: "none"
  diagnostics.warning(
    "skillbill sqlite: transaction rollback failed after transaction failure at ${dbPath.toAbsolutePath().normalize()}; " +
      "rollback=$rollbackDetail; primary=$primaryDetail",
    rollbackFailure,
  )
}

private fun boundedTransactionFailureDetail(failure: Throwable): String {
  val message = failure.message?.takeIf { it.isNotBlank() }
  return (message ?: failure::class.simpleName.orEmpty()).take(ROLLBACK_FAILURE_DETAIL_LIMIT)
}

internal inline fun <T> Connection.inNestedWriteTransaction(
  diagnostics: RuntimeDiagnostics = InternalSqliteDiagnostics,
  block: Connection.() -> T,
): T = inDatabaseTransaction(
  dbPath = this.databasePath(),
  beginMode = DatabaseTransactionBeginMode.IMMEDIATE,
  operation = DatabaseAccessOperation.WRITE,
  diagnostics = diagnostics,
  mapSqlExceptions = false,
  block = block,
)

internal fun PreparedStatement.bindAll(vararg values: Any?) {
  values.forEachIndexed { index, value ->
    val parameterIndex = index + 1
    when (value) {
      null -> setNull(parameterIndex, Types.NULL)
      is String -> setString(parameterIndex, value)
      is Int -> setInt(parameterIndex, value)
      is Long -> setLong(parameterIndex, value)
      is Boolean -> setBoolean(parameterIndex, value)
      is ByteArray -> setBytes(parameterIndex, value)
      else -> throw IllegalArgumentException("Unsupported bind value at index $parameterIndex: ${value::class}")
    }
  }
}
