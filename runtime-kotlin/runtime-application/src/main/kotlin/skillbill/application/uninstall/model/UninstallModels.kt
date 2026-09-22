package skillbill.application.uninstall.model

import java.nio.file.Path

data class UninstallRequest(
  val home: Path,
  val environment: Map<String, String>,
  val desktopAppDir: String?,
)

data class LauncherRemoval(
  val path: Path,
  val expectedTarget: Path,
)

data class DesktopRemoval(
  val launcher: LauncherRemoval?,
  val files: List<Path>,
  val directories: List<Path>,
)

data class UninstallPlan(
  val home: Path,
  val stateRoot: Path,
  val skillNames: List<String>,
  val legacyNames: List<String>,
  val agentTargets: List<Path>,
  val nativeSourceRoots: List<Path>,
  val mcpAgents: List<String>,
  val launchers: List<LauncherRemoval>,
  val desktop: DesktopRemoval,
) {
  fun confirmationText(): String =
    buildString {
      appendLine("This will uninstall Skill Bill from:")
      appendLine("- ${agentTargets.size} agent target directories")
      appendLine("- ${mcpAgents.size} MCP configurations")
      appendLine("- $stateRoot")
      append("Continue? [y/N] ")
    }

  fun toText(status: String): String =
    buildString {
      appendLine("uninstall_status: $status")
      appendLine("state_root: $stateRoot")
      appendLine("agent_targets: ${agentTargets.size}")
      appendLine("skill_names: ${skillNames.size}")
    }
}

data class UninstallResult(
  val failed: Boolean,
  val removed: List<String>,
  val skipped: List<String>,
  val warnings: List<String>,
) {
  val status: String = if (failed) "failed_with_degradations" else "completed"

  val exitCode: Int = if (failed) 1 else 0

  fun toText(): String =
    buildString {
      appendLine("uninstall_status: $status")
      appendLine("removed: ${removed.size}")
      appendLine("skipped: ${skipped.size}")
      if (warnings.isNotEmpty()) {
        appendLine("warnings:")
        warnings.forEach { warning -> appendLine("- $warning") }
      }
    }
}
