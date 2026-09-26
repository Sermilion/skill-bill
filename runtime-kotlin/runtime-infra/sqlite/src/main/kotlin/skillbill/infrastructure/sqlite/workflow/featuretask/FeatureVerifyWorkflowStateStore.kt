package skillbill.infrastructure.sqlite.workflow.featuretask

import skillbill.contracts.workflow.session.WorkflowContinueSessionSummary
import skillbill.infrastructure.sqlite.core.ops.bindAll
import skillbill.infrastructure.sqlite.core.schema.FEATURE_VERIFY_WORKFLOW_CONTRACT_VERSION
import skillbill.infrastructure.sqlite.workflow.getWorkflowRow
import skillbill.infrastructure.sqlite.workflow.getWorkflowRows
import skillbill.infrastructure.sqlite.workflow.listWorkflowRows
import skillbill.infrastructure.sqlite.workflow.upsertWorkflowRow
import skillbill.ports.workflow.WorkflowSnapshotValidator
import skillbill.ports.workflow.model.WorkflowStateRecord
import java.sql.Connection
import java.time.Clock

internal class FeatureVerifyWorkflowStateStore(
  private val connection: Connection,
  private val clock: Clock,
  private val workflowSnapshotValidator: WorkflowSnapshotValidator,
) {
  fun saveWorkflow(row: WorkflowStateRecord) {
    connection.upsertWorkflowRow(
      tableName = "feature_verify_workflows",
      row = row,
      defaultContractVersion = FEATURE_VERIFY_WORKFLOW_CONTRACT_VERSION,
      clock = clock,
      workflowSnapshotValidator = workflowSnapshotValidator,
    )
  }

  fun getWorkflow(workflowId: String): WorkflowStateRecord? =
    connection.getWorkflowRow("feature_verify_workflows", workflowId)

  fun getWorkflows(workflowIds: Set<String>): Map<String, WorkflowStateRecord> =
    connection.getWorkflowRows("feature_verify_workflows", workflowIds)

  fun listWorkflows(limit: Int): List<WorkflowStateRecord> =
    connection.listWorkflowRows("feature_verify_workflows", limit)

  fun getSessionSummary(sessionId: String): WorkflowContinueSessionSummary? =
    connection.prepareStatement(
      """
      SELECT
        session_id,
        acceptance_criteria_count,
        rollout_relevant,
        spec_summary
      FROM feature_verify_sessions
      WHERE session_id = ?
      """.trimIndent(),
    ).use { statement ->
      statement.bindAll(sessionId)
      statement.executeQuery().use { resultSet ->
        if (!resultSet.next()) {
          return null
        }
        WorkflowContinueSessionSummary(
          sessionId = resultSet.getString("session_id"),
          acceptanceCriteriaCount = resultSet.getInt("acceptance_criteria_count"),
          rolloutRelevant = resultSet.getInt("rollout_relevant") == 1,
          specSummary = resultSet.getString("spec_summary"),
        )
      }
    }
}
