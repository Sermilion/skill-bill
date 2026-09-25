package skillbill.engine

import skillbill.application.FakeDatabaseSessionFactory
import skillbill.application.InMemoryWorkflowStates
import skillbill.application.testWorkflowSnapshotValidator
import skillbill.engine.goalrunner.execution.core.testWorkflowGoalRunnerOutcomeStore
import skillbill.engine.goalrunner.model.GoalRunnerRunRequest
import skillbill.engine.goalrunner.persist.GoalRunnerLedgerContext
import skillbill.engine.goalrunner.persist.GoalRunnerLedgerRecorder
import skillbill.engine.goalrunner.planning.attempt.DurableGoalPlanningAttemptRecorder
import skillbill.engine.goalrunner.planning.model.GoalPlanningAttemptRecord
import skillbill.engine.goalrunner.telemetry.GoalRunnerProgressEventEmitter
import skillbill.ports.agentrun.model.AgentRunProgressEmission
import skillbill.ports.diagnostics.NoopRuntimeDiagnostics
import skillbill.ports.workflow.model.toSnapshot
import skillbill.workflow.model.goalreview.GoalProgressEventKind
import skillbill.workflow.model.goalreview.GoalProgressOutcome
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val ISSUE_KEY = "SKILL-378"
private const val WORKFLOW_ID = "wftr-task-runtime"

private val sequenceTestClock: Clock = Clock.fixed(Instant.parse("2026-09-25T10:00:00Z"), ZoneOffset.UTC)

/**
 * The durable sequence number of a progress event or attempt-ledger entry is allocated by the write
 * itself. These tests pin that: writers on one workflow must never repeat or invert a number, which
 * is what per-instance counters seeded from a shared watermark used to do.
 */
class GoalRunnerDurableSequenceAllocationTest {
  private val workflows =
    InMemoryWorkflowStates().apply { saveFeatureTaskRuntimeWorkflow(taskRuntimeWorkflowRecord(WORKFLOW_ID)) }
  private val store =
    testWorkflowGoalRunnerOutcomeStore(
      FakeDatabaseSessionFactory(workflows),
      testWorkflowSnapshotValidator,
    )

  @Test
  fun `interleaved planning attempts and progress events in one run read back strictly increasing`() {
    val attempts = DurableGoalPlanningAttemptRecorder(store, sequenceTestClock)
    val progress = progressEmitter()

    attempts.record(planningAttempt(attempt = 1))
    progress.emit(heartbeat())
    attempts.record(planningAttempt(attempt = 2))
    progress.emit(heartbeat())

    assertStrictlyIncreasing(store.progressEvents(WORKFLOW_ID).map { it.sequenceNumber }, expectedSize = 4)
  }

  @Test
  fun `two recorder instances on one workflow allocate distinct increasing sequences`() {
    DurableGoalPlanningAttemptRecorder(store, sequenceTestClock).record(planningAttempt(attempt = 1))
    progressEmitter().emit(heartbeat())
    DurableGoalPlanningAttemptRecorder(store, sequenceTestClock).record(planningAttempt(attempt = 2))
    progressEmitter().emit(heartbeat())

    assertStrictlyIncreasing(store.progressEvents(WORKFLOW_ID).map { it.sequenceNumber }, expectedSize = 4)

    ledgerRecorder().recordLedgerEntry(childActivation())
    ledgerRecorder().recordLedgerEntry(childActivation())

    assertStrictlyIncreasing(storedLedgerSequences(), expectedSize = 2)
  }

  private fun progressEmitter() =
    GoalRunnerProgressEventEmitter(
      outcomeStore = store,
      resolveWorkflowId = { WORKFLOW_ID },
      issueKey = ISSUE_KEY,
      clock = sequenceTestClock,
      diagnostics = NoopRuntimeDiagnostics,
    )

  private fun ledgerRecorder() =
    GoalRunnerLedgerRecorder(
      store,
      GoalRunnerRunRequest(
        issueKey = ISSUE_KEY,
        repoRoot = Path.of("/tmp/skillbill-sequence-allocation"),
        invokedAgentId = "claude",
      ),
      sequenceTestClock,
      NoopRuntimeDiagnostics,
    )

  private fun planningAttempt(attempt: Int) =
    GoalPlanningAttemptRecord(
      parentWorkflowId = WORKFLOW_ID,
      issueKey = ISSUE_KEY,
      phaseId = "plan",
      subtaskId = 1,
      attempt = attempt,
      outcome = GoalProgressOutcome.SUCCEEDED,
    )

  private fun heartbeat() =
    AgentRunProgressEmission(
      GoalProgressEventKind.OPERATION_HEARTBEAT,
      true,
      "child_agent_run",
      "long_child_run",
    )

  private fun childActivation() =
    GoalRunnerLedgerContext.ChildActivation(
      workflowId = WORKFLOW_ID,
      issueKey = ISSUE_KEY,
      subtaskId = 1,
    )

  private fun storedLedgerSequences(): List<Int> {
    val artifacts = requireNotNull(workflows.getFeatureTaskRuntimeWorkflow(WORKFLOW_ID)).toSnapshot().artifacts
    return (artifacts["goal_attempt_ledger"] as List<*>)
      .map { entry -> (entry as Map<*, *>)["sequence_number"] as Int }
  }

  private fun assertStrictlyIncreasing(
    sequences: List<Int>,
    expectedSize: Int,
  ) {
    assertEquals(expectedSize, sequences.size)
    assertEquals(sequences.distinct(), sequences, "durable sequence numbers must be distinct")
    assertTrue(
      sequences.zipWithNext().all { (earlier, later) -> earlier < later },
      "durable sequence numbers must strictly increase, got $sequences",
    )
  }
}
