package skillbill.db

import java.sql.Connection

internal object ParallelReviewTelemetryMigration {
  fun apply(connection: Connection) {
    COLUMNS.forEach { (column, definition) ->
      connection.createStatement().use { statement ->
        statement.execute("ALTER TABLE feature_task_runtime_sessions ADD COLUMN $column $definition")
      }
    }
  }

  private val COLUMNS: List<Pair<String, String>> = listOf(
    "parallel_review_requested" to "INTEGER NOT NULL DEFAULT 0",
    "default_review_agent_id" to "TEXT NOT NULL DEFAULT ''",
    "alternative_review_agent_id" to "TEXT NOT NULL DEFAULT ''",
    "review_lane_count" to "INTEGER NOT NULL DEFAULT 1",
    "review_lane_statuses" to "TEXT NOT NULL DEFAULT '[]'",
    "merged_review_finding_count" to "INTEGER NOT NULL DEFAULT 0",
    "accepted_review_finding_count" to "INTEGER NOT NULL DEFAULT 0",
    "rejected_review_finding_count" to "INTEGER NOT NULL DEFAULT 0",
    "unresolved_review_finding_count" to "INTEGER NOT NULL DEFAULT 0",
  )
}
