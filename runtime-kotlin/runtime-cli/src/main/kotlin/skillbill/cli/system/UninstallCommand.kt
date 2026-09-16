package skillbill.cli.system

import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import me.tatarka.inject.annotations.Inject
import skillbill.application.uninstall.SkillBillUninstallService
import skillbill.application.uninstall.UninstallPlan
import skillbill.application.uninstall.UninstallRequest
import skillbill.cli.kernel.CliRunState
import skillbill.cli.kernel.DocumentedCliCommand
import skillbill.cli.kernel.formatOption
import skillbill.cli.model.CliRunInputs
import skillbill.contracts.SharedPayloadKeys

@Inject
class UninstallCommand(
  private val state: CliRunState,
  private val inputs: CliRunInputs,
  private val uninstallService: SkillBillUninstallService,
) : DocumentedCliCommand("uninstall", "Uninstall Skill Bill from local agents and runtime state.") {
  private val yes by option("--yes", "-y", help = "Skip the interactive confirmation prompt.")
    .flag(default = false)
  private val dryRun by option("--dry-run", help = "Show what would be removed without changing files.")
    .flag(default = false)
  private val desktopAppDir by option("--desktop-app-dir", help = "Override the desktop app install directory.")
  private val format by formatOption()

  override fun run() {
    if (inputs.environment[GOAL_CONTINUATION_ENV] == "1") {
      val message =
        "Refusing to run skill-bill uninstall during skill-bill goal-continuation.\n" +
          "Goal workers must preserve the active workflow store; uninstall after the goal completes."
      state.completeText(
        message,
        mapOf(
          SharedPayloadKeys.STATUS to "error",
          "error" to message,
          "exit_code" to GOAL_CONTINUATION_REFUSAL_EXIT_CODE,
        ),
        exitCode = GOAL_CONTINUATION_REFUSAL_EXIT_CODE,
      )
      return
    }

    val request = UninstallRequest(
      home = inputs.userHome,
      environment = inputs.environment,
      desktopAppDir = desktopAppDir,
    )
    val plan = uninstallService.plan(request)
    if (!dryRun && !yes && !confirmed(plan)) {
      val payload = plan.toPayload(
        status = "aborted",
        removed = emptyList(),
        skipped = emptyList(),
        warnings = emptyList(),
      )
      completeUninstall("uninstall_status: aborted\n", payload, exitCode = 1)
      return
    }

    if (dryRun) {
      completeUninstall(plan.toText("dry_run"), plan.toPayload("dry_run", emptyList(), emptyList(), emptyList()))
      return
    }

    val result = uninstallService.apply(plan)
    completeUninstall(result.toText(), result.toPayload(), result.exitCode)
  }

  private fun confirmed(plan: UninstallPlan): Boolean {
    inputs.liveStdout(plan.confirmationText())
    val answer = state.readInputLine()?.trim().orEmpty()
    return answer.equals("y", ignoreCase = true) || answer.equals("yes", ignoreCase = true)
  }

  private fun completeUninstall(text: String, payload: Map<String, Any?>, exitCode: Int = 0) {
    if (format.wireName == "json") {
      state.complete(payload, format, exitCode)
    } else {
      state.completeText(text, payload, exitCode)
    }
  }
}

private const val GOAL_CONTINUATION_ENV = "SKILL_BILL_GOAL_CONTINUATION"
private const val GOAL_CONTINUATION_REFUSAL_EXIT_CODE = 64
