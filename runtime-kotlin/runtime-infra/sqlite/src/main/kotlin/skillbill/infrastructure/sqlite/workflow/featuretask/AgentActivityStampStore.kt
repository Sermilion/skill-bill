package skillbill.infrastructure.sqlite.workflow.featuretask
import skillbill.idestatus.model.AgentActivityLabel
import skillbill.idestatus.model.AgentActivityStamp
import skillbill.infrastructure.sqlite.core.ops.bindAll
import skillbill.ports.idestatus.AgentActivityStampRepository
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.time.format.DateTimeParseException

internal class AgentActivityStampStore(
  private val connection: Connection,
) : AgentActivityStampRepository {
  override fun record(
    workflowId: String,
    stamp: AgentActivityStamp,
  ) {
    require(workflowId.isNotBlank()) { "workflowId is required." }
    val existing = read(workflowId)
    if (existing != null && !stamp.recordedAt.isAfter(existing.recordedAt)) return
    connection.prepareStatement(
      """
      INSERT INTO agent_activity_stamps (workflow_id, recorded_at, label)
      VALUES (?, ?, ?)
      ON CONFLICT(workflow_id) DO UPDATE SET
        recorded_at = excluded.recorded_at,
        label = excluded.label
      WHERE (
        CASE
          WHEN instr(excluded.recorded_at, '.') = 0
            THEN substr(excluded.recorded_at, 1, length(excluded.recorded_at) - 1) ||
              '.000000000Z'
          ELSE substr(excluded.recorded_at, 1, instr(excluded.recorded_at, '.')) ||
            printf(
              '%09d',
              CAST(
                substr(
                  excluded.recorded_at,
                  instr(excluded.recorded_at, '.') + 1,
                  length(excluded.recorded_at) - instr(excluded.recorded_at, '.') - 1
                ) AS INTEGER
              )
            ) || 'Z'
        END
      ) > (
        CASE
          WHEN instr(agent_activity_stamps.recorded_at, '.') = 0
            THEN substr(agent_activity_stamps.recorded_at, 1, length(agent_activity_stamps.recorded_at) - 1) ||
              '.000000000Z'
          ELSE substr(agent_activity_stamps.recorded_at, 1, instr(agent_activity_stamps.recorded_at, '.')) ||
            printf(
              '%09d',
              CAST(
                substr(
                  agent_activity_stamps.recorded_at,
                  instr(agent_activity_stamps.recorded_at, '.') + 1,
                  length(agent_activity_stamps.recorded_at) -
                    instr(agent_activity_stamps.recorded_at, '.') - 1
                ) AS INTEGER
              )
            ) || 'Z'
        END
      )
      """.trimIndent(),
    ).use { statement ->
      statement.bindAll(workflowId, stamp.recordedAt.toString(), stamp.label.wireValue)
      statement.executeUpdate()
    }
  }

  override fun read(workflowId: String): AgentActivityStamp? {
    if (workflowId.isBlank()) return null
    return connection.prepareStatement(
      """
      SELECT recorded_at, label
      FROM agent_activity_stamps
      WHERE workflow_id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.bindAll(workflowId)
      statement.executeQuery().use(::stampFromResultSet)
    }
  }

  private fun stampFromResultSet(resultSet: ResultSet): AgentActivityStamp? {
    if (!resultSet.next()) return null
    val recordedAt = parseInstant(resultSet.getString("recorded_at")) ?: return null
    val label = AgentActivityLabel.fromWire(resultSet.getString("label")) ?: return null
    return AgentActivityStamp(recordedAt = recordedAt, label = label)
  }

  private fun parseInstant(raw: String?): Instant? {
    if (raw.isNullOrBlank()) return null
    return try {
      Instant.parse(raw)
    } catch (_: DateTimeParseException) {
      null
    }
  }
}
