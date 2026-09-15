package skillbill.infrastructure.fs.install

import skillbill.infrastructure.fs.install.runtime.InstallOperations
import skillbill.model.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class ClaudeConfigDirAgentPathTest {
  @Test
  fun `claude skill path falls back to dot-claude skills when CLAUDE_CONFIG_DIR is unset`() {
    val home = Files.createTempDirectory("skillbill-claude-config-default")
    assertEquals(
      home.resolve(".claude/skills"),
      InstallOperations.agentPath("claude", home, environment = emptyMap()),
    )
  }

  @Test
  fun `claude skill path honors CLAUDE_CONFIG_DIR for named profiles`() {
    val home = Files.createTempDirectory("skillbill-claude-config-work")
    val workConfig = home.resolve(".claude-work")
    val env = mapOf("CLAUDE_CONFIG_DIR" to workConfig.toString())

    assertEquals(
      workConfig.resolve("skills"),
      InstallOperations.agentPath("claude", home, environment = env),
    )

    assertEquals(
      workConfig.resolve("agents"),
      InstallOperations.claudeAgentsPath(home, environment = env),
    )

    assertEquals(
      home.resolve(".agents/skills"),
      InstallOperations.agentPath("codex", home, environment = env),
    )
  }

  @Test
  fun `blank CLAUDE_CONFIG_DIR falls back to the default root`() {
    val home = Files.createTempDirectory("skillbill-claude-config-blank")
    assertEquals(
      home.resolve(".claude/skills"),
      InstallOperations.agentPath("claude", home, environment = mapOf("CLAUDE_CONFIG_DIR" to "  ")),
    )
  }

  @Test
  fun `detection honors CLAUDE_CONFIG_DIR when only the work profile exists`() {
    val home = Files.createTempDirectory("skillbill-claude-config-detect")
    val workConfig = home.resolve(".claude-work")
    Files.createDirectories(workConfig)
    val env = mapOf("CLAUDE_CONFIG_DIR" to workConfig.toString())

    val claudeTargets = InstallOperations.detectAgentTargets(home, environment = env)
      .filter { it.name == "claude" }
    assertEquals(
      listOf(home.resolve(".claude/skills"), workConfig.resolve("skills")),
      claudeTargets.map { it.path.toPath() },
    )
  }
}
