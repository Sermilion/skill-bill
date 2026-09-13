package skillbill.engine.featuretask

import skillbill.engine.featuretask.AlwaysValidValidator
import skillbill.engine.auditGapsFoundOutput
import skillbill.engine.featuretask.model.FeatureTaskRuntimePhaseStateRequest
import skillbill.engine.runnerHarness
import skillbill.engine.RuntimeHarnessConfig
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunReport
import skillbill.engine.satisfiedAuditLauncher
import skillbill.engine.IMPLEMENT_OUTPUT
import skillbill.engine.PLAN_OUTPUT
import skillbill.engine.PREPLAN_OUTPUT
import skillbill.engine.WORKFLOW_ID
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeatureTaskRuntimeRunStateReconstructionTest {
  @Test
  fun `legacy blocked audit with loop id only normalizes to pending`() {
    val raw = auditPhaseRecord(
      status = WorkflowStepStatus.BLOCKED,
      loopId = FeatureTaskRuntimePhaseWorkflowDefinition.AUDIT_GAP_LOOP_ID,
      edgeIteration = 1,
      blockedReason = "legacy audit-gap block",
    )
    val normalized = FeatureTaskRuntimeRunStateReconstruction
      .normalizeInitialRecordsForStatelessAudit(mapOf("audit" to raw))
      .getValue("audit")
    assertEquals(WorkflowStepStatus.PENDING, normalized.status)
    assertNull(normalized.loopId)
    assertNull(normalized.outputArtifact)
    assertNull(normalized.blockedReason)
    assertFalse(FeatureTaskRuntimeRunStateReconstruction.isLegacyAuditGapPersistedBlock(normalized))
  }

  @Test
  fun `legacy completed gaps_found audit normalizes to pending`() {
    val raw = auditPhaseRecord(
      status = WorkflowStepStatus.COMPLETED,
      outputArtifact = auditGapsFoundOutput(),
    )
    val normalized = FeatureTaskRuntimeRunStateReconstruction
      .normalizeInitialRecordsForStatelessAudit(mapOf("audit" to raw))
      .getValue("audit")
    assertEquals(WorkflowStepStatus.PENDING, normalized.status)
    assertNull(normalized.outputArtifact)
  }

  @Test
  fun `run state drops legacy blocked audit from blocked records`() {
    val harness = runnerHarness(RuntimeHarnessConfig(launcher = satisfiedAuditLauncher()))
    harness.seedPhase("preplan", "completed", 1, "claude", PREPLAN_OUTPUT)
    harness.seedPhase("plan", "completed", 1, "claude", PLAN_OUTPUT)
    harness.seedPhase("implement", "completed", 1, "claude", IMPLEMENT_OUTPUT)
    harness.recorder.ensureWorkflowOpen(WORKFLOW_ID, "ftr-test-001")
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
    val loaded = requireNotNull(harness.recorder.loadPhaseRecords(WORKFLOW_ID))["audit"]
    assertEquals(FeatureTaskRuntimePhaseWorkflowDefinition.AUDIT_GAP_LOOP_ID, loaded?.loopId)
    val state = FeatureTaskRuntimeRunState(
      initialRecords = harness.recorder.loadPhaseRecords(WORKFLOW_ID).orEmpty(),
      transitions = FeatureTaskRuntimePhaseWorkflowDefinition.transitions,
      outputValidator = AlwaysValidValidator,
    )
    assertNull(state.persistedBlockedReason("audit"))
    assertEquals(WorkflowStepStatus.PENDING, state.recordFor("audit")?.status)
    assertFalse("audit" in state.completed)
  }

  @Test
  fun `legacy blocked audit run completes after fresh relaunch`() {
    val harness = runnerHarness(RuntimeHarnessConfig(launcher = satisfiedAuditLauncher()))
    harness.seedPhase("preplan", "completed", 1, "claude", PREPLAN_OUTPUT)
    harness.seedPhase("plan", "completed", 1, "claude", PLAN_OUTPUT)
    harness.seedPhase("implement", "completed", 1, "claude", IMPLEMENT_OUTPUT)
    harness.recorder.ensureWorkflowOpen(WORKFLOW_ID, "ftr-test-001")
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
    assertTrue(report is FeatureTaskRuntimeRunReport.Completed)
    assertEquals(1, harness.launchedPromptPhaseOrder().count { it == "audit" })
  }

  private fun auditPhaseRecord(
    status: WorkflowStepStatus,
    loopId: String? = null,
    edgeIteration: Int? = null,
    blockedReason: String? = null,
    outputArtifact: String? = null,
  ): FeatureTaskRuntimePhaseRecord = FeatureTaskRuntimePhaseRecord(
    phaseId = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT,
    status = status,
    attemptCount = 1,
    startedAt = "2026-01-01T00:00:00Z",
    resolvedAgentId = "claude",
    loopId = loopId,
    edgeIteration = edgeIteration,
    blockedReason = blockedReason,
    outputArtifact = outputArtifact,
    finishedAt = if (status == WorkflowStepStatus.COMPLETED) "2026-01-01T00:01:00Z" else null,
  )
}
