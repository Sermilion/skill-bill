package skillbill.db

import java.sql.Connection

data class TelemetryOutboxRow(
  val id: Long,
  val eventName: String,
  val payloadJson: String,
  val createdAt: String,
  val syncedAt: String?,
  val lastError: String,
)

class TelemetryOutboxStore(
  private val connection: Connection,
) {
  fun enqueue(eventName: String, payloadJson: String): Long {
    connection.prepareStatement(
      """
      INSERT INTO telemetry_outbox (event_name, payload_json)
      VALUES (?, ?)
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, eventName)
      statement.setString(2, payloadJson)
      statement.executeUpdate()
    }
    return connection.createStatement().use { statement ->
      statement.executeQuery("SELECT last_insert_rowid()").use { resultSet ->
        resultSet.next()
        resultSet.getLong(1)
      }
    }
  }

  fun listPending(): List<TelemetryOutboxRow> = connection.prepareStatement(
    """
      SELECT id, event_name, payload_json, created_at, synced_at, last_error
      FROM telemetry_outbox
      WHERE synced_at IS NULL
      ORDER BY id
    """.trimIndent(),
  ).use { statement ->
    statement.executeQuery().use { resultSet ->
      buildList {
        while (resultSet.next()) {
          add(
            TelemetryOutboxRow(
              id = resultSet.getLong("id"),
              eventName = resultSet.getString("event_name"),
              payloadJson = resultSet.getString("payload_json"),
              createdAt = resultSet.getString("created_at"),
              syncedAt = resultSet.getString("synced_at"),
              lastError = resultSet.getString("last_error").orEmpty(),
            ),
          )
        }
      }
    }
  }

  fun pendingCount(): Int = connection.prepareStatement(
    """
      SELECT COUNT(*)
      FROM telemetry_outbox
      WHERE synced_at IS NULL
    """.trimIndent(),
  ).use { statement ->
    statement.executeQuery().use { resultSet ->
      resultSet.next()
      resultSet.getInt(1)
    }
  }

  fun markSynced(id: Long, syncedAt: String) {
    connection.prepareStatement(
      """
      UPDATE telemetry_outbox
      SET synced_at = ?, last_error = ''
      WHERE id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, syncedAt)
      statement.setLong(2, id)
      statement.executeUpdate()
    }
  }

  fun markFailed(id: Long, lastError: String) {
    connection.prepareStatement(
      """
      UPDATE telemetry_outbox
      SET last_error = ?
      WHERE id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, lastError)
      statement.setLong(2, id)
      statement.executeUpdate()
    }
  }
}
