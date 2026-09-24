package skillbill.infrastructure.sqlite.goalrunner.goal

import skillbill.infrastructure.sqlite.core.schema.DatabaseRuntime
import skillbill.infrastructure.sqlite.goalChildIdentity
import skillbill.infrastructure.sqlite.goalChildWorkflow
import skillbill.infrastructure.sqlite.goalrunner.manifest.goalRunnerPurgePersistence
import skillbill.infrastructure.sqlite.sqliteDatabaseSessionFactory
import skillbill.infrastructure.sqlite.testWorkflowSnapshotValidator
import skillbill.infrastructure.sqlite.workflow.workflow.WorkflowStateStore
import skillbill.infrastructure.sqlite.workflowRow
import skillbill.workflow.model.FeatureTaskRouteScope
import skillbill.workflow.model.FeatureTaskWorkflowMode
import java.nio.file.Files
import java.sql.Connection
import java.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private data class GoalPurgeFixture(
  val parentId: String,
  val childOne: String,
  val childTwo: String,
  val standalone: String,
)

class GoalRunnerPurgePersistenceTest {
  @Test
  fun `purge removes parent goal children and satellites but keeps standalone sibling and telemetry outbox`() {
    val tempDir = Files.createTempDirectory("goal-purge")
    val dbPath = tempDir.resolve("metrics.db")
    DatabaseRuntime.ensureDatabase(dbPath).use { connection ->
      val store = WorkflowStateStore(connection, Clock.systemUTC(), testWorkflowSnapshotValidator)
      val fixture = seedGoalPurgeFixture(connection, store)
      val outboxBefore =
        connection.prepareStatement("SELECT COUNT(*) FROM telemetry_outbox").use { rows ->
          rows.executeQuery().use { it.getInt(1) }
        }
      val factory =
        sqliteDatabaseSessionFactory(userHome = tempDir, dbPathOverride = dbPath.toString(), environment = emptyMap())
      factory.transaction { unitOfWork ->
        goalRunnerPurgePersistence().purgeDecomposedGoal(unitOfWork, fixture.parentId)
      }
      assertPurgedGoalState(connection, store, fixture, outboxBefore)
    }
  }

  private fun seedGoalPurgeFixture(
    connection: Connection,
    store: WorkflowStateStore,
  ): GoalPurgeFixture {
    val parentId = "wftr-parent"
    val childOne =
      goalChildWorkflow("wftr-child-1", parentId).copy(
        artifactsJson = goalContinuationArtifacts(parentId, 1),
      )
    val childTwo =
      goalChildWorkflow("wftr-child-2", parentId).copy(
        artifactsJson = goalContinuationArtifacts(parentId, 2),
      )
    val standalone =
      goalChildWorkflow("wftr-standalone", parentId).copy(
        artifactsJson = goalContinuationArtifacts(parentId, 99),
      )
    store.saveFeatureTaskRuntimeWorkflow(
      workflowRow(parentId, "ftr-parent", "bill-feature-task", "plan", FeatureTaskWorkflowMode.RUNTIME).copy(
        issueKey = "SKILL-245",
        artifactsJson = """{"decomposition_runtime":{"issue_key":"SKILL-245"}}""",
      ),
    )
    listOf(childOne, childTwo, standalone).forEach(store::saveFeatureTaskRuntimeWorkflow)
    listOf(childOne, childTwo).forEach { row ->
      store.saveFeatureTaskExecutionIdentity(goalChildIdentity(row).copy(normalizedIssueKey = "SKILL-245"))
    }
    store.saveFeatureTaskExecutionIdentity(
      goalChildIdentity(standalone).copy(
        normalizedIssueKey = "SKILL-245",
        routeScope = FeatureTaskRouteScope.STANDALONE,
      ),
    )
    seedGoalPurgeSatellites(connection, parentId, childOne.workflowId, childTwo.workflowId)
    seedGoalPurgeControlRows(connection, parentId)
    seedTelemetryOutbox(connection)
    return GoalPurgeFixture(parentId, childOne.workflowId, childTwo.workflowId, standalone.workflowId)
  }

