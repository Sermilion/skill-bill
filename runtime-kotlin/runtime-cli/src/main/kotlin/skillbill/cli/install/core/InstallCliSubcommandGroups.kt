package skillbill.cli.install.core

import me.tatarka.inject.annotations.Inject
import skillbill.cli.install.apply.InstallApplyExternalAddonsCommand
import skillbill.cli.install.mcp.InstallRegisterMcpCommand
import skillbill.cli.install.mcp.InstallUnregisterMcpCommand
import skillbill.cli.install.nativeagent.InstallAgentPathCommand
import skillbill.cli.install.nativeagent.InstallClaudeAgentsPathCommand
import skillbill.cli.install.nativeagent.InstallClaudeRootsCommand
import skillbill.cli.install.nativeagent.InstallCleanupAgentTargetCommand
import skillbill.cli.install.nativeagent.InstallCodexAgentsPathCommand
import skillbill.cli.install.nativeagent.InstallCodexRootsCommand
import skillbill.cli.install.nativeagent.InstallCursorAgentsPathCommand
import skillbill.cli.install.nativeagent.InstallDetectAgentsCommand
import skillbill.cli.install.nativeagent.InstallJunieAgentsPathCommand
import skillbill.cli.install.nativeagent.InstallLinkSkillCommand
import skillbill.cli.install.nativeagent.NativeAgentClaudeCliCommands
import skillbill.cli.install.nativeagent.NativeAgentCodexCliCommands
import skillbill.cli.install.nativeagent.NativeAgentCursorCliCommands
import skillbill.cli.install.nativeagent.NativeAgentJunieCliCommands

@Inject
class InstallPlanCliSubcommands(
  val plan: InstallPlanCommand,
  val apply: InstallApplyCommand,
  val applyExternalAddons: InstallApplyExternalAddonsCommand,
  val reconcile: InstallReconcileCommand,
  val replayLastSelection: InstallReplayLastSelectionCommand,
)

@Inject
class InstallAgentDiscoveryCliSubcommands(
  val agentPath: InstallAgentPathCommand,
  val detectAgents: InstallDetectAgentsCommand,
  val claudeRoots: InstallClaudeRootsCommand,
  val codexRoots: InstallCodexRootsCommand,
)

@Inject
class InstallAgentPathsCliSubcommands(
  val linkSkill: InstallLinkSkillCommand,
  val codexAgentsPath: InstallCodexAgentsPathCommand,
  val claudeAgentsPath: InstallClaudeAgentsPathCommand,
  val junieAgentsPath: InstallJunieAgentsPathCommand,
  val cursorAgentsPath: InstallCursorAgentsPathCommand,
  val cleanupAgentTarget: InstallCleanupAgentTargetCommand,
)

@Inject
class InstallNativeAgentCliSubcommands(
  claude: NativeAgentClaudeCliCommands,
  codex: NativeAgentCodexCliCommands,
  junie: NativeAgentJunieCliCommands,
  cursor: NativeAgentCursorCliCommands,
) {
  val linkClaudeAgents = claude.link
  val unlinkClaudeAgents = claude.unlink
  val linkCodexAgents = codex.link
  val unlinkCodexAgents = codex.unlink
  val linkJunieAgents = junie.link
  val unlinkJunieAgents = junie.unlink
  val linkCursorAgents = cursor.link
  val unlinkCursorAgents = cursor.unlink
}

@Inject
class InstallMcpCliSubcommands(
  val registerMcp: InstallRegisterMcpCommand,
  val unregisterMcp: InstallUnregisterMcpCommand,
)
