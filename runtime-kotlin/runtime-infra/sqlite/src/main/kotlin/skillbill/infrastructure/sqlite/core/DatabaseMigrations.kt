package skillbill.infrastructure.sqlite.core

import skillbill.error.DatabaseAccessOperation
import skillbill.ports.diagnostics.RuntimeDiagnostics
import java.sql.Connection

internal object DatabaseMigrations {
  val migrations: List<DatabaseMigration> =
    (databaseMigrationsEarly + databaseMigrationsLate).also(::requireDeterministicMigrations)

  fun apply(connection: Connection, diagnostics: RuntimeDiagnostics = InternalSqliteDiagnostics) {
    val ledger = MigrationLedger.readState(connection)
    if (!ledger.hasPendingWork(migrations.map { migration -> migration.name })) return
    val dbPath = connection.databasePath()

    connection.inDatabaseTransaction(
      dbPath = dbPath,
      beginMode = DatabaseTransactionBeginMode.IMMEDIATE,
      operation = DatabaseAccessOperation.OPEN,
      diagnostics = diagnostics,
    ) {
      MigrationLedger.ensureNameKeyed(this)
      val appliedNames = MigrationLedger.appliedNames(this)
      migrations
        .filterNot { migration -> migration.name in appliedNames }
        .forEach { migration ->
          migration.apply(this)
          MigrationLedger.record(this, migration)
        }
    }
  }

  private fun requireDeterministicMigrations(migrations: List<DatabaseMigration>) {
    val versions = migrations.map { migration -> migration.version }
    val names = migrations.map { migration -> migration.name }

    require(versions == versions.sorted()) { "Database migrations must be ordered by version." }
    require(versions.toSet().size == versions.size) { "Database migration versions must be unique." }
    require(names.toSet().size == names.size) { "Database migration names must be unique." }
  }
}
