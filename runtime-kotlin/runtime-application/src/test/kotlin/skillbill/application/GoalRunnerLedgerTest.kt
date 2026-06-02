package skillbill.application

import skillbill.application.model.GoalRunnerRunRequest
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoalRunnerLedgerTest {
  @Test
  fun `happy path records attempt ledger entries and best-effort accounting`() {
    val store = InMemoryGoalManifestStore(manifest = manifest(subtaskCount = 1))
    val outcomes = RecordingOutcomeStore()
    val launcher = RecordingSubtaskLauncher { request ->
      val subtaskId = requireNotNull(request.skillRunRequest.subtaskId)
      store.mutate { current -> current.withWorkflowId(subtaskId, "wfl-$subtaskId") }
      outcomes["wfl-$subtaskId"] = completeOutcome(subtaskId)
      launchFacts()
    }
    val runner = GoalRunner(store, launcher, outcomes, RecordingPullRequestPort())

    runner.run(ledgerRunRequest())

    // AC10/AC11: first start (child activation) + terminal done check are recorded.
    val actions = outcomes.attemptLedgerRecords.map { it.entry.action.wireValue }
    assertContains(actions, "child_activation")
    assertContains(actions, "terminal_done_check")
    // Ledger sequence space is monotonic (per-recorder, starts at 0).
    assertEquals(
      outcomes.attemptLedgerRecords.map { it.entry.sequenceNumber },
      outcomes.attemptLedgerRecords.map { it.entry.sequenceNumber }.sorted(),
    )
    // AC6/AC7: best-effort accounting recorded; unavailable does not fail the run.
    assertTrue(outcomes.sessionAccountingRecords.isNotEmpty())
    assertTrue(outcomes.sessionAccountingRecords.all { !it.accounting.available })
  }

  private fun ledgerRunRequest(): GoalRunnerRunRequest = GoalRunnerRunRequest(
    issueKey = "SKILL-56",
    repoRoot = Path.of("/tmp/skillbill-goal-runner"),
    invokedAgentId = "claude",
    dbPathOverride = "/tmp/skillbill-goal-runner/metrics.db",
  )
}
