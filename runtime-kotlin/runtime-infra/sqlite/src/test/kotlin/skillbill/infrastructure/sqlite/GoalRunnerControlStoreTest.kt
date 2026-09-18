package skillbill.infrastructure.sqlite

import skillbill.error.InvalidWorkflowStateSchemaError
import skillbill.goalrunner.model.GOAL_PAUSE_REASON_OPERATOR_STOP
import skillbill.goalrunner.model.GOAL_PAUSE_REASON_RUNNER_INTERRUPTED
import skillbill.goalrunner.model.GoalRunnerControlState
import skillbill.goalrunner.model.GoalRunnerExecutionLease
import skillbill.infrastructure.sqlite.core.DatabaseRuntime
import skillbill.infrastructure.sqlite.goalrunner.acquireExecutionLease
import skillbill.infrastructure.sqlite.goalrunner.executionLease
import skillbill.infrastructure.sqlite.goalrunner.heartbeatExecutionLease
import skillbill.infrastructure.sqlite.goalrunner.releaseExecutionLease
import skillbill.infrastructure.sqlite.goalrunner.releaseExecutionLeaseIfExpired
import skillbill.infrastructure.sqlite.workflow.GoalRunnerControlStore
import skillbill.infrastructure.sqlite.workflow.LEGACY_UNKNOWN_PAUSED_AT
import skillbill.ports.goalrunner.runner.model.GoalRunnerOutOfBandAcceptance
import skillbill.ports.goalrunner.runner.model.GoalRunnerReviewPolicy
import skillbill.review.context.model.CodeReviewExecutionMode
import java.nio.file.Files
import java.sql.Connection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoalRunnerControlStoreTest {
  @Test
  fun `review policy and operator acceptance remain durable outside workflow projection`() {
    val dbPath = Files.createTempDirectory("skillbill-goal-controls").resolve("metrics.db")

    DatabaseRuntime.ensureDatabase(dbPath).use { connection ->
      val store = GoalRunnerControlStore(connection)
      val policy = GoalRunnerReviewPolicy(CodeReviewExecutionMode.INLINE)
      val acceptance = GoalRunnerOutOfBandAcceptance(
        subtaskId = 2,
        commitSha = "abc123",
        reason = "work was completed on the feature branch",
        acceptedAt = "2026-08-01T10:00:00Z",
      )

      store.persistReviewPolicy("parent-1", policy)
      store.persistOutOfBandAcceptance("parent-1", acceptance)

      assertEquals(policy, store.reviewPolicy("parent-1"))
      assertEquals(mapOf(2 to acceptance), store.outOfBandAcceptances("parent-1"))
      assertEquals(GoalRunnerControlState(), store.controlState("parent-1"))
      connection.prepareStatement(
        "SELECT review_policy_json, out_of_band_acceptances_json " +
          "FROM goal_runner_controls WHERE parent_workflow_id = ?",
      ).use { statement ->
        statement.setString(1, "parent-1")
        statement.executeQuery().use { rows ->
          check(rows.next())
          check(rows.getString("review_policy_json").contains("code_review_mode"))
          check(rows.getString("out_of_band_acceptances_json").contains("commit_sha"))
        }
      }
    }
  }

  @Test
  fun `runner interrupted pause fields clear to defaults`() {
    val dbPath = Files.createTempDirectory("skillbill-goal-runner-interrupted-clear").resolve("metrics.db")

    DatabaseRuntime.ensureDatabase(dbPath).use { connection ->
      val store = GoalRunnerControlStore(connection)
      val interrupted = GoalRunnerControlState(
        pauseRequested = true,
        pauseConsumed = true,
        paused = true,
        pauseReason = GOAL_PAUSE_REASON_RUNNER_INTERRUPTED,
        pausedAt = "2026-08-02T09:59:00Z",
      )
      store.persistControlState("parent-interrupted", interrupted)
      val cleared = store.clearRunnerInterruptedPause("parent-interrupted")

      assertEquals(GoalRunnerControlState(), cleared)
      assertEquals(GoalRunnerControlState(), store.controlState("parent-interrupted"))
    }
  }

  @Test
  fun `operator stop pause rows remain untouched when only runner interrupted would be cleared`() {
    val dbPath = Files.createTempDirectory("skillbill-goal-operator-stop-preserved").resolve("metrics.db")

    DatabaseRuntime.ensureDatabase(dbPath).use { connection ->
      val store = GoalRunnerControlStore(connection)
      val operatorStop = GoalRunnerControlState(
        pauseRequested = true,
        pauseConsumed = true,
        paused = true,
        pauseReason = GOAL_PAUSE_REASON_OPERATOR_STOP,
        pausedAt = "2026-08-02T09:00:00Z",
      )
      store.persistControlState("parent-stop", operatorStop)

      assertEquals(operatorStop, store.clearRunnerInterruptedPause("parent-stop"))
      assertEquals(operatorStop, store.controlState("parent-stop"))
    }
  }

  @Test
  fun `missing control state is legacy compatible and malformed state fails loudly`() {
    val dbPath = Files.createTempDirectory("skillbill-goal-control-state").resolve("metrics.db")

    DatabaseRuntime.ensureDatabase(dbPath).use { connection ->
      val store = GoalRunnerControlStore(connection)
      assertEquals(GoalRunnerControlState(), store.controlState("missing-parent"))

      val state = GoalRunnerControlState(
        stopAfterSubtaskId = 2,
        pauseRequested = true,
        pauseConsumed = true,
        paused = true,
        pauseReason = "operator_request",
        pausedAt = "2026-08-02T09:00:00Z",
      )
      store.persistControlState("parent-1", state)
      assertEquals(state, store.controlState("parent-1"))

      connection.prepareStatement(
        "UPDATE goal_runner_controls SET control_state_json = ? WHERE parent_workflow_id = ?",
      ).use { statement ->
        statement.setString(1, "{\"paused\":true,\"unsupported\":true}")
        statement.setString(2, "parent-1")
        statement.executeUpdate()
      }
      assertFailsWith<InvalidWorkflowStateSchemaError> { store.controlState("parent-1") }
    }
  }

  @Test
  fun `malformed control-state JSON returns a typed schema error not IllegalArgumentException`() {
    val dbPath = Files.createTempDirectory("skillbill-goal-control-malformed-json").resolve("metrics.db")

    DatabaseRuntime.ensureDatabase(dbPath).use { connection ->
      val store = GoalRunnerControlStore(connection)
      store.persistControlState("parent-malformed", GoalRunnerControlState())
      writeRawControlState(connection, "parent-malformed", "{not valid json")

      assertFailsWith<InvalidWorkflowStateSchemaError> { store.controlState("parent-malformed") }
    }
  }

  @Test
  fun `control state survives a reopened database and duplicate writes remain stable`() {
    val dbPath = Files.createTempDirectory("skillbill-goal-control-restart").resolve("metrics.db")
    val state = GoalRunnerControlState(
      stopAfterSubtaskId = 4,
      pauseRequested = true,
      pauseConsumed = true,
      paused = true,
      pauseReason = "operator_request",
      pausedAt = "2026-08-02T09:00:00Z",
    )

    DatabaseRuntime.ensureDatabase(dbPath).use { connection ->
      val store = GoalRunnerControlStore(connection)
      assertEquals(state, store.persistControlState("parent-restart", state))
      assertEquals(state, store.persistControlState("parent-restart", state))
    }

    DatabaseRuntime.ensureDatabase(dbPath).use { connection ->
      assertEquals(state, GoalRunnerControlStore(connection).controlState("parent-restart"))
    }
  }

  @Test
  fun `a paused record written before paused_at existed decodes from the lease heartbeat`() {
    val dbPath = Files.createTempDirectory("skillbill-goal-legacy-paused").resolve("metrics.db")

    DatabaseRuntime.ensureDatabase(dbPath).use { connection ->
      val store = GoalRunnerControlStore(connection)
      store.persistControlState("parent-legacy", GoalRunnerControlState())
      writeRawControlState(
        connection,
        "parent-legacy",
        """
        {"paused":true,"pause_requested":true,"pause_consumed":true,"pause_reason":"operator_request",
         "execution_lease":{"generation":1,"owner_token":"owner-token-123456","host_identity":"host",
         "boot_identity":"boot","pid":42,"process_birth_token":"birth",
         "heartbeat_at":"2026-08-02T10:00:10Z","expires_at":"2026-08-02T10:00:40Z"}}
        """.trimIndent(),
      )

      val decoded = store.controlState("parent-legacy")
      assertTrue(decoded.paused)
      assertEquals("2026-08-02T10:00:10Z", decoded.pausedAt)
    }
  }

  @Test
  fun `a paused legacy record with no lease decodes to the unknown-time sentinel rather than failing`() {
    val dbPath = Files.createTempDirectory("skillbill-goal-legacy-sentinel").resolve("metrics.db")

    DatabaseRuntime.ensureDatabase(dbPath).use { connection ->
      val store = GoalRunnerControlStore(connection)
      store.persistControlState("parent-legacy", GoalRunnerControlState())
      writeRawControlState(
        connection,
        "parent-legacy",
        """{"paused":true,"pause_requested":true,"pause_consumed":true,"pause_reason":"operator_request"}""",
      )

      assertEquals(LEGACY_UNKNOWN_PAUSED_AT, store.controlState("parent-legacy").pausedAt)
    }
  }

  private fun writeRawControlState(connection: Connection, parentWorkflowId: String, json: String) {
    connection.prepareStatement(
      "UPDATE goal_runner_controls SET control_state_json = ? WHERE parent_workflow_id = ?",
    ).use { statement ->
      statement.setString(1, json)
      statement.setString(2, parentWorkflowId)
      statement.executeUpdate()
    }
  }

  @Test
  fun `parent execution lease survives a reopened database`() {
    val dbPath = Files.createTempDirectory("skillbill-goal-execution-lease").resolve("metrics.db")
    val lease = GoalRunnerExecutionLease(
      generation = 1,
      ownerToken = "owner-token-123456",
      hostIdentity = "host",
      bootIdentity = "boot",
      pid = 42,
      processBirthToken = "birth",
      heartbeatAt = "2026-08-02T10:00:00Z",
      expiresAt = "2026-08-02T10:00:30Z",
    )

    DatabaseRuntime.ensureDatabase(dbPath).use { connection ->
      val store = GoalRunnerControlStore(connection)
      assertTrue(store.acquireExecutionLease("parent-lease", lease))
      assertEquals(lease, store.executionLease("parent-lease"))
      assertTrue(
        store.heartbeatExecutionLease(
          "parent-lease",
          lease.copy(heartbeatAt = "2026-08-02T10:00:10Z", expiresAt = "2026-08-02T10:00:40Z"),
        ),
      )
    }

    DatabaseRuntime.ensureDatabase(dbPath).use { connection ->
      val store = GoalRunnerControlStore(connection)
      assertEquals("2026-08-02T10:00:40Z", store.executionLease("parent-lease")?.expiresAt)
      store.persistControlState(
        "parent-lease",
        GoalRunnerControlState(
          pauseRequested = true,
          pauseReason = "operator_request",
          executionLease = requireNotNull(store.executionLease("parent-lease")),
        ),
      )
      store.clearControlState("parent-lease")
      assertEquals(
        GoalRunnerControlState(
          executionLease = lease.copy(heartbeatAt = "2026-08-02T10:00:10Z", expiresAt = "2026-08-02T10:00:40Z"),
        ),
        store.controlState("parent-lease"),
      )
      assertTrue(store.releaseExecutionLease("parent-lease", lease.ownerToken, lease.generation))
      assertEquals(null, store.executionLease("parent-lease"))
    }
  }

  @Test
  fun `expired execution lease releases only at the expiry boundary with matching fencing`() {
    val dbPath = Files.createTempDirectory("skillbill-goal-expired-lease-release").resolve("metrics.db")
    val lease = GoalRunnerExecutionLease(
      generation = 3,
      ownerToken = "owner-token-expired",
      hostIdentity = "host",
      bootIdentity = "boot",
      pid = 42,
      processBirthToken = "birth",
      heartbeatAt = "2026-08-02T10:00:00Z",
      expiresAt = "2026-08-02T10:00:30Z",
    )

    DatabaseRuntime.ensureDatabase(dbPath).use { connection ->
      val store = GoalRunnerControlStore(connection)
      assertTrue(store.acquireExecutionLease("parent-expired", lease))
      assertFalse(
        store.releaseExecutionLeaseIfExpired(
          "parent-expired",
          lease.ownerToken,
          lease.generation,
          "2026-08-02T10:00:29Z",
        ),
      )
      assertFalse(
        store.releaseExecutionLeaseIfExpired(
          "parent-expired",
          "different-owner",
          lease.generation,
          "2026-08-02T10:00:30Z",
        ),
      )
      assertTrue(
        store.releaseExecutionLeaseIfExpired(
          "parent-expired",
          lease.ownerToken,
          lease.generation,
          "2026-08-02T10:00:30Z",
        ),
      )
      assertEquals(null, store.executionLease("parent-expired"))
    }
  }

  @Test
  fun `clearing control state preserves the accumulated execution clock`() {
    val dbPath = Files.createTempDirectory("skillbill-goal-clear-active-clock").resolve("metrics.db")
    val lease = GoalRunnerExecutionLease(
      generation = 1,
      ownerToken = "owner-token-123456",
      hostIdentity = "host",
      bootIdentity = "boot",
      pid = 42,
      processBirthToken = "birth",
      heartbeatAt = "2026-08-02T10:00:00Z",
      expiresAt = "2026-08-02T10:00:30Z",
    )

    DatabaseRuntime.ensureDatabase(dbPath).use { connection ->
      val store = GoalRunnerControlStore(connection)
      assertTrue(store.acquireExecutionLease("parent-clear", lease))
      assertTrue(store.heartbeatExecutionLease("parent-clear", lease.copy(heartbeatAt = "2026-08-02T10:00:10Z")))
      val paused = store.controlState("parent-clear")
      store.persistControlState(
        "parent-clear",
        paused.copy(
          pauseRequested = true,
          paused = true,
          pauseReason = "operator_request",
          pausedAt = "2026-08-02T10:00:10Z",
        ),
      )

      store.clearControlState("parent-clear")

      val cleared = store.controlState("parent-clear")
      assertEquals(false, cleared.paused)
      assertEquals(10_000, cleared.activeDurationMs)
      assertEquals("2026-08-02T10:00:10Z", cleared.activeDurationAsOf)
    }
  }

  @Test
  fun `the active execution clock caps an over-long heartbeat gap instead of counting the downtime`() {
    val dbPath = Files.createTempDirectory("skillbill-goal-active-clock").resolve("metrics.db")
    val lease = GoalRunnerExecutionLease(
      generation = 1,
      ownerToken = "owner-token-123456",
      hostIdentity = "host",
      bootIdentity = "boot",
      pid = 42,
      processBirthToken = "birth",
      heartbeatAt = "2026-08-07T18:22:00Z",
      expiresAt = "2026-08-07T18:22:30Z",
    )

    DatabaseRuntime.ensureDatabase(dbPath).use { connection ->
      val store = GoalRunnerControlStore(connection)
      assertTrue(store.acquireExecutionLease("parent-clock", lease))
      assertEquals(0, store.controlState("parent-clock").activeDurationMs)

      assertTrue(store.heartbeatExecutionLease("parent-clock", lease.copy(heartbeatAt = "2026-08-07T18:22:10Z")))
      assertTrue(store.heartbeatExecutionLease("parent-clock", lease.copy(heartbeatAt = "2026-08-07T18:22:20Z")))
      assertEquals(20_000, store.controlState("parent-clock").activeDurationMs)

      assertTrue(store.heartbeatExecutionLease("parent-clock", lease.copy(heartbeatAt = "2026-08-08T06:31:00Z")))
      assertEquals(40_000, store.controlState("parent-clock").activeDurationMs)

      assertTrue(store.heartbeatExecutionLease("parent-clock", lease.copy(heartbeatAt = "2026-08-08T06:31:10Z")))
      assertEquals(50_000, store.controlState("parent-clock").activeDurationMs)
    }
  }

  @Test
  fun `a heartbeat slightly past the limit is credited one interval rather than discarded`() {
    val dbPath = Files.createTempDirectory("skillbill-goal-late-heartbeat").resolve("metrics.db")
    val lease = GoalRunnerExecutionLease(
      generation = 1,
      ownerToken = "owner-token-123456",
      hostIdentity = "host",
      bootIdentity = "boot",
      pid = 42,
      processBirthToken = "birth",
      heartbeatAt = "2026-08-07T18:22:00Z",
      expiresAt = "2026-08-07T18:22:30Z",
    )

    DatabaseRuntime.ensureDatabase(dbPath).use { connection ->
      val store = GoalRunnerControlStore(connection)
      assertTrue(store.acquireExecutionLease("parent-late", lease))
      assertTrue(store.heartbeatExecutionLease("parent-late", lease.copy(heartbeatAt = "2026-08-07T18:22:20.001Z")))
      assertEquals(20_000, store.controlState("parent-late").activeDurationMs)
    }
  }

  @Test
  fun `reacquiring a lease after downtime resumes the clock without counting the downtime`() {
    val dbPath = Files.createTempDirectory("skillbill-goal-active-clock-reacquire").resolve("metrics.db")
    val lease = GoalRunnerExecutionLease(
      generation = 1,
      ownerToken = "owner-token-123456",
      hostIdentity = "host",
      bootIdentity = "boot",
      pid = 42,
      processBirthToken = "birth",
      heartbeatAt = "2026-08-07T18:22:00Z",
      expiresAt = "2026-08-07T18:22:30Z",
    )

    DatabaseRuntime.ensureDatabase(dbPath).use { connection ->
      val store = GoalRunnerControlStore(connection)
      assertTrue(store.acquireExecutionLease("parent-reacquire", lease))
      assertTrue(store.heartbeatExecutionLease("parent-reacquire", lease.copy(heartbeatAt = "2026-08-07T18:22:10Z")))
      assertTrue(store.releaseExecutionLease("parent-reacquire", lease.ownerToken, lease.generation))

      val nextDay = lease.copy(
        generation = 2,
        heartbeatAt = "2026-08-08T06:31:00Z",
        expiresAt = "2026-08-08T06:31:30Z",
      )
      assertTrue(store.acquireExecutionLease("parent-reacquire", nextDay))
      assertEquals(10_000, store.controlState("parent-reacquire").activeDurationMs)

      assertTrue(store.heartbeatExecutionLease("parent-reacquire", nextDay.copy(heartbeatAt = "2026-08-08T06:31:05Z")))
      assertEquals(15_000, store.controlState("parent-reacquire").activeDurationMs)
    }
  }

  @Test
  fun `heartbeat increments goal and subtask active duration equally`() {
    val dbPath = Files.createTempDirectory("skillbill-goal-subtask-dual").resolve("metrics.db")
    val lease = GoalRunnerExecutionLease(
      generation = 1,
      ownerToken = "owner-token-123456",
      hostIdentity = "host",
      bootIdentity = "boot",
      pid = 42,
      processBirthToken = "birth",
      heartbeatAt = "2026-08-07T18:22:00Z",
      expiresAt = "2026-08-07T18:22:30Z",
    )

    DatabaseRuntime.ensureDatabase(dbPath).use { connection ->
      val store = GoalRunnerControlStore(connection)
      store.persistControlState("parent-dual", GoalRunnerControlState(currentSubtaskId = 1))
      assertTrue(store.acquireExecutionLease("parent-dual", lease))
      assertTrue(store.heartbeatExecutionLease("parent-dual", lease.copy(heartbeatAt = "2026-08-07T18:22:10Z")))
      val state = store.controlState("parent-dual")
      assertEquals(10_000, state.activeDurationMs)
      assertEquals(state.activeDurationMs, state.subtaskActiveDurationMs)
    }
  }

  @Test
  fun `clearing control state preserves both goal and subtask accumulated clocks`() {
    val dbPath = Files.createTempDirectory("skillbill-goal-clear-subtask-clock").resolve("metrics.db")
    val lease = GoalRunnerExecutionLease(
      generation = 1,
      ownerToken = "owner-token-123456",
      hostIdentity = "host",
      bootIdentity = "boot",
      pid = 42,
      processBirthToken = "birth",
      heartbeatAt = "2026-08-02T10:00:00Z",
      expiresAt = "2026-08-02T10:00:30Z",
    )

    DatabaseRuntime.ensureDatabase(dbPath).use { connection ->
      val store = GoalRunnerControlStore(connection)
      store.persistControlState(
        "parent-clear-subtask",
        GoalRunnerControlState(currentSubtaskId = 1),
      )
      assertTrue(store.acquireExecutionLease("parent-clear-subtask", lease))
      assertTrue(
        store.heartbeatExecutionLease("parent-clear-subtask", lease.copy(heartbeatAt = "2026-08-02T10:00:10Z")),
      )
      store.persistControlState(
        "parent-clear-subtask",
        store.controlState("parent-clear-subtask").copy(
          pauseRequested = true,
          paused = true,
          pauseReason = "operator_request",
          pausedAt = "2026-08-02T10:00:10Z",
        ),
      )

      store.clearControlState("parent-clear-subtask")

      val cleared = store.controlState("parent-clear-subtask")
      assertEquals(10_000, cleared.activeDurationMs)
      assertEquals(10_000, cleared.subtaskActiveDurationMs)
      assertEquals("2026-08-02T10:00:10Z", cleared.activeDurationAsOf)
      assertEquals("2026-08-02T10:00:10Z", cleared.subtaskActiveDurationAsOf)
    }
  }

  @Test
  fun `switching current subtask id zeros only the subtask accumulator`() {
    val state = GoalRunnerControlState(
      currentSubtaskId = 1,
      subtaskActiveDurationMs = 30_000,
      subtaskActiveDurationAsOf = "2026-08-02T10:00:10Z",
      activeDurationMs = 60_000,
      activeDurationAsOf = "2026-08-02T10:00:10Z",
    )

    val switched = state.reconciledForCurrentSubtask(2)
    assertEquals(2, switched.currentSubtaskId)
    assertEquals(0, switched.subtaskActiveDurationMs)
    assertEquals(null, switched.subtaskActiveDurationAsOf)
    assertEquals(60_000, switched.activeDurationMs)
    assertEquals(state, state.reconciledForCurrentSubtask(1))
  }
}
