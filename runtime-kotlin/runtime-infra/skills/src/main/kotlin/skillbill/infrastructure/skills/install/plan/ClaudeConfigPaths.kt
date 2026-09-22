package skillbill.infrastructure.skills.install.plan

import skillbill.infrastructure.skills.nativeagent.support.CLAUDE_CONFIG_DIR_ENV
import java.nio.file.Path
import skillbill.infrastructure.skills.nativeagent.support.claudeConfigRoots as nativeAgentClaudeConfigRoots

internal const val CLAUDE_CONFIG_DIR_ENV: String = "CLAUDE_CONFIG_DIR"

internal fun claudeConfigRoot(
  home: Path,
  environment: Map<String, String>,
): Path =
  environment[CLAUDE_CONFIG_DIR_ENV]?.takeIf { it.isNotBlank() }
    ?.let { Path.of(it).toAbsolutePath().normalize() }
    ?: home.resolve(".claude")

internal fun claudeConfigRoots(
  home: Path,
  environment: Map<String, String>,
): List<Path> = nativeAgentClaudeConfigRoots(home, environment)

internal fun claudeSkillTargets(
  home: Path,
  environment: Map<String, String>,
): List<Path> {
  return claudeConfigRoots(home, environment).map { root -> root.resolve("skills") }
}
