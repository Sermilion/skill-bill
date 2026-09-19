package skillbill.cli.goal.purge
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import me.tatarka.inject.annotations.Inject
import skillbill.cli.goal.core.purge
import skillbill.cli.kernel.cli.CliRunState
import skillbill.cli.kernel.cli.DocumentedCliCommand
import skillbill.cli.kernel.cli.resolveCliRepositoryRoot
import skillbill.cli.model.CliRunInputs
import skillbill.engine.goalrunner.GoalRunnerStatusService
import skillbill.engine.goalrunner.model.GoalRunnerPurgeRequest

@Inject
class GoalPurgeCommand(
  private val goalRunnerStatusService: GoalRunnerStatusService,
  private val state: CliRunState,
  private val inputs: CliRunInputs,
) : DocumentedCliCommand(
  "purge",
  "Delete decomposed goal runtime state and restore the feature-spec tree to an unlaunched shape.",
) {
  private val issueKey by argument(help = "Parent issue key for the decomposed goal.")
  private val force by option("--force", "--yes", help = "Bypass purge confirmation gate.")
    .flag(default = false)
  private val confirmIssueKey by option(
    "--confirm-issue-key",
    help = "Confirmation gate for purge. Must match the issue key.",
  )
  private val repoRoot by option("--repo-root", help = "Repository root that owns the goal.")

  override fun run() {
    if (!force && confirmIssueKey != issueKey) {
      throw UsageError(
        "Goal purge requires explicit confirmation. Pass --confirm-issue-key $issueKey or --force.",
      )
    }
    val result = goalRunnerStatusService.purge(
      GoalRunnerPurgeRequest(
        issueKey = issueKey,
        repoRoot = resolveCliRepositoryRoot(repoRoot, inputs),
      ),
    )
    val payload = result.toGoalPurgeCliMap()
    state.completeText(goalPurgeText(payload), payload, exitCode = payload.goalPurgeExitCode())
  }
}
