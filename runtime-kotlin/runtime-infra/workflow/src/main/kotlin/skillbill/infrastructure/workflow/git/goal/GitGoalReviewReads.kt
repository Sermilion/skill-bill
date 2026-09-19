package skillbill.infrastructure.workflow.git.goal
import skillbill.infrastructure.workflow.decomposition.repoRoot
import skillbill.infrastructure.workflow.feature.repoRoot
import skillbill.infrastructure.workflow.featuretask.repoRoot
import skillbill.infrastructure.workflow.git.checkpoint.git
import skillbill.infrastructure.workflow.git.local.git
import skillbill.infrastructure.workflow.git.protected.git
import skillbill.infrastructure.workflow.git.repository.git
import skillbill.infrastructure.workflow.git.repository.repoRoot
import skillbill.infrastructure.workflow.git.scoped.git
import skillbill.infrastructure.workflow.git.standard.args
import skillbill.infrastructure.workflow.git.suppression.git
import skillbill.infrastructure.workflow.git.workflow.git
import skillbill.infrastructure.workflow.git.workflow.repoRoot
import skillbill.infrastructure.workflow.git.workflow.value
import skillbill.infrastructure.workflow.process.runGitCommand
import skillbill.infrastructure.workflow.review.specialists.system.repoRoot
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import java.nio.file.Path

internal fun currentGoalReviewBranch(repoRoot: Path, expectedBranch: String): String? =
  goalReviewGitValue(repoRoot, "branch", "--show-current")?.trim()?.takeIf { it == expectedBranch }

internal fun goalReviewGitValue(repoRoot: Path, vararg args: String): String? =
  goalReviewGitValue(repoRoot, args.toList())

internal fun goalReviewGitValue(repoRoot: Path, args: List<String>): String? =
  runGitCommand(repoRoot, args).takeIf { it is WorkflowGitOperationResult.Ok }?.value

internal fun goalReviewUntrackedPaths(repoRoot: Path): List<String>? = runGitCommand(
  repoRoot,
  "ls-files",
  "--others",
  "--exclude-standard",
  "-z",
).takeIf { it is WorkflowGitOperationResult.Ok }
  ?.value
  ?.split('\u0000')
  ?.filter(String::isNotBlank)
  ?.distinct()
  ?.sorted()
