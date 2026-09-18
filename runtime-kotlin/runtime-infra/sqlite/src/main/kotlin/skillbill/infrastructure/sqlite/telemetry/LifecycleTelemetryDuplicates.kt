package skillbill.infrastructure.sqlite.telemetry

import skillbill.contracts.telemetry.GoalTelemetryPayloadKeys
import skillbill.infrastructure.sqlite.core.bindAll
import java.sql.Connection

internal fun lifecycleAlreadyFinished(connection: Connection, tableName: String, sessionId: String): Boolean =
  connection.prepareStatement("SELECT finished_at FROM $tableName WHERE session_id = ?").use { statement ->
    statement.bindAll(sessionId)
    statement.executeQuery().use { resultSet ->
      resultSet.next() && !resultSet.getString(GoalTelemetryPayloadKeys.FINISHED_AT).isNullOrBlank()
    }
  }

internal fun incrementDuplicateTerminalFinishedEvents(connection: Connection, tableName: String, sessionId: String) {
  connection.prepareStatement(
    """
    UPDATE $tableName
    SET duplicate_terminal_finished_events = duplicate_terminal_finished_events + 1
    WHERE session_id = ?
    """.trimIndent(),
  ).use { statement ->
    statement.bindAll(sessionId)
    statement.executeUpdate()
  }
}

internal fun staleFinishedAlreadyEmitted(
  connection: Connection,
  tableName: String,
  terminalColumn: String,
  sessionId: String,
): Boolean = connection.prepareStatement(
  """
    SELECT $terminalColumn, finished_event_emitted_at
    FROM $tableName
    WHERE session_id = ?
  """.trimIndent(),
).use { statement ->
  statement.bindAll(sessionId)
  statement.executeQuery().use { resultSet ->
    resultSet.next() &&
      resultSet.getString(terminalColumn) == "stale" &&
      !resultSet.getString("finished_event_emitted_at").isNullOrBlank()
  }
}

internal fun terminalEventAlreadyEmitted(connection: Connection, tableName: String, sessionId: String): Boolean =
  connection.prepareStatement(
    """
    SELECT finished_event_emitted_at
    FROM $tableName
    WHERE session_id = ?
    """.trimIndent(),
  ).use { statement ->
    statement.bindAll(sessionId)
    statement.executeQuery().use { resultSet ->
      resultSet.next() && !resultSet.getString("finished_event_emitted_at").isNullOrBlank()
    }
  }
