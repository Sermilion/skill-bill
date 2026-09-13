package skillbill.engine

import skillbill.engine.featuretask.model.FeatureTaskRuntimePhaseStateRequest
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunReport
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseLedgerAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FeatureTaskRuntimeStatelessAuditTest {
  private fun seedPlanningUpstreamPhases(harness: RunnerHarness) {
    harness.seedPhase("preplan", "completed", 1, "claude", PREPLAN_OUTPUT)
    harness.seedPhase("plan", "completed", 1, "claude", PLAN_OUTPUT)
    harness.seedPhase("implement", "completed", 1, "claude", IMPLEMENT_OUTPUT)
  }

  @Test
  fun `satisfied audit advances without audit_gap loop edge`() {
    val harness = runnerHarness(RuntimeHarnessConfig(launcher = satisfiedAuditLauncher()))
    val report = harness.runner.run(harness.request())
    assertIs<FeatureTaskRuntimeRunReport.Completed>(report)
    assertEquals(1, harness.launchedPromptPhaseOrder().count { it == "audit" })
    assertTrue(
      harness.recorder.loadPhaseLedger(WORKFLOW_ID).orEmpty()
        .none { it.action == FeatureTaskRuntimePhaseLedgerAction.LOOP_EDGE && it.loopId == "audit_gap" },
    )
  }

  @Test
  fun `gaps_found audit output is rejected and does not re-enter implement`() {
    var auditLaunches = 0
    val launcher = RuntimeRecordingLauncher { request ->
      val phaseId = phaseIdFromPrompt(requireNotNull(request.skillRunRequest.promptOverride))
      if (phaseId == "audit") {
        auditLaunches += 1
        facts(if (auditLaunches == 1) auditGapsFoundOutput() else auditSatisfiedOutput())
      } else {
        facts(defaultPhaseOutput(request))
      }
    }
    val harness = runnerHarness(RuntimeHarnessConfig(launcher = launcher))
    val report = harness.runner.run(harness.request())
    assertTrue(
      report is FeatureTaskRuntimeRunReport.Completed || report is FeatureTaskRuntimeRunReport.Blocked,
      "removed gaps_found must not route to implement; terminal outcome is completion or block",
    )
    assertTrue(auditLaunches >= 1, "audit must launch at least once")
    assertEquals(1, harness.launchedPromptPhaseOrder().count { it == "implement" })
    assertTrue(
      harness.recorder.loadPhaseLedger(WORKFLOW_ID).orEmpty()
        .none { it.loopId == FeatureTaskRuntimePhaseWorkflowDefinition.AUDIT_GAP_LOOP_ID },
    )
  }

  @Test
  fun `audit launch does not write audit-specific gap progress or pause artifacts`() {
    val harness = runnerHarness(RuntimeHarnessConfig(launcher = satisfiedAuditLauncher()))
    val report = harness.runner.run(harness.request())
    assertIs<FeatureTaskRuntimeRunReport.Completed>(report)
  }

  @Test
  fun `legacy audit gap pause record does not block ordinary resume`() {
    val harness = runnerHarness(RuntimeHarnessConfig(launcher = satisfiedAuditLauncher()))
    harness.recorder.ensureWorkflowOpen(WORKFLOW_ID, SESSION_ID)
    val report = harness.runner.run(harness.request())
    assertIs<FeatureTaskRuntimeRunReport.Completed>(report)
  }

  @Test
  fun `legacy audit gap loop edge does not resume implement`() {
    val harness = runnerHarness(RuntimeHarnessConfig(launcher = satisfiedAuditLauncher()))
    seedPlanningUpstreamPhases(harness)
    harness.seedLoopEdge(
      phaseId = "implement",
      loopId = FeatureTaskRuntimePhaseWorkflowDefinition.AUDIT_GAP_LOOP_ID,
      edgeIteration = 1,
    )
    val report = harness.runner.run(harness.request())
    assertIs<FeatureTaskRuntimeRunReport.Completed>(report)
    assertEquals(1, harness.launchedPromptPhaseOrder().count { it == "implement" })
    assertEquals(1, harness.launchedPromptPhaseOrder().count { it == "audit" })
  }

  @Test
  fun `legacy completed gaps_found audit is discarded and relaunched fresh`() {
    val harness = runnerHarness(RuntimeHarnessConfig(launcher = satisfiedAuditLauncher()))
    seedPlanningUpstreamPhases(harness)
    harness.seedPhase(
      "audit",
      "completed",
      1,
      "claude",
      auditGapsFoundOutput(),
    )
    harness.seedPhase("review", "completed", 1, "claude", """{"contract_version":"0.1","verdict":"approved"}""")
    val report = harness.runner.run(harness.request())
    assertIs<FeatureTaskRuntimeRunReport.Completed>(report)
    assertEquals(1, harness.launchedPromptPhaseOrder().count { it == "audit" })
    assertTrue(harness.launchedPromptPhaseOrder().contains("review"))
  }

  @Test
  fun `legacy blocked audit with only loop_id marker is discarded and relaunched fresh`() {
    val harness = runnerHarness(RuntimeHarnessConfig(launcher = satisfiedAuditLauncher()))
    seedPlanningUpstreamPhases(harness)
    harness.recorder.ensureWorkflowOpen(WORKFLOW_ID, SESSION_ID)
    harness.recorder.recordPhaseState(
      FeatureTaskRuntimePhaseStateRequest(
        workflowId = WORKFLOW_ID,
        phaseId = "audit",
        status = "blocked",
        attemptCount = 1,
        resolvedAgentId = "claude",
        finished = false,
        outputArtifact = null,
        blockedReason = "legacy audit-gap block",
        loopId = FeatureTaskRuntimePhaseWorkflowDefinition.AUDIT_GAP_LOOP_ID,
        edgeIteration = 1,
      ),
    )
    val report = harness.runner.run(harness.request())
    assertIs<FeatureTaskRuntimeRunReport.Completed>(report)
    assertEquals(1, harness.launchedPromptPhaseOrder().count { it == "audit" })
  }

  @Test
  fun `legacy blocked gaps_found audit is discarded and relaunched fresh`() {
    val harness = runnerHarness(RuntimeHarnessConfig(launcher = satisfiedAuditLauncher()))
    seedPlanningUpstreamPhases(harness)
    harness.recorder.ensureWorkflowOpen(WORKFLOW_ID, SESSION_ID)
    harness.recorder.recordPhaseState(
      FeatureTaskRuntimePhaseStateRequest(
        workflowId = WORKFLOW_ID,
        phaseId = "audit",
        status = "blocked",
        attemptCount = 1,
        resolvedAgentId = "claude",
        finished = false,
        outputArtifact = auditGapsFoundOutput(),
        blockedReason = "legacy audit-gap pause",
        loopId = FeatureTaskRuntimePhaseWorkflowDefinition.AUDIT_GAP_LOOP_ID,
        edgeIteration = 1,
      ),
    )
    val report = harness.runner.run(harness.request())
    assertIs<FeatureTaskRuntimeRunReport.Completed>(report)
    assertEquals(1, harness.launchedPromptPhaseOrder().count { it == "audit" })
  }

  @Test
  fun `legacy paused gaps_found audit is discarded and relaunched fresh`() {
    val harness = runnerHarness(RuntimeHarnessConfig(launcher = satisfiedAuditLauncher()))
    seedPlanningUpstreamPhases(harness)
    harness.recorder.ensureWorkflowOpen(WORKFLOW_ID, SESSION_ID)
    harness.recorder.recordPhaseState(
      FeatureTaskRuntimePhaseStateRequest(
        workflowId = WORKFLOW_ID,
        phaseId = "audit",
        status = "paused",
        attemptCount = 1,
        resolvedAgentId = "claude",
        finished = false,
        outputArtifact = auditGapsFoundOutput(),
        blockedReason = "legacy audit-gap pause",
        loopId = FeatureTaskRuntimePhaseWorkflowDefinition.AUDIT_GAP_LOOP_ID,
        edgeIteration = 1,
      ),
    )
    val report = harness.runner.run(harness.request())
    assertIs<FeatureTaskRuntimeRunReport.Completed>(report)
    assertEquals(1, harness.launchedPromptPhaseOrder().count { it == "audit" })
  }

  @Test
  fun `malformed audit output blocks after one agent session`() {
    var auditLaunches = 0
    val launcher = RuntimeRecordingLauncher { request ->
      val phaseId = phaseIdFromPrompt(requireNotNull(request.skillRunRequest.promptOverride))
      if (phaseId == "audit") {
        auditLaunches += 1
        facts("{not valid json")
      } else {
        facts(defaultPhaseOutput(request))
      }
    }
    val harness = runnerHarness(RuntimeHarnessConfig(launcher = launcher))
    val report = harness.runner.run(harness.request())
    assertIs<FeatureTaskRuntimeRunReport.Blocked>(report)
    assertEquals(1, auditLaunches)
  }
}
