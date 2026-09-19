package skillbill.infrastructure.sqlite.core.migration.column
import skillbill.infrastructure.sqlite.core.migration.attribution.ReviewAttributionBackfillMigration
import skillbill.infrastructure.sqlite.core.migration.attribution.backfillExecutionModes
import skillbill.infrastructure.sqlite.core.migration.evidence.rekeyDiagnosticEvidenceByRepairTurn
import skillbill.infrastructure.sqlite.core.ops.bindAll
import skillbill.infrastructure.sqlite.core.ops.tableName
import skillbill.infrastructure.sqlite.core.schema.DatabaseReviewColumnMigrations
import skillbill.infrastructure.sqlite.core.schema.connection
import skillbill.infrastructure.sqlite.telemetry.outbox.TelemetryOutboxDeliveryIdentityMigration
import java.sql.Connection

internal object DatabaseColumnMigrations {
  fun apply(connection: Connection) {
    DatabaseColumnMigrationsEnsure.ensureFeatureVerifyWorkflowColumns(connection)
    DatabaseColumnMigrationsEnsure.ensureReviewRunColumns(connection)
    DatabaseReviewColumnMigrations.apply(connection)
    ReviewAttributionBackfillMigration.backfillExecutionModes(connection)
    DatabaseColumnMigrationsEnsure.ensureFeatureImplementSessionColumns(connection)
    DatabaseColumnMigrationsEnsure.ensureFeatureVerifySessionColumns(connection)
    DatabaseColumnMigrationsEnsure.ensureQualityCheckSessionColumns(connection)
    DatabaseColumnMigrationsEnsure.ensureFeatureTaskRuntimeSessionColumns(connection)
    DatabaseColumnMigrationsEnsure.ensureColumn(connection, "feature_task_workflows", "interruption_reason", "TEXT")
    DatabaseColumnMigrationsEnsure.ensureColumn(connection, "telemetry_outbox", "skill_bill_version", "TEXT")
    TelemetryOutboxDeliveryIdentityMigration.ensureColumns(connection)
    DatabaseColumnMigrationsConditional.apply(connection)
    DatabaseColumnMigrationsWorkListRecovery.ensureReconciliationIndexes(connection)
  }

  fun healDiagnosticEvidenceKeys(connection: Connection) {
    rekeyDiagnosticEvidenceByRepairTurn(connection)
  }

  fun applyWorkListMetadata(connection: Connection) {
    DatabaseColumnMigrationsWorkList.applyWorkListMetadata(connection)
  }

  fun healWorkListMetadata(connection: Connection) {
    DatabaseColumnMigrationsWorkList.healWorkListMetadata(connection)
  }

  fun recoverWorkListIssueKeys(connection: Connection) {
    DatabaseColumnMigrationsWorkList.recoverWorkListIssueKeys(connection)
  }

  internal fun reviewRunsTableExists(connection: Connection): Boolean = tableExists(connection, "review_runs")

  internal fun reviewRunColumnNames(connection: Connection): Set<String> =
    DatabaseColumnMigrationsEnsure.tableColumnNames(connection, "review_runs")

  internal fun tableExists(connection: Connection, tableName: String): Boolean = connection.prepareStatement(
    "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
  ).use { statement ->
    statement.bindAll(tableName)
    statement.executeQuery().use { resultSet -> resultSet.next() }
  }

  internal fun ensureColumn(
    connection: Connection,
    tableName: String,
    columnName: String,
    definition: String,
  ): Boolean = DatabaseColumnMigrationsEnsure.ensureColumn(connection, tableName, columnName, definition)

  internal fun ensureReviewRunColumns(connection: Connection) {
    DatabaseColumnMigrationsEnsure.ensureReviewRunColumns(connection)
  }
}
