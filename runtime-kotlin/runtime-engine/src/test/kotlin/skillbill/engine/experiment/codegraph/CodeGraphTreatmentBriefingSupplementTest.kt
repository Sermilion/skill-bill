package skillbill.engine.experiment.codegraph

import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeGoalContinuationContext
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeRunRequest
import skillbill.experiment.model.ExperimentArmId
import skillbill.infrastructure.host.experiment.codegraph.InMemoryCodeGraphUsageLedger
import skillbill.ports.experiment.codegraph.CodeGraphRetrievalPort
import skillbill.ports.experiment.codegraph.model.CodeGraphCandidateHit
import skillbill.ports.experiment.codegraph.model.CodeGraphQueryRequest
import skillbill.ports.experiment.codegraph.model.CodeGraphQueryResult
import skillbill.ports.workflow.gitops.model.GoalSubtaskReviewBaseline
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimeFeatureSize
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimeRunInvariants
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CodeGraphTreatmentBriefingSupplementTest {
  @Test
  fun `control arm receives no graph supplement`() {
    val supplement = CodeGraphTreatmentBriefingSupplement(fakePort(), InMemoryCodeGraphUsageLedger())
    val request = runRequest(
      arm = ExperimentArmId.CONTROL,
      capabilities = setOf("codegraph"),
    )
    assertEquals("", supplement.phaseSupplement(request, FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PLAN))
  }

  @Test
  fun `treatment arm surfaces bounded candidates`() {
    val supplement = CodeGraphTreatmentBriefingSupplement(fakePort(), InMemoryCodeGraphUsageLedger())
    val request = runRequest(
      arm = ExperimentArmId.TREATMENT,
      capabilities = setOf("codegraph"),
    )
    val text = supplement.phaseSupplement(request, FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_IMPLEMENT)
    assertContains(text, "CodeGraph candidates")
    assertContains(text, "Caller.kt")
  }

  @Test
  fun `omitted capability performs no graph setup`() {
    val ledger = InMemoryCodeGraphUsageLedger()
    val supplement = CodeGraphTreatmentBriefingSupplement(fakePort(), ledger)
    val request = runRequest(arm = ExperimentArmId.TREATMENT, capabilities = emptySet())
    assertEquals("", supplement.phaseSupplement(request, FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PLAN))
    assertTrue(ledger.snapshot("pair-1").notExercised)
  }

  @Test
  fun `missing retrieval falls back with an attributable degradation`() {
    val ledger = InMemoryCodeGraphUsageLedger()
    val supplement = CodeGraphTreatmentBriefingSupplement(null, ledger)
    val text = supplement.phaseSupplement(
      runRequest(arm = ExperimentArmId.TREATMENT, capabilities = setOf("codegraph")),
      FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PLAN,
    )

    assertContains(text, "retrieval is unavailable")
    assertTrue(ledger.snapshot("pair-1").degraded)
  }

  private fun fakePort(): CodeGraphRetrievalPort = object : CodeGraphRetrievalPort {
    override fun query(request: CodeGraphQueryRequest): CodeGraphQueryResult = CodeGraphQueryResult(
      hits = listOf(
        CodeGraphCandidateHit(
          name = "invoke",
          file = "Caller.kt",
          kind = "call",
        ),
      ),
      receipt = emptyMap(),
    )
  }

  private fun runRequest(arm: ExperimentArmId, capabilities: Set<String>): FeatureTaskRuntimeRunRequest {
    val repo = Files.createTempDirectory("codegraph-supplement")
    return FeatureTaskRuntimeRunRequest(
      issueKey = "SKILL-TEST",
      workflowId = "wf-1",
      sessionId = "session",
      runInvariants = FeatureTaskRuntimeRunInvariants(
        specReference = "spec.md",
        featureSize = FeatureTaskRuntimeFeatureSize.SMALL,
        acceptanceCriteria = listOf("AC-1"),
        mandatesAndOverrides = emptyList(),
      ),
      invokedAgentId = "cursor",
      repoRoot = repo,
      goalContinuation = FeatureTaskRuntimeGoalContinuationContext(
        parentIssueKey = "PARENT",
        subtaskId = 1,
        goalBranch = "feat/test",
        suppressPr = true,
        reviewBaseline = GoalSubtaskReviewBaseline("a".repeat(40), emptyList()),
        experimentArmId = arm,
        experimentPairId = "pair-1",
        experimentTreatmentCapabilities = capabilities,
      ),
    )
  }
}
