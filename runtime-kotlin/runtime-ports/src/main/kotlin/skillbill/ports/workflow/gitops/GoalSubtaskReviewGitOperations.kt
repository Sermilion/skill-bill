package skillbill.ports.workflow.gitops

import skillbill.ports.workflow.gitops.model.GoalSubtaskReviewBaseline
import skillbill.ports.workflow.gitops.model.GoalSubtaskReviewBaselineRecoveryRequest
import skillbill.ports.workflow.gitops.model.GoalSubtaskReviewBaselineResult
import skillbill.ports.workflow.gitops.model.GoalSubtaskReviewInputResult
import java.nio.file.Path

interface GoalSubtaskReviewGitOperations {
  fun captureGoalSubtaskReviewBaseline(
    repoRoot: Path,
    expectedBranch: String,
  ): GoalSubtaskReviewBaselineResult

  fun buildGoalSubtaskReviewInput(
    repoRoot: Path,
    baseline: GoalSubtaskReviewBaseline,
    expectedBranch: String,
  ): GoalSubtaskReviewInputResult

  fun recoverGoalSubtaskReviewBaseline(
    repoRoot: Path,
    request: GoalSubtaskReviewBaselineRecoveryRequest,
    expectedBranch: String,
  ): GoalSubtaskReviewBaselineResult
}
