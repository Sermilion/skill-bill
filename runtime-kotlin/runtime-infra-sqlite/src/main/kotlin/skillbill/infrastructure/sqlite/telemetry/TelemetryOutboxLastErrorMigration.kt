package skillbill.infrastructure.sqlite.telemetry

import java.sql.Connection

internal object TelemetryOutboxLastErrorMigration {
  fun apply(connection: Connection) {
    if (!needsMigration(connection)) return

    connection.createStatement().use { statement ->
      statement.execute("ALTER TABLE telemetry_outbox RENAME TO telemetry_outbox_legacy")
      statement.execute(
        """
        CREATE TABLE telemetry_outbox (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          event_name TEXT NOT NULL,
          payload_json TEXT NOT NULL,
          created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
          synced_at TEXT,
          last_error TEXT
        )
        """.trimIndent(),
      )
      statement.execute(
        """
        INSERT INTO telemetry_outbox (id, event_name, payload_json, created_at, synced_at, last_error)
        SELECT id, event_name, payload_json, created_at, synced_at,
               CASE WHEN last_error = '' THEN NULL ELSE last_error END
        FROM telemetry_outbox_legacy
        """.trimIndent(),
      )
      statement.execute("DROP TABLE telemetry_outbox_legacy")
      statement.execute(
        """
        CREATE INDEX IF NOT EXISTS idx_telemetry_outbox_pending
          ON telemetry_outbox(synced_at, id)
        """.trimIndent(),
      )
    }
  }

  private fun needsMigration(connection: Connection): Boolean = connection.prepareStatement(
    "SELECT \"notnull\" FROM pragma_table_info('telemetry_outbox') WHERE name = 'last_error'",
  ).use { statement ->
    statement.executeQuery().use { resultSet ->
      resultSet.next() && resultSet.getInt("notnull") != 0
    }
  }
}
