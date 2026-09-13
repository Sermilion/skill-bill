package skillbill.engine

import skillbill.application.realFeatureTaskRuntimePhaseOutputValidator
import skillbill.engine.featuretask.model.FeatureTaskRuntimePhaseStateRequest
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunReport
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.model.FEATURE_TASK_RUNTIME_DELIVERED_PROJECTIONS_ARTIFACT_KEY
import skillbill.workflow.taskruntime.model.FEATURE_TASK_RUNTIME_PHASE_BRIEFINGS_ARTIFACT_KEY
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
  fun `completed audit remains completed when obsolete audit progress is unreadable`() {
    val harness = runnerHarness(RuntimeHarnessConfig(launcher = satisfiedAuditLauncher()))
    assertIs<FeatureTaskRuntimeRunReport.Completed>(harness.runner.run(harness.request()))
    val auditRecord = harness.recorder.loadPhaseRecords(WORKFLOW_ID)?.get("audit")
    val auditLaunchCount = harness.launchedPromptPhaseOrder().count { it == "audit" }
    val retiredProgressKey = "feature_task_runtime_audit_gap_progress"
    harness.repository.replaceTaskRuntimeArtifacts(
      WORKFLOW_ID,
      harness.repository.taskRuntimeArtifacts(WORKFLOW_ID) + (retiredProgressKey to "unreadable old progress"),
    )
    assertIs<FeatureTaskRuntimeRunReport.Completed>(harness.runner.run(harness.request()))
    assertEquals(auditLaunchCount, harness.launchedPromptPhaseOrder().count { it == "audit" })
    assertEquals(auditRecord, harness.recorder.loadPhaseRecords(WORKFLOW_ID)?.get("audit"))
    assertEquals("unreadable old progress", harness.repository.taskRuntimeArtifacts(WORKFLOW_ID)[retiredProgressKey])
  }

  @Test
  fun `gaps_found audit output is rejected and does not re-enter implement`() {
    var auditLaunches = 0
    val launcher = RuntimeRecordingLauncher { request ->
      val phaseId = phaseIdFromPrompt(requireNotNull(request.skillRunRequest.promptOverride))
      if (phaseId == "audit") {
        auditLaunches += 1
        facts(auditGapsFoundOutput())
      } else {
        facts(defaultPhaseOutput(request))
      }
    }
    val harness = runnerHarness(
      RuntimeHarnessConfig(launcher = launcher, validator = realFeatureTaskRuntimePhaseOutputValidator),
    )
    val report = harness.runner.run(harness.request())
    assertIs<FeatureTaskRuntimeRunReport.Blocked>(report)
    assertEquals(1, auditLaunches)
    assertTrue("review" !in harness.launchOrder())
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
    assertTrue(harness.repository.taskRuntimeArtifacts(WORKFLOW_ID).keys.none { it.contains("audit_gap") })
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
    assertEquals(0, harness.launchedPromptPhaseOrder().count { it == "implement" })
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
    val launched = harness.launchOrder()
    assertEquals(1, launched.count { it == "review" })
    assertTrue(launched.indexOf("audit") < launched.indexOf("review"))
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
  fun `interrupted legacy implement remediation resumes the audit using retained implementation output`() {
    val harness = runnerHarness(RuntimeHarnessConfig(launcher = satisfiedAuditLauncher()))
    seedPlanningUpstreamPhases(harness)
    harness.seedLoopEdge(
      phaseId = "implement",
      loopId = FeatureTaskRuntimePhaseWorkflowDefinition.AUDIT_GAP_LOOP_ID,
      edgeIteration = 1,
    )
    harness.recorder.recordPhaseState(
      FeatureTaskRuntimePhaseStateRequest(
        workflowId = WORKFLOW_ID,
        phaseId = "implement",
        status = "blocked",
        attemptCount = 2,
        resolvedAgentId = "claude",
        finished = false,
        blockedReason = "Interrupted legacy gap repair",
        loopId = FeatureTaskRuntimePhaseWorkflowDefinition.AUDIT_GAP_LOOP_ID,
        edgeIteration = 1,
      ),
    )
    val legacyArtifacts = mapOf(
      "feature_task_runtime_audit_gap_pause" to "unreadable pause",
      "feature_task_runtime_audit_gap_progress" to "unreadable progress",
      "feature_task_runtime_audit_generations" to "dangling generation",
    )
    harness.repository.replaceTaskRuntimeArtifacts(
      WORKFLOW_ID,
      harness.repository.taskRuntimeArtifacts(WORKFLOW_ID) + legacyArtifacts + mapOf(
        FEATURE_TASK_RUNTIME_PHASE_BRIEFINGS_ARTIFACT_KEY to mapOf("audit" to "retired unreadable briefing"),
        FEATURE_TASK_RUNTIME_DELIVERED_PROJECTIONS_ARTIFACT_KEY to
          mapOf("$WORKFLOW_ID|audit|1|plan#1|legacy" to "retired unreadable projection"),
      ),
    )
    val result = harness.runner.run(harness.request())
    assertIs<FeatureTaskRuntimeRunReport.Completed>(result)
    assertEquals(0, harness.launchedPromptPhaseOrder().count { it == "implement" })
    assertEquals(1, harness.launchedPromptPhaseOrder().count { it == "audit" })
    val artifacts = harness.repository.taskRuntimeArtifacts(WORKFLOW_ID)
    assertEquals(legacyArtifacts, artifacts.filterKeys { it in legacyArtifacts })
    assertTrue(harness.recorder.loadPhaseBriefings(WORKFLOW_ID).orEmpty().keys.none { it == "audit" })
    assertTrue(harness.recorder.loadDeliveredProjections(WORKFLOW_ID).orEmpty().keys.none { it == "audit" })
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
    val harness = runnerHarness(
      RuntimeHarnessConfig(launcher = launcher, validator = realFeatureTaskRuntimePhaseOutputValidator),
    )
    val report = harness.runner.run(harness.request())
    assertIs<FeatureTaskRuntimeRunReport.Blocked>(report)
    assertEquals(1, auditLaunches)
  }
}
