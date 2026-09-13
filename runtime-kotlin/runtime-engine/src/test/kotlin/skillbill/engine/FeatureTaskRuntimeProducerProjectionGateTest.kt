
package skillbill.engine
import skillbill.application.realFeatureTaskRuntimePhaseOutputValidator
import skillbill.application.realPlanningProjectionValidator
import skillbill.contracts.JsonCodec
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunReport
import skillbill.engine.planningprojection.producerProjectionGateReason
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeatureTaskRuntimeProducerProjectionGateTest {
  @Test
  fun `a preplan output missing value blocks preplan and never reaches plan`() {
    val harness = runnerHarness(
      RuntimeHarnessConfig(
        launcher = RuntimeRecordingLauncher { request ->
          val phaseId = phaseIdFromPrompt(requireNotNull(request.skillRunRequest.promptOverride))
          facts(if (phaseId == "preplan") PREPLAN_MISSING_VALUE else validJsonOutput(phaseId))
        },
        validator = realFeatureTaskRuntimePhaseOutputValidator,
        agentAssignment = phasePerAgentAssignment(),
      ),
    )

    val blocked = assertIs<FeatureTaskRuntimeRunReport.Blocked>(harness.runner.run(harness.request()))
    assertEquals("preplan", blocked.lastIncompletePhase)
    assertTrue(
      blocked.blockedReason.contains("value", ignoreCase = true) ||
        harness.io.database.rejectedDiagnostics().any {
          it.metadata.phaseId == "preplan" && it.metadata.reason.contains("value", ignoreCase = true)
        },
    )
    assertTrue(harness.launchedPromptPhaseOrder().none { it == "plan" })
  }

  @Test
  fun `leftover digest keys plus value complete preplan without producer-projection re-entry`() {
    val envelope = JsonCodec.anyToStringAnyMap(
      JsonCodec.jsonElementToValue(JsonCodec.parseObjectOrNull(PREPLAN_LEFTOVER_DIGEST_KEYS)!!),
    )!!
    assertNull(
      producerProjectionGateReason(
        phaseId = "preplan",
        outputMap = envelope,
        planningProjectionValidator = realPlanningProjectionValidator,
      ),
    )
  }

  @Test
  fun `a blocked producing-phase output with a projection-invalid body settles terminally, not through the gate`() {
    listOf("preplan", "plan", "implement").forEach { targetPhase ->
      val outcome = runTerminalProducer(targetPhase, terminalProducerOutput(targetPhase, status = "blocked"))

      assertEquals(targetPhase, outcome.blocked.lastIncompletePhase, "$targetPhase must settle at its own phase")
      assertContains(outcome.blocked.blockedReason, "status 'blocked'")
      assertContains(outcome.blocked.blockedReason, TERMINAL_BLOCKING_REASON)
      assertTrue(
        !outcome.blocked.blockedReason.contains("is not a valid"),
        "a blocked envelope must bypass the producer projection gate",
      )
    }
  }

  @Test
  fun `a failed producing-phase output with a projection-invalid body settles terminally, not through the gate`() {
    listOf("preplan", "plan", "implement").forEach { targetPhase ->
      val outcome = runTerminalProducer(targetPhase, terminalProducerOutput(targetPhase, status = "failed"))

      assertEquals(targetPhase, outcome.blocked.lastIncompletePhase)
      assertContains(outcome.blocked.blockedReason, "status 'failed'")
      assertTrue(
        !outcome.blocked.blockedReason.contains("is not a valid"),
        "a failed envelope must bypass the producer projection gate",
      )
    }
  }

  private fun runTerminalProducer(targetPhase: String, terminalOutput: String): ProducerBlockOutcome {
    val harness = runnerHarness(
      RuntimeHarnessConfig(
        launcher = RuntimeRecordingLauncher { request ->
          val phaseId = phaseIdFromPrompt(requireNotNull(request.skillRunRequest.promptOverride))
          facts(if (phaseId == targetPhase) terminalOutput else validJsonOutput(phaseId))
        },
        agentAssignment = phasePerAgentAssignment(),
      ),
    )
    val report = harness.runner.run(harness.request())
    val blocked = assertIs<FeatureTaskRuntimeRunReport.Blocked>(report)
    return ProducerBlockOutcome(blocked)
  }

  private class ProducerBlockOutcome(
    val blocked: FeatureTaskRuntimeRunReport.Blocked,
  )
}

private fun envelope(phaseId: String, producedOutputs: String): String =
  """{"contract_version":"0.2","phase_id":"$phaseId","status":"completed","summary":"Producer output.",""" +
    """"produced_outputs":$producedOutputs}"""

private const val TERMINAL_BLOCKING_REASON = "Upstream dependency was unavailable."

private fun terminalProducerOutput(phaseId: String, status: String): String {
  val reconciled = if (phaseId == "implement") ""","reconciled_state":{"reconciled":true}""" else ""
  return """{"contract_version":"0.2","phase_id":"$phaseId","status":"$status",""" +
    """"failure_disposition":"non_retryable_policy_conflict","summary":"Producer could not finish.",""" +
    """"produced_outputs":{"blocking_reasons":["$TERMINAL_BLOCKING_REASON"],""" +
    """"free_form":"not a projection"$reconciled}}"""
}

private val PREPLAN_MISSING_VALUE: String = envelope(
  "preplan",
  """{"prompt":"optional only"}""",
)

private val PREPLAN_LEFTOVER_DIGEST_KEYS: String = envelope(
  "preplan",
  """{"value":"prose preplan with leftover digest keys","affected_boundaries":["runtime-domain"],""" +
    """"risks":["Fixture risk."],"rollout":{"flag_required":false,"flag_pattern":"none","notes":"n"},""" +
    """"validation_strategy":["Focused runtime tests."]}""",
)
