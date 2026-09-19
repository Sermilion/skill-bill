package skillbill.engine.featuretask.validation

import skillbill.engine.featuretask.model.core.FeatureTaskRuntimeGoalContinuationContext
import skillbill.engine.featuretask.runner.phasesFor
import skillbill.infrastructure.workflow.github.GitHubPullRequestCheckDiscovery
import skillbill.ports.scaffold.install.InstalledPlatformPackCatalogPort
import skillbill.ports.workflow.gitops.model.GoalSubtaskReviewBaseline
import skillbill.workflow.taskruntime.phase.task.FeatureTaskRuntimePhaseWorkflowDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FeatureTaskRuntimeReadinessGateCoordinatorTest {
  @Test
  fun `Detekt edit after validate invalidates only pack check`() {
    val selection = ReadinessCheckSelection(
      InstalledPlatformPackCatalogPort {
        listOf(kotlinPackWithoutGate().copy(validationGate = validationGateTestDeclaration))
      },
      GitHubPullRequestCheckDiscovery(),
    )
    val selected = assertIs<ReadinessCheckSelectionResult.Selected>(
      selection.select(validationGateTestRepoRoot, listOf("runtime-kotlin/runtime-engine/Foo.kt")),
    ).checks
    val invalidated = selection.invalidatedCheckIds(
      selected,
      listOf("runtime-kotlin/runtime-engine/DetektEdit.kt"),
    )
    assertEquals(setOf(READINESS_PACK_COLLECT_ALL_CHECK_ID), invalidated)
  }

  @Test
  fun `goal child commit_push omits pr phase but still lists commit_push`() {
    val request = minimalRequest().copy(
      goalContinuation = FeatureTaskRuntimeGoalContinuationContext(
        parentIssueKey = "SKILL-364",
        subtaskId = 1,
        subtaskName = "readiness",
        goalBranch = "feat/skill-364",
        parentWorkflowId = "parent-wf",
        suppressPr = true,
        reviewBaseline = GoalSubtaskReviewBaseline("0".repeat(40), emptyList()),
      ),
    )
    val phases = phasesFor(request)
    assertFalse(phases.contains(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_PR))
    assertTrue(phases.contains(FeatureTaskRuntimePhaseWorkflowDefinition.PHASE_COMMIT_PUSH))
  }
}
