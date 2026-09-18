package skillbill.infrastructure.sqlite

import me.tatarka.inject.annotations.Inject
import skillbill.error.DatabaseAccessOperation
import skillbill.infrastructure.sqlite.core.DatabaseRuntime
import skillbill.infrastructure.sqlite.core.DatabaseTransactionBeginMode
import skillbill.infrastructure.sqlite.core.OpenDatabase
import skillbill.infrastructure.sqlite.core.attachSqliteDiagnostics
import skillbill.infrastructure.sqlite.core.detachSqliteDiagnostics
import skillbill.infrastructure.sqlite.core.databaseAccessError
import skillbill.infrastructure.sqlite.core.inDatabaseTransaction
import skillbill.infrastructure.sqlite.core.requireResolvedEnvironmentContext
import skillbill.model.EnvironmentContext
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.ports.persistence.UnitOfWork
import java.nio.file.Files
import java.nio.file.Path
import java.sql.SQLException
import java.time.Clock

@Inject
class SQLiteDatabaseSessionFactory(
  context: EnvironmentContext,
  private val clock: Clock,
  private val diagnostics: RuntimeDiagnostics,
) : DatabaseSessionFactory {
  private val resolvedContext = requireResolvedEnvironmentContext(context)
  private val resolvedPath by lazy {
    DatabaseRuntime.resolveDbPath(
      cliValue = resolvedContext.dbPathOverride,
      environment = resolvedContext.environment,
      userHome = resolvedContext.userHome,
    )
  }

  override fun resolveDbPath(): Path = resolvedPath

  override fun databaseExists(): Boolean = Files.exists(resolveDbPath())

  override fun <T> read(block: (UnitOfWork) -> T): T = DatabaseRuntime.openReadDbAt(resolveDbPath()).use { openDb ->
    openDb.connection.attachSqliteDiagnostics(diagnostics)
    try {
      runCatching {
        openDb.connection.inDatabaseTransaction(
          dbPath = openDb.dbPath,
          beginMode = DatabaseTransactionBeginMode.DEFERRED,
          operation = DatabaseAccessOperation.READ,
          diagnostics = diagnostics,
        ) {
          block(unitOfWork(openDb))
        }
      }.getOrElse { error -> throwReadFailure(openDb.dbPath, error) }
    } finally {
      openDb.connection.detachSqliteDiagnostics()
    }
  }

  override fun <T> readIfPresent(block: (UnitOfWork) -> T): T? =
    DatabaseRuntime.openReadDbIfPresentAt(resolveDbPath())?.use { openDb ->
      openDb.connection.attachSqliteDiagnostics(diagnostics)
      try {
        runCatching {
          openDb.connection.inDatabaseTransaction(
            dbPath = openDb.dbPath,
            beginMode = DatabaseTransactionBeginMode.DEFERRED,
            operation = DatabaseAccessOperation.READ,
            diagnostics = diagnostics,
          ) {
            block(unitOfWork(openDb))
          }
        }.getOrElse { error -> throwReadFailure(openDb.dbPath, error) }
      } finally {
        openDb.connection.detachSqliteDiagnostics()
      }
    }

  override fun <T> selfManagedWrite(block: (UnitOfWork) -> T): T = withWriteDatabase { openDb ->
    block(unitOfWork(openDb))
  }

  override fun <T> transaction(block: (UnitOfWork) -> T): T = withWriteDatabase { openDb ->
    openDb.connection.inDatabaseTransaction(
      dbPath = openDb.dbPath,
      beginMode = DatabaseTransactionBeginMode.IMMEDIATE,
      operation = DatabaseAccessOperation.WRITE,
      diagnostics = diagnostics,
    ) {
      block(unitOfWork(openDb))
    }
  }

  private fun unitOfWork(openDb: OpenDatabase): SQLiteUnitOfWork =
    SQLiteUnitOfWork(openDb.connection, openDb.dbPath, clock, diagnostics)

  private fun <T> withWriteDatabase(block: (OpenDatabase) -> T): T {
    val dbPath = resolveDbPath()
    DatabaseRuntime.ensureWriteReady(dbPath)
    return DatabaseRuntime.openWriteDbAt(dbPath).use { openDb ->
      openDb.connection.attachSqliteDiagnostics(diagnostics)
      try {
        block(openDb)
      } finally {
        openDb.connection.detachSqliteDiagnostics()
      }
    }
  }

}

private fun throwReadFailure(dbPath: Path, error: Throwable): Nothing {
  if (error is SQLException) {
    throw databaseAccessError(dbPath, DatabaseAccessOperation.READ, error)
  }
  throw error
}
