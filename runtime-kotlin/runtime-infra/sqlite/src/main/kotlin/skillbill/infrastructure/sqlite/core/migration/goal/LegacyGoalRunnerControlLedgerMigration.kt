package skillbill.infrastructure.sqlite.core.migration.goal
import skillbill.goalrunner.GOAL_OUT_OF_BAND_ACCEPTANCE_ARTIFACT_KEY
import skillbill.goalrunner.GOAL_REVIEW_POLICY_ARTIFACT_KEY
import skillbill.infrastructure.sqlite.core.ops.recordMigrationNormalization
import skillbill.infrastructure.sqlite.core.ops.sqliteDiagnostics
import skillbill.infrastructure.sqlite.goalrunner.control.outOfBandAcceptancesFromLegacyArtifacts
import skillbill.infrastructure.sqlite.goalrunner.control.reviewPolicyFromLegacyArtifacts
import skillbill.infrastructure.sqlite.workflow.goalrunner.runner.GoalRunnerControlStore
import skillbill.infrastructure.sqlite.workflow.workflow.toFeatureTaskWorkflowStateRecord
import skillbill.ports.workflow.model.toSnapshot
import java.sql.Connection

internal fun applyLegacyGoalRunnerControlLedgerMigration(connection: Connection) {
  val store = GoalRunnerControlStore(connection)
  connection.prepareStatement(
    """
    SELECT *
    FROM feature_task_workflows
    WHERE mode = 'runtime'
    """.trimIndent(),
  ).use { statement ->
    statement.executeQuery().use { rows ->
      while (rows.next()) {
        val workflow = rows.toFeatureTaskWorkflowStateRecord()
        val workflowId = workflow.workflowId
        val artifacts = workflow.toSnapshot().artifacts
        val movedKeys = mutableListOf<String>()
        if (store.reviewPolicy(workflowId) == null) {
          reviewPolicyFromLegacyArtifacts(artifacts)?.let { policy ->
            store.persistReviewPolicy(workflowId, policy)
            movedKeys += GOAL_REVIEW_POLICY_ARTIFACT_KEY
          }
        }
        val durableAcceptances = store.outOfBandAcceptances(workflowId)
        val legacyAcceptances =
          outOfBandAcceptancesFromLegacyArtifacts(artifacts)
            .filterKeys { subtaskId -> subtaskId !in durableAcceptances }
        if (legacyAcceptances.isNotEmpty()) {
          legacyAcceptances.values.forEach { acceptance ->
            store.persistOutOfBandAcceptance(workflowId, acceptance)
          }
          movedKeys += GOAL_OUT_OF_BAND_ACCEPTANCE_ARTIFACT_KEY
        }
        if (movedKeys.isNotEmpty()) {
          connection.sqliteDiagnostics().recordMigrationNormalization(
            seam = "goal_runner_controls.legacy_artifacts",
            parentWorkflowId = workflowId,
            movedArtifactKeys = movedKeys,
          )
        }
      }
    }
  }
}
