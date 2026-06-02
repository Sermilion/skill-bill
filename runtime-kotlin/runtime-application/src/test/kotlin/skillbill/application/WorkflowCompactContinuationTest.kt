package skillbill.application

import skillbill.application.model.WorkflowContinueResult
import skillbill.application.model.WorkflowFamilyKind
import skillbill.application.model.WorkflowOpenResult
import skillbill.application.model.WorkflowUpdateRequest
import skillbill.ports.workflow.UnavailableDecompositionManifestFileStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkflowCompactContinuationTest {
  @Test
  fun `continueWorkflow compact projection inlines small current-step artifacts`() {
    val service = newService()
    val opened = assertIs<WorkflowOpenResult.Ok>(service.open(WorkflowFamilyKind.IMPLEMENT, sessionId = "fis-001"))
    service.update(
      WorkflowFamilyKind.IMPLEMENT,
      WorkflowUpdateRequest(
        workflowId = opened.workflowId,
        workflowStatus = "blocked",
        currentStepId = "implement",
        stepUpdates = listOf(
          mapOf("step_id" to "implement", "status" to "blocked", "attempt_count" to 1),
        ),
        artifactsPatch = mapOf(
          "branch" to mapOf("branch_name" to "feat/demo"),
          "plan" to mapOf("mode" to "implement", "task_count" to 1),
          "preplan_digest" to mapOf("risk" to "low"),
        ),
      ),
    )

    val standard = assertIs<WorkflowContinueResult.Standard>(
      service.continueWorkflow(WorkflowFamilyKind.IMPLEMENT, opened.workflowId),
    )
    val compact = standard.view.compact

    assertEquals("reopened", compact.continueStatus)
    assertEquals("reopened", standard.view.continueStatus)
    assertEquals("blocked", compact.workflowStatusBeforeContinue)
    assertEquals("blocked", standard.view.workflowStatusBeforeContinue)
    assertEquals(opened.workflowId, compact.workflowId)
    assertEquals("bill-feature-task", compact.skillName)
    assertEquals("implement", compact.resumeStepId)
    assertEquals("Step 4: Execute Plan", compact.resumeStepLabel)
    assertEquals(listOf("plan", "preplan_digest"), compact.requiredArtifactKeys)
    assertEquals(listOf("branch", "plan", "preplan_digest"), compact.availableArtifactKeys)
    assertEquals(listOf("branch"), compact.omittedArtifactKeys)
    assertTrue(compact.continuationBrief.contains(opened.workflowId))
    assertTrue(compact.continuationEntryPrompt.contains("Continue status: reopened"))
    assertTrue(compact.continuationBrief.contains("`current_step_artifacts`"))
    assertTrue(compact.continuationEntryPrompt.contains("Current-step artifacts: plan, preplan_digest"))
    assertTrue(compact.continuationEntryPrompt.contains("Omitted artifact keys: branch"))
    assertTrue(compact.continuationBrief.contains("Omitted artifact keys (branch) require read-only inspection"))
    assertFalse(compact.continuationBrief.contains("`step_artifacts`"))
    assertFalse(compact.continuationEntryPrompt.contains("Recovered artifacts:"))
    val planSummary = compact.currentStepArtifacts.single { it.key == "plan" }
    assertTrue(planSummary.present)
    assertTrue(planSummary.inline)
    assertFalse(planSummary.truncated)
    assertEquals(mapOf("mode" to "implement", "task_count" to 1), planSummary.value)
  }

  @Test
  fun `continueWorkflow compact projection summarizes large current-step artifacts`() {
    val service = newService()
    val opened = assertIs<WorkflowOpenResult.Ok>(service.open(WorkflowFamilyKind.IMPLEMENT, sessionId = "fis-001"))
    service.update(
      WorkflowFamilyKind.IMPLEMENT,
      WorkflowUpdateRequest(
        workflowId = opened.workflowId,
        workflowStatus = "blocked",
        currentStepId = "implement",
        stepUpdates = listOf(
          mapOf("step_id" to "implement", "status" to "blocked", "attempt_count" to 1),
        ),
        artifactsPatch = mapOf(
          "plan" to mapOf("mode" to "implement", "body" to "x".repeat(5000)),
          "preplan_digest" to mapOf("risk" to "low"),
        ),
      ),
    )

    val standard = assertIs<WorkflowContinueResult.Standard>(
      service.continueWorkflow(WorkflowFamilyKind.IMPLEMENT, opened.workflowId),
    )
    val planSummary = standard.view.compact.currentStepArtifacts.single { it.key == "plan" }

    assertTrue(planSummary.present)
    assertFalse(planSummary.inline)
    assertTrue(requireNotNull(planSummary.sizeBytes) > 4096)
    assertNull(planSummary.value)
    assertEquals(1024, requireNotNull(planSummary.preview).length)
    assertTrue(planSummary.truncated)
    assertTrue(planSummary.omitted)
    assertEquals("artifact_exceeds_inline_limit", planSummary.omissionReason)
    assertTrue(standard.view.compact.continuationEntryPrompt.contains("Current-step artifacts: plan, preplan_digest"))
    assertFalse(standard.view.compact.continuationEntryPrompt.contains("Recovered artifacts:"))
  }
}

private fun newService(): WorkflowService = WorkflowService(
  database = FakeDatabaseSessionFactory(InMemoryWorkflowStates()),
  decompositionManifestFileStore = UnavailableDecompositionManifestFileStore,
  workflowSnapshotValidator = testWorkflowSnapshotValidator,
  decompositionManifestValidator = testDecompositionManifestValidator,
)