  private fun goalContinuationArtifacts(
    parentId: String,
    subtaskId: Int,
  ) = """{"goal_continuation":{"issue_key":"SKILL-245","subtask_id":$subtaskId,"parent_workflow_id":"$parentId"}}"""

  private fun assertPurgedGoalState(
    connection: Connection,
    store: WorkflowStateStore,
    fixture: GoalPurgeFixture,
    outboxBefore: Int,
  ) {
    assertNull(store.getFeatureTaskRuntimeWorkflow(fixture.parentId))
    assertNull(store.getFeatureTaskRuntimeWorkflow(fixture.childOne))
    assertNull(store.getFeatureTaskRuntimeWorkflow(fixture.childTwo))
    assertNotNull(store.getFeatureTaskRuntimeWorkflow(fixture.standalone))
    assertNotNull(store.getFeatureTaskExecutionIdentity(fixture.standalone))
    val workflowIds = listOf(fixture.parentId, fixture.childOne, fixture.childTwo)
    listOf(
      "feature_task_execution_identities",
      "feature_task_runtime_worker_leases",
      "goal_run_sessions",
      "goal_subtask_events",
      "feature_task_phase_settlements",
    ).forEach { table ->
      assertEquals(0, countByWorkflowIds(connection, table, workflowIds))
    }
    assertEquals(0, countByParentWorkflowId(connection, "goal_runner_controls", "parent_workflow_id", fixture.parentId))
    assertEquals(0, countByParentWorkflowId(connection, "goal_issue_progress", "parent_workflow_id", fixture.parentId))
    listOf("goal_planning_preparations", "goal_shared_preplans", "goal_subtask_plans").forEach { table ->
      assertEquals(0, countByParentWorkflowId(connection, table, "parent_goal_workflow_id", fixture.parentId))
    }
    val outboxAfter =
      connection.prepareStatement("SELECT COUNT(*) FROM telemetry_outbox").use { rows ->
        rows.executeQuery().use { it.getInt(1) }
      }
    assertEquals(outboxBefore, outboxAfter)
  }

