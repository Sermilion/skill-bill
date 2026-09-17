package skillbill.engine

import skillbill.engine.goalrunner.GoalRunnerStatusTestPorts
import skillbill.engine.goalrunner.model.GoalRunnerStatusRequest
import skillbill.engine.goalrunner.testGoalRunnerStatusService
import skillbill.goalrunner.model.GoalRunnerAttemptLedgerSummary
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.ports.goalrunner.runner.GoalRunnerAttemptLedgerStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoalRunnerStatusProjectionDegradationTest {
  @Test
  fun `attempt ledger failure records the seam and marks the projection degraded`() {
    val diagnostics = RecordingStatusDiagnostics()
    val projection = requireNotNull(
      testGoalRunnerStatusService(
        manifestStore = InMemoryGoalManifestStore(manifest(subtaskCount = 1)),
        outcomeStore = RecordingOutcomeStore(),
        ports = GoalRunnerStatusTestPorts(
          attemptLedgerStore = object : GoalRunnerAttemptLedgerStore {
            override fun readAttemptLedgerSummary(issueKey: String): GoalRunnerAttemptLedgerSummary =
              error("attempt ledger unavailable")
          },
          diagnostics = diagnostics,
        ),
      ).status(
        GoalRunnerStatusRequest(
          issueKey = "SKILL-56",
          invokedAgentId = "codex",
        ),
      ),
    )

    assertTrue(projection.degradedDurableRead)
    assertEquals(0, projection.blockedAttemptCount)
    assertEquals(
      "seam=goal-status.attempt_ledger value_expected=ledger_summary value_used=degraded",
      diagnostics.warnings.single(),
    )
  }
}

private class RecordingStatusDiagnostics : RuntimeDiagnostics {
  val warnings = mutableListOf<String>()

  override fun warning(message: String, error: Throwable?) {
    warnings += message
  }

  override fun error(message: String, error: Throwable?) = Unit
}
