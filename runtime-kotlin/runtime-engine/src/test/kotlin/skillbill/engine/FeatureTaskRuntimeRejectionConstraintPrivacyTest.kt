
package skillbill.engine
import skillbill.application.assertNoRawResponseSpan
import skillbill.application.assertPrivateDiagnosticRejection
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunReport
import skillbill.error.InvalidFeatureTaskRuntimePhaseOutputSchemaError
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseOutputValidator
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FeatureTaskRuntimeRejectionConstraintPrivacyTest {
  private val rawSpan = "smuggled-response-body-fragment"
  private val valueBearingReason = "status: does not have a value in the enumeration — offending value: $rawSpan"
  private val payloadFreeConstraint = "status: does not have a value in the enumeration"

  @Test
  fun `audit schema rejection blocks without exposing the constraint or raw response`() {
    val harness = rejectingHarness { sourceLabel ->
      InvalidFeatureTaskRuntimePhaseOutputSchemaError(
        sourceLabel = sourceLabel,
        reason = valueBearingReason,
        payloadFreeReason = payloadFreeConstraint,
      )
    }

    val blocked = assertIs<FeatureTaskRuntimeRunReport.Blocked>(harness.runner.run(harness.request()))
    assertContains(blocked.blockedReason, "does not participate in a fix loop")
    assertEquals(1, auditPrompts(harness).size)
    assertPrivateDiagnosticRejection(blocked.blockedReason, "phase-output-schema", rawSpan, payloadFreeConstraint)
    assertNoRawResponseSpan(blocked.blockedReason, rawSpan)
  }

  @Test
  fun `the private diagnostic row records the value-bearing reason alongside the raw bytes`() {
    val harness = rejectingHarness { sourceLabel ->
      InvalidFeatureTaskRuntimePhaseOutputSchemaError(
        sourceLabel = sourceLabel,
        reason = valueBearingReason,
        payloadFreeReason = payloadFreeConstraint,
      )
    }

    val blocked = assertIs<FeatureTaskRuntimeRunReport.Blocked>(harness.runner.run(harness.request()))
    assertContains(blocked.blockedReason, "does not participate in a fix loop")

    val diagnostic = harness.io.database.rejectedDiagnostics().single { it.metadata.phaseId == "audit" }
    assertContains(diagnostic.metadata.reason, rawSpan)
    assertTrue(diagnostic.payload?.isNotEmpty() == true, "the row must keep the raw response bytes")
  }

  @Test
  fun `a terminal schema-gate block keeps every operator surface free of the constraint and the raw span`() {
    var writeHistoryAttempts = 0
    val harness = runnerHarness(
      RuntimeHarnessConfig(
        validator = object : FeatureTaskRuntimePhaseOutputValidator {
          override fun validatePhaseOutputText(phaseOutputText: String, sourceLabel: String) {
            if (sourceLabel != "write_history") return
            writeHistoryAttempts += 1
            throw InvalidFeatureTaskRuntimePhaseOutputSchemaError(
              sourceLabel = sourceLabel,
              reason = valueBearingReason,
              payloadFreeReason = payloadFreeConstraint,
            )
          }
        },
      ),
    )

    val blocked = assertIs<FeatureTaskRuntimeRunReport.Blocked>(harness.runner.run(harness.request()))

    assertEquals("write_history", blocked.lastIncompletePhase)
    assertPrivateDiagnosticRejection(blocked.blockedReason, "phase-output-schema", rawSpan, payloadFreeConstraint)
    assertNoRawResponseSpan(blocked.blockedReason, rawSpan)
    val writeHistoryRecord = requireNotNull(harness.recorder.loadPhaseRecords(WORKFLOW_ID).orEmpty()["write_history"])
    assertNoRawResponseSpan(requireNotNull(writeHistoryRecord.blockedReason), rawSpan, payloadFreeConstraint)
  }

  @Test
  fun `a malformed rejection with no payload-free reason falls back instead of substituting the value-bearing one`() {
    val harness = rejectingHarness { sourceLabel ->
      InvalidFeatureTaskRuntimePhaseOutputSchemaError(
        sourceLabel = sourceLabel,
        reason = valueBearingReason,
        failureCode = "malformed",
      )
    }

    val blocked = assertIs<FeatureTaskRuntimeRunReport.Blocked>(harness.runner.run(harness.request()))
    assertContains(blocked.blockedReason, "Rejected output violated 'phase-output-schema'")
    assertNoRawResponseSpan(blocked.blockedReason, rawSpan, "Violated constraint: ")
  }

  @Test
  fun `audit schema rejection retains the exact diagnostic body without a second launch`() {
    val rejectedBody =
      "{\"contract_version\":\"0.5\",\"phase_id\":\"audit\",\"status\":\"completed\"," +
        "\"summary\":\"SKILL187-GATEOUTPUT-SENTINEL\",\"produced_outputs\":{\"gaps\":[]}}"
    var auditAttempts = 0
    val harness = runnerHarness(
      RuntimeHarnessConfig(
        launcher = RuntimeRecordingLauncher { request ->
          val phaseId = phaseIdFromPrompt(requireNotNull(request.skillRunRequest.promptOverride))
          if (phaseId != "audit") return@RuntimeRecordingLauncher facts(defaultPhaseOutput(request))
          auditAttempts += 1
          facts(if (auditAttempts == 1) rejectedBody else defaultPhaseOutput(request))
        },
        validator = object : FeatureTaskRuntimePhaseOutputValidator {
          override fun validatePhaseOutputText(phaseOutputText: String, sourceLabel: String) {
            if (sourceLabel != "audit") return
            if (phaseOutputText.contains("SKILL187-GATEOUTPUT-SENTINEL")) {
              throw InvalidFeatureTaskRuntimePhaseOutputSchemaError(
                sourceLabel = sourceLabel,
                reason = valueBearingReason,
                payloadFreeReason = payloadFreeConstraint,
              )
            }
          }
        },
      ),
    )

    val blocked = assertIs<FeatureTaskRuntimeRunReport.Blocked>(harness.runner.run(harness.request()))
    assertContains(blocked.blockedReason, "does not participate in a fix loop")
    assertEquals(1, auditAttempts)
    val diagnostic = harness.io.database.rejectedDiagnostics().single { it.metadata.phaseId == "audit" }
    assertEquals(rejectedBody.encodeToByteArray().toList(), diagnostic.payload?.toList())
  }

  private fun auditPrompts(harness: RunnerHarness): List<String> = harness.launcher.requests
    .map { requireNotNull(it.skillRunRequest.promptOverride) }
    .filter { phaseIdFromPrompt(it) == "audit" }

  private fun rejectingHarness(error: (String) -> Throwable): RunnerHarness {
    var auditAttempts = 0
    return runnerHarness(
      RuntimeHarnessConfig(
        validator = object : FeatureTaskRuntimePhaseOutputValidator {
          override fun validatePhaseOutputText(phaseOutputText: String, sourceLabel: String) {
            if (sourceLabel != "audit") return
            auditAttempts += 1
            if (auditAttempts < 2) throw error(sourceLabel)
          }
        },
      ),
    )
  }
}
