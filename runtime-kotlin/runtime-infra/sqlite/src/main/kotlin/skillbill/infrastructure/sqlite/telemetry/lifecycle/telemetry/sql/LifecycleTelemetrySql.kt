package skillbill.infrastructure.sqlite.telemetry.lifecycle.telemetry.sql
import kotlinx.serialization.json.JsonElement
import skillbill.contracts.JsonCodec
import skillbill.contracts.telemetry.SqliteLifecycleTelemetryMaterializationPayloadKeys
import skillbill.infrastructure.sqlite.core.ops.bindAll
import java.sql.Connection
import java.sql.ResultSet

internal fun Boolean?.toSqlInt(): Int = if (this == true) 1 else 0

internal fun listJson(items: List<Any?>): String = JsonCodec.mapToJsonString(
  mapOf(SqliteLifecycleTelemetryMaterializationPayloadKeys.ITEMS to items),
).itemsArrayJson()

internal fun rowExists(connection: Connection, tableName: String, sessionId: String): Boolean =
  connection.prepareStatement("SELECT 1 FROM $tableName WHERE session_id = ?").use { statement ->
    statement.bindAll(sessionId)
    statement.executeQuery().use(ResultSet::next)
  }

internal fun lifecycleRow(connection: Connection, tableName: String, sessionId: String): Map<String, Any?>? =
  connection.prepareStatement("SELECT * FROM $tableName WHERE session_id = ?").use { statement ->
    statement.bindAll(sessionId)
    statement.executeQuery().use { resultSet -> if (resultSet.next()) resultSet.toMap() else null }
  }

internal fun markLifecycleEmitted(connection: Connection, tableName: String, columnName: String, sessionId: String) {
  connection.prepareStatement(
    """
    UPDATE $tableName
    SET $columnName = CURRENT_TIMESTAMP
    WHERE session_id = ?
    """.trimIndent(),
  ).use { statement ->
    statement.bindAll(sessionId)
    statement.executeUpdate()
  }
}

private fun String.itemsArrayJson(): String = JsonCodec.parseObjectOrNull(this)
  ?.get(SqliteLifecycleTelemetryMaterializationPayloadKeys.ITEMS)
  ?.let { JsonCodec.json.encodeToString(JsonElement.serializer(), it) }
  ?: "[]"

private fun ResultSet.toMap(): Map<String, Any?> {
  val metadata = metaData
  return buildMap {
    for (index in 1..metadata.columnCount) {
      put(metadata.getColumnName(index), getObject(index))
    }
  }
}
