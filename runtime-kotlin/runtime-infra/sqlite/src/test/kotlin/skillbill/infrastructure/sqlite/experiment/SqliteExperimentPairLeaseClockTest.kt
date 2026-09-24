package skillbill.infrastructure.sqlite.experiment

import org.junit.jupiter.api.Test
import skillbill.error.shellcontent.ExperimentIsolationCapabilityRefusalError
import skillbill.infrastructure.sqlite.core.schema.DatabaseRuntime
import java.nio.file.Files
import java.sql.Connection
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SqliteExperimentPairLeaseClockTest {
  @Test
  fun `lease liveness is judged against the injected clock, not wall time`() {
    withConnection { connection ->
      seedPairWithWorkflow(connection)
      storeAt(connection, LEASE_ISSUED_MILLIS).acquireLease(PAIR_ID, "owner-a", LEASE_ISSUED_MILLIS, LEASE_MILLIS)

      assertFailsWith<ExperimentIsolationCapabilityRefusalError> {
        storeAt(connection, LEASE_ISSUED_MILLIS + LEASE_MILLIS - 1).deletePairsForWorkflowIds(listOf(WORKFLOW_ID))
      }

      storeAt(connection, LEASE_ISSUED_MILLIS + LEASE_MILLIS).deletePairsForWorkflowIds(listOf(WORKFLOW_ID))
      assertNull(storeAt(connection, LEASE_ISSUED_MILLIS + LEASE_MILLIS).loadPairPayload(PAIR_ID))
    }
  }

  private fun storeAt(
    connection: Connection,
    epochMillis: Long,
  ): SqliteExperimentPairStore =
    SqliteExperimentPairStore(connection, Clock.fixed(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC))

  private fun seedPairWithWorkflow(connection: Connection) {
    connection.prepareStatement(
      """
      INSERT INTO experiment_pairs(
        pair_id, contract_version, execution_mode, selected_experiment_names_json, arm_order_json,
        random_seed, delivery_arm, pair_status, frozen_input_identity_json, delivery_status
      ) VALUES (?, '0.1', 'goal_pair', '{}', '{}', 'seed', 'control', 'running', '{}', 'deferred')
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, PAIR_ID)
      statement.executeUpdate()
    }
    connection.prepareStatement(
      """
      INSERT INTO experiment_arm_outcomes(pair_id, arm_id, workflow_id, terminal_status)
      VALUES (?, 'control', ?, 'running')
      """.trimIndent(),
    ).use { statement ->
      statement.setString(1, PAIR_ID)
      statement.setString(2, WORKFLOW_ID)
      statement.executeUpdate()
    }
  }

  private fun withConnection(block: (Connection) -> Unit) {
    val dbPath = Files.createTempDirectory("experiment-pair-lease-clock").resolve("metrics.db")
    DatabaseRuntime.ensureDatabase(dbPath).use(block)
  }

  private companion object {
    const val PAIR_ID = "pair-lease-clock"
    const val WORKFLOW_ID = "workflow-lease-clock"
    const val LEASE_ISSUED_MILLIS = 1_000L
    const val LEASE_MILLIS = 100L
  }
}
