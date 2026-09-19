package skillbill.infrastructure.sqlite.workflow.featuretask
import skillbill.infrastructure.sqlite.workflow.goalrunner.child.connection
import skillbill.infrastructure.sqlite.workflow.goalrunner.planning.connection
import skillbill.infrastructure.sqlite.workflow.goalrunner.runner.connection
import skillbill.infrastructure.sqlite.workflow.goalrunner.shared.connection
import skillbill.infrastructure.sqlite.workflow.goalrunner.subtask.connection
import skillbill.infrastructure.sqlite.workflow.workflow.connection
import skillbill.infrastructure.sqlite.workflow.workflow.statement
import java.sql.Connection

internal object FeatureTaskPhaseSettlementsMigration {
  fun apply(connection: Connection) {
    connection.createStatement().use { statement ->
      statement.execute(
        """
        CREATE TABLE IF NOT EXISTS feature_task_phase_settlements (
          workflow_id TEXT NOT NULL,
          phase_id TEXT NOT NULL,
          attempt INTEGER NOT NULL CHECK (attempt > 0),
          kind TEXT NOT NULL,
          envelope_json TEXT NOT NULL,
          recorded_at TEXT NOT NULL,
          PRIMARY KEY (workflow_id, phase_id, attempt)
        )
        """.trimIndent(),
      )
      statement.execute(
        """
        CREATE INDEX IF NOT EXISTS idx_feature_task_phase_settlements_selector
        ON feature_task_phase_settlements(workflow_id, phase_id, attempt)
        """.trimIndent(),
      )
    }
  }
}
