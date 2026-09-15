package skillbill.engine.featuretask

import skillbill.engine.IMPLEMENT_OUTPUT
import skillbill.engine.PLAN_OUTPUT
import skillbill.engine.PREPLAN_OUTPUT
import skillbill.engine.RuntimeHarnessConfig
import skillbill.engine.WORKFLOW_ID
import skillbill.engine.auditGapsFoundOutput
import skillbill.engine.featuretask.model.FeatureTaskRuntimePhaseStateRequest
import skillbill.engine.featuretask.model.FeatureTaskRuntimeRunReport
import skillbill.engine.runnerHarness
import skillbill.engine.satisfiedAuditLauncher
import skillbill.workflow.model.WorkflowStepStatus
import skillbill.workflow.taskruntime.FeatureTaskRuntimePhaseWorkflowDefinition
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseLedgerAction
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseLedgerEntry
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseOutput
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimePhaseRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeatureTaskRuntimeRunStateReconstructionTest {
  @Test
  fun `completed phase and output views cannot mutate run state`() {
    val state = FeatureTaskRuntimeRunState(
      initialRecords = emptyMap(),
      transitions = FeatureTaskRuntimePhaseWorkflowDefinition.transitions,
      outputValidator = AlwaysValidValidator,
    )
    val output = FeatureTaskRuntimePhaseOutput(
      phaseId = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT,
      iteration = 1,
      payload = "{}",
    )

    state.recordCompleted(output)
    val outputs = state.outputs().toMutableList()
    outputs.clear()

    assertEquals(listOf(output), state.outputs())
    assertEquals(listOf(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT), state.completedPhaseIds())
  }

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
    assertFalse(state.isComplete("audit"))
  }

  @Test
  fun `normalizeForStatelessAudit applies record and ledger normalization together`() {
    val rawAudit = auditPhaseRecord(
      status = WorkflowStepStatus.BLOCKED,
      loopId = FeatureTaskRuntimePhaseWorkflowDefinition.AUDIT_GAP_LOOP_ID,
      blockedReason = "legacy audit-gap block",
    )
    val rawLedger = listOf(
      FeatureTaskRuntimePhaseLedgerEntry(
        action = FeatureTaskRuntimePhaseLedgerAction.BLOCKED,
        sequenceNumber = 1,
        timestamp = "2026-01-01T00:00:00Z",
        phaseId = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT,
        attemptCount = 1,
      ),
    )
    val normalized = FeatureTaskRuntimeRunStateReconstruction.normalizeForStatelessAudit(
      mapOf(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT to rawAudit),
      rawLedger,
    )
    assertEquals(WorkflowStepStatus.PENDING, normalized.records.getValue("audit").status)
    assertTrue(normalized.ledger.isEmpty())
  }

  @Test
  fun `normalize ledger drops retired audit gap loop edges and legacy audit blocks`() {
    val rawAudit = auditPhaseRecord(
      status = WorkflowStepStatus.BLOCKED,
      loopId = FeatureTaskRuntimePhaseWorkflowDefinition.AUDIT_GAP_LOOP_ID,
      blockedReason = "legacy audit-gap block",
    )
    val ledger = listOf(
      FeatureTaskRuntimePhaseLedgerEntry(
        action = FeatureTaskRuntimePhaseLedgerAction.LOOP_EDGE,
        sequenceNumber = 1,
        timestamp = "2026-01-01T00:00:00Z",
        phaseId = "implement",
        attemptCount = 1,
        loopId = FeatureTaskRuntimePhaseWorkflowDefinition.AUDIT_GAP_LOOP_ID,
        edgeIteration = 1,
      ),
      FeatureTaskRuntimePhaseLedgerEntry(
        action = FeatureTaskRuntimePhaseLedgerAction.BLOCKED,
        sequenceNumber = 2,
        timestamp = "2026-01-01T00:00:01Z",
        phaseId = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT,
        attemptCount = 1,
      ),
    )
    val normalized = FeatureTaskRuntimeRunStateReconstruction.normalizeLedgerForStatelessAudit(
      ledger,
      mapOf(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT to rawAudit),
    )
    assertTrue(normalized.isEmpty())
  }

  @Test
  fun `normalize ledger is idempotent on already normalized records`() {
    val rawAudit = auditPhaseRecord(
      status = WorkflowStepStatus.BLOCKED,
      loopId = FeatureTaskRuntimePhaseWorkflowDefinition.AUDIT_GAP_LOOP_ID,
      blockedReason = "legacy audit-gap block",
    )
    val rawRecords = mapOf(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT to rawAudit)
    val rawLedger = listOf(
      FeatureTaskRuntimePhaseLedgerEntry(
        action = FeatureTaskRuntimePhaseLedgerAction.BLOCKED,
        sequenceNumber = 1,
        timestamp = "2026-01-01T00:00:00Z",
        phaseId = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT,
        attemptCount = 1,
      ),
    )
    val firstPass = FeatureTaskRuntimeRunStateReconstruction.normalizeForStatelessAudit(rawRecords, rawLedger)
    val secondPass = FeatureTaskRuntimeRunStateReconstruction.normalizeLedgerForStatelessAudit(
      rawLedger,
      firstPass.records,
    )
    assertTrue(secondPass.isEmpty())
    assertEquals(firstPass.ledger, secondPass)
  }

  @Test
  fun `fix loop budget bases retain operator retry and ignore retired audit gap edges`() {
    val rawAudit = auditPhaseRecord(
      status = WorkflowStepStatus.BLOCKED,
      loopId = FeatureTaskRuntimePhaseWorkflowDefinition.AUDIT_GAP_LOOP_ID,
      blockedReason = "legacy audit-gap block",
    )
    val rawRecords = mapOf(
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT to FeatureTaskRuntimePhaseRecord(
        phaseId = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT,
        status = WorkflowStepStatus.COMPLETED,
        attemptCount = 2,
        startedAt = "2026-01-01T00:00:00Z",
        resolvedAgentId = "claude",
        loopId = FeatureTaskRuntimePhaseWorkflowDefinition.AUDIT_GAP_LOOP_ID,
        edgeIteration = 1,
        finishedAt = "2026-01-01T00:01:00Z",
      ),
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT to rawAudit,
    )
    val rawLedger = listOf(
      FeatureTaskRuntimePhaseLedgerEntry(
        action = FeatureTaskRuntimePhaseLedgerAction.LOOP_EDGE,
        sequenceNumber = 1,
        timestamp = "2026-01-01T00:00:00Z",
        phaseId = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT,
        attemptCount = 2,
        loopId = FeatureTaskRuntimePhaseWorkflowDefinition.AUDIT_GAP_LOOP_ID,
        edgeIteration = 1,
      ),
      FeatureTaskRuntimePhaseLedgerEntry(
        action = FeatureTaskRuntimePhaseLedgerAction.RETRY,
        sequenceNumber = 2,
        timestamp = "2026-01-01T00:00:01Z",
        phaseId = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT,
        attemptCount = 2,
      ),
    )
    val bases = FeatureTaskRuntimeRunStateReconstruction.reconstructFixLoopBudgetBases(
      ReconstructFixLoopBudgetBasesArgs(
        transitions = FeatureTaskRuntimePhaseWorkflowDefinition.transitions,
        edgeIterationByLoop = emptyMap(),
        initialRecords = rawRecords,
        initialLedger = rawLedger,
        completed = setOf(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT),
        gateInvalidatedPhases = emptySet(),
        nextIteration = { 1 },
      ),
    )
    assertEquals(mapOf(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_AUDIT to 2), bases)
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
