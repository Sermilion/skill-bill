package skillbill.application.uninstall

import skillbill.contracts.SharedPayloadKeys
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
  fun confirmationText(): String = buildString {
    appendLine("This will uninstall Skill Bill from:")
    appendLine("- ${agentTargets.size} agent target directories")
    appendLine("- ${mcpAgents.size} MCP configurations")
    appendLine("- $stateRoot")
    append("Continue? [y/N] ")
  }

  fun toText(status: String): String = buildString {
    appendLine("uninstall_status: $status")
    appendLine("state_root: $stateRoot")
    appendLine("agent_targets: ${agentTargets.size}")
    appendLine("skill_names: ${skillNames.size}")
  }

  fun toPayload(
    status: String,
    removed: List<String>,
    skipped: List<String>,
    warnings: List<String>,
  ): Map<String, Any?> = linkedMapOf(
    SharedPayloadKeys.STATUS to status,
    "state_root" to stateRoot.toString(),
    "skill_names" to skillNames,
    "legacy_names" to legacyNames,
    "agent_targets" to agentTargets.map(Path::toString),
    "mcp_agents" to mcpAgents,
    "launchers" to launchers.map {
      mapOf("path" to it.path.toString(), "expected_target" to it.expectedTarget.toString())
    },
    "desktop" to mapOf(
      "launcher" to desktop.launcher?.path?.toString(),
      "files" to desktop.files.map(Path::toString),
      "directories" to desktop.directories.map(Path::toString),
    ),
    "removed" to removed,
    "skipped" to skipped,
    "warnings" to warnings,
  )
}

data class UninstallResult(
  val failed: Boolean,
  val removed: List<String>,
  val skipped: List<String>,
  val warnings: List<String>,
) {
  val status: String = if (failed) "failed_with_degradations" else "completed"

  val exitCode: Int = if (failed) 1 else 0

  fun toText(): String = buildString {
    appendLine("uninstall_status: $status")
    appendLine("removed: ${removed.size}")
    appendLine("skipped: ${skipped.size}")
    if (warnings.isNotEmpty()) {
      appendLine("warnings:")
      warnings.forEach { warning -> appendLine("- $warning") }
    }
  }

  fun toPayload(): Map<String, Any?> = linkedMapOf(
    SharedPayloadKeys.STATUS to status,
    "removed" to removed,
    "skipped" to skipped,
    "warnings" to warnings,
  )
}
