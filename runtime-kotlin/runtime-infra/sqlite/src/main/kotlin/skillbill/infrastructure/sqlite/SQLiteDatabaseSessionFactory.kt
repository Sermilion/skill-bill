package skillbill.infrastructure.sqlite

import me.tatarka.inject.annotations.Inject
import skillbill.error.DatabaseAccessOperation
import skillbill.infrastructure.sqlite.core.DatabaseRuntime
import skillbill.infrastructure.sqlite.core.OpenDatabase
import skillbill.infrastructure.sqlite.core.databaseAccessError
import skillbill.infrastructure.sqlite.core.rollbackAfterFailedTransaction
import skillbill.model.EnvironmentContext
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.ports.persistence.UnitOfWork
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.SQLException

@Inject
class SQLiteDatabaseSessionFactory(
  private val context: EnvironmentContext,
) : DatabaseSessionFactory {
  private val resolvedContext = context.withProcessDefaults()
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
    runCatching {
      openDb.connection.inReadTransaction(openDb.dbPath) {
        block(SQLiteUnitOfWork(openDb.connection, openDb.dbPath))
      }
    }.getOrElse { error -> throwReadFailure(openDb.dbPath, error) }
  }

  override fun <T> readIfPresent(block: (UnitOfWork) -> T): T? =
    DatabaseRuntime.openReadDbIfPresentAt(resolveDbPath())?.use { openDb ->
      runCatching {
        openDb.connection.inReadTransaction(openDb.dbPath) {
          block(SQLiteUnitOfWork(openDb.connection, openDb.dbPath))
        }
      }.getOrElse { error -> throwReadFailure(openDb.dbPath, error) }
    }

  override fun <T> selfManagedWrite(block: (UnitOfWork) -> T): T = withWriteDatabase { openDb ->
    block(SQLiteUnitOfWork(openDb.connection, openDb.dbPath))
  }

  override fun <T> transaction(block: (UnitOfWork) -> T): T = withWriteDatabase { openDb ->
    openDb.connection.inTransaction(openDb.dbPath) {
      block(SQLiteUnitOfWork(openDb.connection, openDb.dbPath))
    }
  }

  private fun <T> withWriteDatabase(block: (OpenDatabase) -> T): T {
    val dbPath = resolveDbPath()
    DatabaseRuntime.ensureWriteReady(dbPath)
    return DatabaseRuntime.openWriteDbAt(dbPath).use(block)
  }
}

private fun EnvironmentContext.withProcessDefaults(): EnvironmentContext {
  val withUserHome =
    if (userHome == EnvironmentContext.UnspecifiedUserHome) {
      copy(userHome = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize())
    } else {
      copy(userHome = userHome.toAbsolutePath().normalize())
    }
  return if (withUserHome.environment === EnvironmentContext.UnspecifiedEnvironment) {
    withUserHome.copy(environment = System.getenv())
  } else {
    withUserHome
  }
}

private fun <T> Connection.inTransaction(dbPath: Path, block: () -> T): T {
  typedStatement(dbPath, "BEGIN IMMEDIATE", DatabaseAccessOperation.OPEN)
  var committed = false
  var primaryFailure: Throwable? = null
  return try {
    runCatching {
      val result = block()
      typedStatement(dbPath, "COMMIT", DatabaseAccessOperation.OPEN)
      committed = true
      result
    }.onFailure { primaryFailure = it }.getOrThrow()
  } finally {
    if (!committed) {
      rollbackAfterFailedTransaction(primaryFailure)
    }
  }
}

private fun <T> Connection.inReadTransaction(dbPath: Path, block: () -> T): T {
  typedStatement(dbPath, "BEGIN DEFERRED", DatabaseAccessOperation.READ)
  var committed = false
  var primaryFailure: Throwable? = null
  return try {
    runCatching {
      val result = block()
      typedStatement(dbPath, "COMMIT", DatabaseAccessOperation.READ)
      committed = true
      result
    }.onFailure { primaryFailure = it }.getOrThrow()
  } finally {
    if (!committed) {
      rollbackAfterFailedTransaction(primaryFailure)
    }
  }
}

private fun Connection.typedStatement(dbPath: Path, sql: String, operation: DatabaseAccessOperation) {
  runCatching {
    createStatement().use { it.execute(sql) }
  }.getOrElse { error -> throwTypedStatementFailure(dbPath, operation, error) }
}

private fun throwReadFailure(dbPath: Path, error: Throwable): Nothing {
  if (error is SQLException) {
    throw databaseAccessError(dbPath, DatabaseAccessOperation.READ, error)
  }
  throw error
}

private fun throwTypedStatementFailure(dbPath: Path, operation: DatabaseAccessOperation, error: Throwable): Nothing {
  if (error is SQLException) {
    throw databaseAccessError(dbPath, operation, error)
  }
  throw error
}
