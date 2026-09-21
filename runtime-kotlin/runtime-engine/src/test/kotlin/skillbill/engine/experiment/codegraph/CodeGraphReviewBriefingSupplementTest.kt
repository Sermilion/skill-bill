package skillbill.engine.experiment.codegraph

import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeGoalContinuationContext
import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeRunRequest
import skillbill.experiment.model.ExperimentArmId
import skillbill.infrastructure.host.experiment.codegraph.InMemoryCodeGraphUsageLedger
import skillbill.infrastructure.workflow.review.broker.FileSystemReviewEvidenceBroker
import skillbill.ports.experiment.codegraph.CodeGraphRetrievalPort
import skillbill.ports.experiment.codegraph.model.CodeGraphCandidateHit
import skillbill.ports.experiment.codegraph.model.CodeGraphQueryRequest
import skillbill.ports.experiment.codegraph.model.CodeGraphQueryResult
import skillbill.ports.review.evidence.ReviewEvidenceBrokerFactory
import skillbill.ports.workflow.gitops.model.GoalSubtaskReviewBaseline
import skillbill.ports.workflow.gitops.model.GoalSubtaskReviewInput
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimeFeatureSize
import skillbill.workflow.taskruntime.model.handoff.task.FeatureTaskRuntimeRunInvariants
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class CodeGraphReviewBriefingSupplementTest {
  @Test
  fun `review phase drops unauthorized graph paths after broker readBatch`() {
    val reviewInput = GoalSubtaskReviewInput(
      reviewBaseSha = "b".repeat(40),
      currentHeadSha = "c".repeat(40),
      trackedDelta = """
        diff --git a/allowed/Owned.kt b/allowed/Owned.kt
        --- a/allowed/Owned.kt
        +++ b/allowed/Owned.kt
        @@ -1 +1 @@
        -class Before
        +class Owned
      """.trimIndent(),
      ownedUntrackedPatches = "",
    )
    val supplement = CodeGraphReviewBriefingSupplement(
      retrievalPort = fakePort(),
      usageLedger = InMemoryCodeGraphUsageLedger(),
      reviewEvidenceBrokerFactory = ReviewEvidenceBrokerFactory { binding ->
        FileSystemReviewEvidenceBroker(binding)
      },
    )
    val text = supplement.phaseSupplement(
      request = runRequest(setOf("codegraph")),
      phaseId = FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_REVIEW,
      reviewInput = reviewInput,
    )
    assertContains(text, "allowed/Owned.kt")
    assertContains(text, "+class Owned")
    assertEquals(false, text.contains("class Drifted"))
    assertEquals(false, text.contains("denied/Outside.kt"))
  }

  private fun fakePort(): CodeGraphRetrievalPort = object : CodeGraphRetrievalPort {
    override fun query(request: CodeGraphQueryRequest): CodeGraphQueryResult = CodeGraphQueryResult(
      hits = listOf(
        CodeGraphCandidateHit(name = "Owned", file = "allowed/Owned.kt", kind = "type"),
        CodeGraphCandidateHit(name = "Outside", file = "denied/Outside.kt", kind = "type"),
      ),
      receipt = emptyMap(),
    )
  }

  private fun runRequest(capabilities: Set<String>): FeatureTaskRuntimeRunRequest {
    val repo = Files.createTempDirectory("codegraph-review-supplement")
    Files.createDirectories(repo.resolve("allowed"))
    Files.createDirectories(repo.resolve("denied"))
    Files.writeString(repo.resolve("allowed/Owned.kt"), "class Drifted\n")
    Files.writeString(repo.resolve("denied/Outside.kt"), "class Outside\n")
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
        experimentArmId = ExperimentArmId.TREATMENT,
        experimentPairId = "pair-1",
        experimentTreatmentCapabilities = capabilities,
      ),
    )
  }
}
