package skillbill.engine.featuretask.review.core

import skillbill.ports.workflow.gitops.NoopWorkflowGitOperations
import skillbill.ports.workflow.gitops.WorkflowGitOperations
import skillbill.ports.workflow.gitops.model.WorkflowGitNameListResult
import skillbill.workflow.taskruntime.model.core.FeatureTaskRuntimeResolvedBranch
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

private class OwnedPathsGitOperations(private val result: WorkflowGitNameListResult) :
  WorkflowGitOperations by NoopWorkflowGitOperations {
  override fun repositoryOwnedPaths(repoRoot: Path): WorkflowGitNameListResult = result
}

class FeatureTaskRuntimeScopedReviewBaselineTest {
  private val repoRoot: Path = Path.of("/tmp/scoped-review-baseline")
  private val baseSha = "a".repeat(40)

  private fun resolved() =
    FeatureTaskRuntimeResolvedBranch(
      branch = "feat/SKILL-150",
      baselineUntrackedPaths = listOf("baseline/pre-existing.txt"),
      workflowOwnedPaths = listOf("src/Owned.kt", "untracked/owned-new.kt"),
    )

  @Test
  fun `scoped baseline carries the owned inventory and excludes foreign untracked paths`() {
    val git =
      OwnedPathsGitOperations(
        WorkflowGitNameListResult.Listed(
          listOf("untracked/owned-new.kt", "foreign/sibling.kt", ".feature-specs/OTHER-1/spec.md"),
        ),
      )

    val baseline = FeatureTaskRuntimeScopedReviewBaseline.of(git, repoRoot, resolved(), baseSha)

    assertEquals(listOf("src/Owned.kt", "untracked/owned-new.kt"), baseline.ownedPathspec)
    assertEquals(
      listOf(".feature-specs/OTHER-1/spec.md", "baseline/pre-existing.txt", "foreign/sibling.kt"),
      baseline.baselineUntrackedPaths,
    )
  }

  @Test
  fun `an unreadable owned-path listing falls back to the durable baseline instead of widening`() {
    val git = OwnedPathsGitOperations(WorkflowGitNameListResult.Failed(error = "git failed"))

    val baseline = FeatureTaskRuntimeScopedReviewBaseline.of(git, repoRoot, resolved(), baseSha)

    assertEquals(listOf("baseline/pre-existing.txt"), baseline.baselineUntrackedPaths)
    assertEquals(listOf("src/Owned.kt", "untracked/owned-new.kt"), baseline.ownedPathspec)
  }
}
