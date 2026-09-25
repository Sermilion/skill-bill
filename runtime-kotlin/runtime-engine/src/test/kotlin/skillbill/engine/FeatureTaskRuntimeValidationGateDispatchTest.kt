package skillbill.engine

import skillbill.application.realFeatureTaskRuntimePhaseOutputValidator
import skillbill.contracts.JsonCodec
import skillbill.engine.featuretask.lifecycle.branch.Blocked
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeRunReport
import skillbill.engine.featuretask.runloop.state.validationPassedFromEnvelope
import skillbill.install.model.SupportedAgent
import skillbill.ports.agentrun.agentRunLaunchFacts
import skillbill.ports.agentrun.model.AgentRunLaunchOutcome
import skillbill.ports.agentrun.model.AgentRunTermination
import skillbill.ports.validation.ValidationGateRunner
import skillbill.ports.validation.model.ValidationGateRunRequest
import skillbill.ports.validation.model.ValidationGateRunResult
import skillbill.workflow.model.WorkflowStepStatus
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FeatureTaskRuntimeValidationGateDispatchTest {
  @Test
  fun `boolean success completes validate without command evidence or a runtime rerun`() {
    val harness = validationHarness(validJsonOutput("validate"))

    val report = harness.runner.run(harness.request())

    assertIs<FeatureTaskRuntimeRunReport.Completed>(report, report.toString())
    assertEquals(1, harness.launchedPromptPhaseOrder().count { it == "validate" })
    val record = assertNotNull(harness.recorder.loadPhaseRecords(WORKFLOW_ID)?.get("validate"))
    assertEquals(WorkflowStepStatus.COMPLETED, record.status)
    val envelope =
      assertNotNull(
        JsonCodec.parseObjectOrNull(assertNotNull(record.outputArtifact))
          ?.let(JsonCodec::jsonElementToValue)
          ?.let(JsonCodec::anyToStringAnyMap),
      )
    assertEquals(true, validationPassedFromEnvelope(envelope))
    assertNull(harness.recorder.loadValidationGateProgress(WORKFLOW_ID))
  }

  @Test
  fun `boolean validation completes with or without a runtime platform pack`() {
    val output = validJsonOutput("validate")
    listOf(true, false).forEach { hasRuntimePack ->
      val harness = validationHarness(hasRuntimePack) { facts(output) }

      assertIs<FeatureTaskRuntimeRunReport.Completed>(harness.runner.run(harness.request()))
      assertEquals(1, harness.launchedPromptPhaseOrder().count { it == "validate" })
    }
  }

  @Test
  fun `false missing and malformed results cannot advance beyond validate`() {
    val valid = validJsonOutput("validate")
    val malformed =
      listOf(
        valid.replace("validation_passed", "missing_signal"),
        valid.replace("\"validation_passed\":true", "\"validation_passed\":\"true\""),
        "finished",
      )
    malformed.forEach { output ->
      val harness = validationHarness(output)

      val report = assertIs<FeatureTaskRuntimeRunReport.Blocked>(harness.runner.run(harness.request()))

      assertEquals("validate", report.lastIncompletePhase)
      assertEquals(2, harness.launchedPromptPhaseOrder().count { it == "validate" })
      assertFalse("write_history" in harness.launchedPromptPhaseOrder())
      val records = harness.recorder.loadPhaseRecords(WORKFLOW_ID).orEmpty()
      assertEquals(WorkflowStepStatus.BLOCKED, records["validate"]?.status)
      assertNull(records["commit_push"])
    }
  }

  @Test
  fun `the same remaining failures twice blocks validate without treating false as success`() {
    val remaining = "detekt failed on LongMethod in RankingService."
    val output =
      validJsonOutput("validate")
        .replace("\"validation_passed\":true", "\"validation_passed\":false")
        .replace("Project checks passed.", remaining)
    val harness = validationHarness(output)

    val report = assertIs<FeatureTaskRuntimeRunReport.Blocked>(harness.runner.run(harness.request()))

    assertEquals("validate", report.lastIncompletePhase)
    assertEquals(2, harness.launchedPromptPhaseOrder().count { it == "validate" })
    assertFalse("write_history" in harness.launchedPromptPhaseOrder())
    assertContains(report.blockedReason, "leftover set did not shrink")
  }

  @Test
  fun `blocked phase result remains blocked without a runtime check to override it`() {
    val output =
      """
      {"contract_version":"0.6","phase_id":"validate","status":"blocked",
       "failure_disposition":"needs_user_action","summary":"Quality check could not run.",
       "produced_outputs":{}}
      """.trimIndent()
    val harness = validationHarness(output)

    val report = assertIs<FeatureTaskRuntimeRunReport.Blocked>(harness.runner.run(harness.request()))

    assertEquals("validate", report.lastIncompletePhase)
    assertEquals(1, harness.launchedPromptPhaseOrder().count { it == "validate" })
    assertFalse("write_history" in harness.launchedPromptPhaseOrder())
  }

  @Test
  fun `false result reruns phase and a later true result advances`() {
    val harness =
      validationHarness { attempt ->
        facts(
          validJsonOutput("validate").let { output ->
            if (attempt == 1) {
              output.replace("\"validation_passed\":true", "\"validation_passed\":false")
                .replace("Project checks passed.", "WidgetTest failed: expected 2 but got 3.")
            } else {
              output
            }
          },
        )
      }

    val report = harness.runner.run(harness.request())
    assertIs<FeatureTaskRuntimeRunReport.Completed>(report, report.toString())
    assertEquals(2, harness.launchedPromptPhaseOrder().count { it == "validate" })
    assertEquals(2, harness.recorder.loadPhaseRecords(WORKFLOW_ID)?.get("validate")?.attemptCount)
    val validationPrompts =
      harness.launcher.requests.mapNotNull { it.skillRunRequest.promptOverride }
        .filter { phaseIdFromPrompt(it) == "validate" }
    assertContains(validationPrompts.last(), "WidgetTest failed: expected 2 but got 3.")
  }

  @Test
  fun `two shrinking false results then a true result still advance`() {
    val harness =
      validationHarness { attempt ->
        facts(
          validJsonOutput("validate").let { output ->
            when (attempt) {
              1 ->
                output.replace("\"validation_passed\":true", "\"validation_passed\":false")
                  .replace("Project checks passed.", "detekt failed on LongMethod A and LongMethod B.")
              2 ->
                output.replace("\"validation_passed\":true", "\"validation_passed\":false")
                  .replace("Project checks passed.", "detekt failed on LongMethod B.")
              else -> output
            }
          },
        )
      }

    val report = harness.runner.run(harness.request())
    assertIs<FeatureTaskRuntimeRunReport.Completed>(report, report.toString())
    assertEquals(3, harness.launchedPromptPhaseOrder().count { it == "validate" })
  }

  @Test
  fun `failed validation process cannot complete using a successful-looking final response`() {
    val harness =
      validationHarness {
        agentRunLaunchFacts(
          agent = SupportedAgent.CLAUDE,
          termination = AgentRunTermination.Exited(1),
          stdout = validJsonOutput("validate"),
          stderr = "Validation process failed.",
        )
      }

    val report = assertIs<FeatureTaskRuntimeRunReport.Blocked>(harness.runner.run(harness.request()))

    assertEquals("validate", report.lastIncompletePhase)
    assertFalse("write_history" in harness.launchedPromptPhaseOrder())
  }

  @Test
  fun `provider limit leaves validate paused without recording a completed check`() {
    val harness =
      validationHarness {
        agentRunLaunchFacts(
          agent = SupportedAgent.CLAUDE,
          termination = AgentRunTermination.Exited(1),
          stdout = "",
          stderr = "You've hit your usage limit",
        )
      }

    assertIs<FeatureTaskRuntimeRunReport.Paused>(harness.runner.run(harness.request()))
    assertEquals(WorkflowStepStatus.PAUSED, harness.recorder.loadPhaseRecords(WORKFLOW_ID)?.get("validate")?.status)
    assertFalse("write_history" in harness.launchedPromptPhaseOrder())
  }

  private fun validationHarness(output: String): RunnerHarness = validationHarness { facts(output) }

  private fun validationHarness(
    hasRuntimePack: Boolean = true,
    outcome: (Int) -> AgentRunLaunchOutcome,
  ): RunnerHarness {
    var validationAttempts = 0
    return runnerHarness(
      RuntimeHarnessConfig(
        validationGatePlatformManifests = if (hasRuntimePack) listOf(kotlinPackWithValidationGate()) else emptyList(),
        validationGateRunner =
          object : ValidationGateRunner {
            override fun run(request: ValidationGateRunRequest): ValidationGateRunResult =
              error("Runtime must not rerun the phase check: ${request.argv}")
          },
        validator = realFeatureTaskRuntimePhaseOutputValidator,
        launcher =
          RuntimeRecordingLauncher { request ->
            when (phaseIdFromPrompt(requireNotNull(request.skillRunRequest.promptOverride))) {
              "validate" -> outcome(++validationAttempts)
              "audit" -> facts(auditSatisfiedOutput())
              else -> facts(defaultPhaseOutput(request))
            }
          },
      ),
    )
  }
}
