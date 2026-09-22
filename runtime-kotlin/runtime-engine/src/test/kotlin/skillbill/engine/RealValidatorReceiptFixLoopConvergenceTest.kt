package skillbill.engine

import skillbill.application.realFeatureTaskRuntimePhaseOutputValidator
import skillbill.application.realPlanningProjectionValidator
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeRunReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RealValidatorReceiptFixLoopConvergenceTest {
  @Test
  fun `missing value blocks implement without reaching audit`() {
    assertBlockedAtImplement(IMPLEMENT_MISSING_VALUE)
  }

  @Test
  fun `blank value blocks implement without reaching audit`() {
    assertBlockedAtImplement(IMPLEMENT_BLANK_VALUE)
  }

  private fun assertBlockedAtImplement(malformed: String) {
    val harness =
      runnerHarness(
        RuntimeHarnessConfig(planningProjectionValidator = realPlanningProjectionValidator).copy(
          launcher =
            RuntimeRecordingLauncher { request ->
              val phaseId = phaseIdFromPrompt(requireNotNull(request.skillRunRequest.promptOverride))
              facts(if (phaseId == "implement") malformed else validJsonOutput(phaseId))
            },
          validator = realFeatureTaskRuntimePhaseOutputValidator,
          agentAssignment = phasePerAgentAssignment(),
        ),
      )

    val blocked = assertIs<FeatureTaskRuntimeRunReport.Blocked>(harness.runner.run(harness.request()))

    assertEquals("implement", blocked.lastIncompletePhase)
    assertTrue(
      blocked.blockedReason.contains("value", ignoreCase = true) ||
        harness.io.database.rejectedDiagnostics().any {
          it.metadata.phaseId == "implement" && it.metadata.reason.contains("value", ignoreCase = true)
        },
    )
    assertTrue(!harness.launchedPromptPhaseOrder().contains("audit"))
  }
}

private const val IMPLEMENT_MISSING_VALUE: String =
  """{"contract_version":"0.4","phase_id":"implement","status":"completed",""" +
    """"summary":"Implement output.","produced_outputs":{"prompt":"optional only"}}"""

private const val IMPLEMENT_BLANK_VALUE: String =
  """{"contract_version":"0.4","phase_id":"implement","status":"completed",""" +
    """"summary":"Implement output.","produced_outputs":{"value":"   "}}"""
