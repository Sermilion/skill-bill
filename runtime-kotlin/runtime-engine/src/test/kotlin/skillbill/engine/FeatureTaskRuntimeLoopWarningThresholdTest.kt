package skillbill.engine
import skillbill.engine.featuretask.FeatureTaskRuntimeAttemptBudgets
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunReport
import skillbill.ports.diagnostics.NoopRuntimeDiagnostics
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseLedgerAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FeatureTaskRuntimeLoopWarningThresholdTest {
  private val threshold = FeatureTaskRuntimePhaseWorkflowDefinition.SEMANTIC_LOOP_WARNING_THRESHOLD

  @Test
  fun `review_fix crossing the threshold warns once naming the loop the count and the work`() {
    val diagnostics = RecordingDiagnostics()
    val harness = runnerHarness(
      reviewFixRuntimeConfig(2).copy(launcher = reviewFixLauncher(convergeOnReview = 2), diagnostics = diagnostics),
    )

    val request = harness.request()
    assertIs<FeatureTaskRuntimeRunReport.Completed>(harness.runner.run(request))

    assertEquals(
      listOf(1),
      loopEdgeIterations(harness, FeatureTaskRuntimePhaseWorkflowDefinition.REVIEW_FIX_LOOP_ID),
      "verify_findings drives a single bounded review_fix edge per subtask",
    )
    assertEquals(emptyList(), diagnostics.warnings, "bounded review_fix must not attach semantic loop warnings")
  }

  @Test
  fun `iterations up to the threshold stay silent for review_fix`() {
    val reviewDiagnostics = RecordingDiagnostics()
    val reviewHarness = runnerHarness(
      reviewFixRuntimeConfig(threshold + 1).copy(
        launcher = reviewFixLauncher(convergeOnReview = threshold + 1),
        diagnostics = reviewDiagnostics,
      ),
    )
    assertIs<FeatureTaskRuntimeRunReport.Completed>(reviewHarness.runner.run(reviewHarness.request()))
    assertEquals(
      listOf(1),
      loopEdgeIterations(reviewHarness, FeatureTaskRuntimePhaseWorkflowDefinition.REVIEW_FIX_LOOP_ID),
    )
    assertEquals(emptyList(), reviewDiagnostics.warnings, "bounded review_fix never emits semantic loop warnings")
  }

  @Test
  fun `the transition outcome is identical with silent recording and throwing diagnostics`() {
    val outcomes = listOf<RuntimeDiagnostics>(
      NoopRuntimeDiagnostics,
      RecordingDiagnostics(),
      ThrowingDiagnostics(),
    ).map { diagnostics ->
      val harness = runnerHarness(
        reviewFixRuntimeConfig(2).copy(launcher = reviewFixLauncher(convergeOnReview = 2), diagnostics = diagnostics),
      )
      val report = assertIs<FeatureTaskRuntimeRunReport.Completed>(harness.runner.run(harness.request()))
      RunOutcome(
        completedPhaseIds = report.completedPhaseIds,
        launchedPhases = harness.launchedPromptPhaseOrder(),
        reviewFixIterations = loopEdgeIterations(harness, FeatureTaskRuntimePhaseWorkflowDefinition.REVIEW_FIX_LOOP_ID),
        phaseStatuses = harness.recorder.loadPhaseRecords(WORKFLOW_ID).orEmpty()
          .mapValues { (_, record) -> record.status.wireValue },
      )
    }

    assertEquals(outcomes[0], outcomes[1], "recording the warning must not change the run")
    assertEquals(outcomes[0], outcomes[2], "a diagnostics fault must not change the run")
  }

  @Test
  fun `process-failure and malformed-output budgets stay pinned independently of the semantic loops`() {
    assertEquals(3, FeatureTaskRuntimeAttemptBudgets.MAX_PROCESS_FAILURE_ATTEMPTS)
    assertEquals(1, FeatureTaskRuntimeAttemptBudgets.MAX_FORMAT_RETRY_ATTEMPTS)
    assertEquals(1, FeatureTaskRuntimeAttemptBudgets.MAX_OUTPUT_GATE_RETRY_ATTEMPTS)
  }

  private data class RunOutcome(
    val completedPhaseIds: List<String>,
    val launchedPhases: List<String>,
    val reviewFixIterations: List<Int>,
    val phaseStatuses: Map<String, String>,
  )

  private fun loopEdgeIterations(harness: RunnerHarness, loopId: String): List<Int> =
    harness.recorder.loadPhaseLedger(WORKFLOW_ID).orEmpty()
      .filter { it.action == FeatureTaskRuntimePhaseLedgerAction.LOOP_EDGE && it.loopId == loopId }
      .mapNotNull { it.edgeIteration }
}

internal class RecordingDiagnostics : RuntimeDiagnostics {
  val warnings: MutableList<String> = mutableListOf()

  override fun warning(message: String, error: Throwable?) {
    warnings += message
  }

  override fun error(message: String, error: Throwable?) = Unit
}

private class ThrowingDiagnostics : RuntimeDiagnostics {
  override fun warning(message: String, error: Throwable?): Nothing = kotlin.error("diagnostics sink unavailable")

  override fun error(message: String, error: Throwable?) = Unit
}