  private fun seedGoalPurgeControlRows(
    connection: Connection,
    parentId: String,
  ) {
    connection.prepareStatement(
      """
      INSERT INTO goal_runner_controls (parent_workflow_id, control_state_json)
      VALUES (?, '{}')
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, parentId)
      statement.executeUpdate()
    }
    connection.prepareStatement(
      """
      INSERT INTO goal_issue_progress (parent_workflow_id, issue_key)
      VALUES (?, 'SKILL-245')
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, parentId)
      statement.executeUpdate()
    }
  }

  private fun seedTelemetryOutbox(connection: Connection) {
    connection.prepareStatement(
      """
      INSERT INTO telemetry_outbox (event_name, payload_json, synced_at, last_error)
      VALUES ('goal.purge.fixture', '{}', NULL, NULL)
      """.trimIndent(),
    ).use { statement -> statement.executeUpdate() }
  }

  private fun seedGoalPurgeSatellites(
    connection: Connection,
    parentId: String,
    childOne: String,
    childTwo: String,
  ) {
    seedGoalPurgePlanningSatellites(connection, parentId)
    seedGoalPurgeWorkflowSatellites(connection, listOf(parentId, childOne, childTwo))
  }

  private fun seedGoalPurgePlanningSatellites(
    connection: Connection,
    parentId: String,
  ) {
    connection.prepareStatement(
      """
      INSERT INTO goal_planning_preparations (
        parent_goal_workflow_id, normalized_issue_key, repository_identity, subtask_id,
        governed_sub_spec_path, contract_version, parent_spec_hash, sub_spec_hash, decomposition_manifest_hash,
        phase_output_contract_id, phase_output_contract_version, preplan_payload_json, plan_payload_json
      ) VALUES (?, 'SKILL-245', 'repo', 1, '.feature-specs/SKILL-245/subtask.md', '0.1', 'parent', 'subtask',
        'manifest', 'goal-plan', '0.2', '{}', '{}')
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, parentId)
      statement.executeUpdate()
    }
    connection.prepareStatement(
      """
      INSERT INTO goal_shared_preplans (
        parent_goal_workflow_id, normalized_issue_key, repository_identity, preparation_status,
        contract_version, parent_spec_hash, decomposition_manifest_hash, planning_contract_id,
        planning_contract_version, phase_output_contract_id, phase_output_contract_version,
        payload_sha256, preplan_payload_json
      ) VALUES (?, 'SKILL-245', 'repo', 'prepared', '0.2', 'parent', 'manifest', 'goal-plan',
        '0.2', 'goal-plan', '0.2', 'sha', '{}')
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, parentId)
      statement.executeUpdate()
    }
    connection.prepareStatement(
      """
      INSERT INTO goal_subtask_plans (
        parent_goal_workflow_id, normalized_issue_key, repository_identity, subtask_id, manifest_order,
        governed_sub_spec_path, sub_spec_hash, preparation_status, contract_version, parent_spec_hash,
        decomposition_manifest_hash, planning_contract_id, planning_contract_version,
        phase_output_contract_id, phase_output_contract_version, payload_sha256, plan_payload_json
      ) VALUES (?, 'SKILL-245', 'repo', 1, 0, '.feature-specs/SKILL-245/subtask.md', 'subtask',
        'prepared', '0.2', 'parent', 'manifest', 'goal-plan', '0.2', 'goal-plan', '0.2', 'sha', '{}')
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, parentId)
      statement.executeUpdate()
    }
  }

  private fun seedGoalPurgeWorkflowSatellites(
    connection: Connection,
    workflowIds: List<String>,
  ) {
    workflowIds.forEach { workflowId ->
      connection.prepareStatement(
        """
        INSERT INTO goal_run_sessions (workflow_id, issue_key, started_at)
        VALUES (?, 'SKILL-245', '2026-01-01T00:00:00Z')
        """.trimIndent(),
      ).use { statement ->
        statement.setString(1, workflowId)
        statement.executeUpdate()
      }
      connection.prepareStatement(
        """
        INSERT INTO goal_subtask_events (
          issue_key, workflow_id, subtask_id, status, started_at, finished_at, duration_ms, attempt_count
        ) VALUES ('SKILL-245', ?, 1, 'complete', '2026-01-01T00:00:00Z', '2026-01-01T00:00:01Z', 1000, 1)
        """.trimIndent(),
      ).use { statement ->
        statement.setString(1, workflowId)
        statement.executeUpdate()
      }
      connection.prepareStatement(
        """
        INSERT INTO feature_task_phase_settlements (
          workflow_id, phase_id, attempt, kind, envelope_json, recorded_at
        ) VALUES (?, 'plan', 1, 'completed', '{}', '2026-01-01T00:00:00Z')
        """.trimIndent(),
      ).use { statement ->
        statement.setString(1, workflowId)
        statement.executeUpdate()
      }
      connection.prepareStatement(
        """
        INSERT INTO feature_task_runtime_worker_leases (
          workflow_id, contract_version, generation, owner_token, host_identity, boot_identity,
          pid, process_birth_token, lease_state, heartbeat_at, expires_at, phase_id, phase_attempt
        ) VALUES (?, '0.1', 1, 'owner', 'host', 'boot', 1234, 'birth', 'active',
          '2026-01-01T00:00:00Z', '2000-01-01T00:00:00Z', 'plan', 1)
        """.trimIndent(),
      ).use { statement ->
        statement.setString(1, workflowId)
        statement.executeUpdate()
      }
    }
  }

  private fun countByWorkflowIds(
    connection: Connection,
    table: String,
    workflowIds: List<String>,
  ): Int {
    val placeholders = workflowIds.joinToString(", ") { "?" }
    return connection.prepareStatement(
      "SELECT COUNT(*) FROM $table WHERE workflow_id IN ($placeholders)",
    ).use { statement ->
      workflowIds.forEachIndexed { index, workflowId -> statement.setString(index + 1, workflowId) }
      statement.executeQuery().use { rows ->
        check(rows.next())
        rows.getInt(1)
      }
    }
  }

  private fun countByParentWorkflowId(
    connection: Connection,
    table: String,
    column: String,
    parentId: String,
  ): Int =
    connection.prepareStatement("SELECT COUNT(*) FROM $table WHERE $column = ?").use { statement ->
      statement.setString(1, parentId)
      statement.executeQuery().use { rows ->
        check(rows.next())
        rows.getInt(1)
      }
    }
}
