package skillbill.engine

import skillbill.application.realFeatureTaskRuntimePhaseOutputValidator
import skillbill.contracts.JsonCodec
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeRunReport
import skillbill.engine.featuretask.runloop.state.validationEvidenceFromEnvelope
import skillbill.engine.featuretask.validation.FeatureTaskRuntimeValidationGateCoordinator
import skillbill.ports.validation.ValidationGateRunner
import skillbill.ports.validation.model.ValidationGateRunRequest
import skillbill.ports.validation.model.ValidationGateRunResult
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.taskruntime.model.validation.FeatureTaskRuntimeValidationGateRepairWindowPhase
import skillbill.workflow.taskruntime.model.validation.ValidationGateCacheMode
import skillbill.workflow.taskruntime.model.validation.ValidationGateRunOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FeatureTaskRuntimeValidationGateDispatchTest {
  @Test
  fun `agent finished signal cannot complete validate until runtime confirmation passes`() {
    val confirmations = mutableListOf<ValidationGateRunRequest>()
    lateinit var harness: RunnerHarness
    val gate = object : ValidationGateRunner {
      override fun run(request: ValidationGateRunRequest): ValidationGateRunResult {
        if (!request.terminalVerifying) return gateResult(request, passed = true)
        assertNotEquals(
          WorkflowStepStatus.COMPLETED,
          harness.recorder.loadPhaseRecords(WORKFLOW_ID)?.get("validate")?.status,
        )
        confirmations += request
        return gateResult(request, passed = confirmations.size > 1)
      }
    }
    harness = validationHarness(gate)

    val report = harness.runner.run(harness.request())

    assertIs<FeatureTaskRuntimeRunReport.Completed>(report, report.toString())
    assertEquals(2, confirmations.size)
    assertEquals(2, harness.launchedPromptPhaseOrder().count { it == "validate" })
    confirmations.forEach { request ->
      assertEquals(ValidationGateCacheMode.FORCED_FULL, request.cacheMode)
      assertEquals(
        kotlinPackWithValidationGate().validationGate!!.cacheBypassingCollectAllFullGateCommand,
        request.argv,
      )
    }
    val record = assertNotNull(harness.recorder.loadPhaseRecords(WORKFLOW_ID)?.get("validate"))
    val envelope = assertNotNull(
      JsonCodec.parseObjectOrNull(assertNotNull(record.outputArtifact))
        ?.let(JsonCodec::jsonElementToValue)
        ?.let(JsonCodec::anyToStringAnyMap),
    )
    val evidence = assertNotNull(validationEvidenceFromEnvelope(envelope, "validate"))
    assertEquals(listOf(1, 0), evidence.results.map { it.exitCode })
    evidence.requireSuccessfulCommand(confirmations.last().argv.joinToString(" "), "validate")
  }

  @Test
  fun `persistent gate failure blocks validate before history and commit despite agent success`() {
    val gate = object : ValidationGateRunner {
      override fun run(request: ValidationGateRunRequest): ValidationGateRunResult = gateResult(request, passed = false)
    }
    val harness = validationHarness(gate)

    val report = assertIs<FeatureTaskRuntimeRunReport.Blocked>(harness.runner.run(harness.request()))

    assertEquals("validate", report.lastIncompletePhase)
    assertEquals(
      FeatureTaskRuntimeValidationGateCoordinator.FINDINGS_REMAIN_AFTER_RESTARTS_REASON,
      report.blockedReason,
    )
    assertEquals(3, harness.launchedPromptPhaseOrder().count { it == "validate" })
    assertFalse("write_history" in harness.launchedPromptPhaseOrder())
    val records = harness.recorder.loadPhaseRecords(WORKFLOW_ID).orEmpty()
    assertEquals(WorkflowStepStatus.BLOCKED, records["validate"]?.status)
    assertTrue(records["commit_push"] == null)
    val progress = assertNotNull(harness.recorder.loadValidationGateProgress(WORKFLOW_ID))
    assertEquals(FeatureTaskRuntimeValidationGateRepairWindowPhase.FINDINGS_OPEN, progress.repairWindowPhase)
    assertEquals(listOf(1, 1, 1), progress.gateRuns.map { it.exitCode })
  }

  private fun validationHarness(gate: ValidationGateRunner): RunnerHarness = runnerHarness(
    RuntimeHarnessConfig(
      validationGateRunner = gate,
      validator = realFeatureTaskRuntimePhaseOutputValidator,
      launcher = RuntimeRecordingLauncher { request ->
        when (phaseIdFromPrompt(requireNotNull(request.skillRunRequest.promptOverride))) {
          "validate" -> facts("finished")
          "audit" -> facts(auditSatisfiedOutput())
          else -> facts(defaultPhaseOutput(request))
        }
      },
    ),
  )

  private fun gateResult(request: ValidationGateRunRequest, passed: Boolean): ValidationGateRunResult =
    ValidationGateRunResult(
      exitCode = if (passed) 0 else 1,
      durationMs = 1,
      outcome = if (passed) ValidationGateRunOutcome.PASSED else ValidationGateRunOutcome.FAILED,
      cacheMode = request.cacheMode,
      executedWorkUnits = 1,
      executedCheckIdentities = listOf("runtime-engine|test"),
      findings = emptyList(),
      stdout = if (passed) "BUILD SUCCESSFUL" else "FeatureTaskRuntimeSimplifyPhaseBoundariesTest FAILED",
    )
}
