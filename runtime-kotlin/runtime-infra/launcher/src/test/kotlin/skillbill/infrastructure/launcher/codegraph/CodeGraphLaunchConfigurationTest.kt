package skillbill.infrastructure.launcher.codegraph

import org.junit.jupiter.api.io.TempDir
import skillbill.infrastructure.launcher.agentrun.AgentRunCommand
import skillbill.install.model.InstallAgent
import skillbill.ports.codegraph.model.CodeGraphSessionPreparation
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import skillbill.contracts.codegraph.CodeGraphMcpKeys as K

class CodeGraphLaunchConfigurationTest {
  @TempDir lateinit var root: Path

  @Test
  fun `provider projection preserves prompt environment deadline and unrelated MCP servers`() {
    InstallAgent.entries.forEach { agent ->
      val fixture = CodeGraphBoundaryFixture(Files.createDirectory(root.resolve(agent.id))).apply { prepared() }
      val lease = assertIs<CodeGraphSessionPreparation.Ready>(fixture.session.prepare(fixture.request(agent.id))).lease
      try {
        val previous = fixture.root.resolve("previous-mcp.json")
        val original = "{\"mcpServers\":{\"existing\":{\"url\":\"http://other\"}}}"
        Files.writeString(previous, original)
        val argv = if (agent == InstallAgent.CLAUDE) {
          listOf("claude", "--mcp-config", previous.toString(), "--tools", "Read,mcp__existing__read")
        } else {
          listOf(agent.id)
        }
        val command = AgentRunCommand(argv, fixture.root, 10.seconds, "unchanged prompt", mapOf("SENTINEL" to "kept"))
        val configured = CodeGraphAgentLaunchConfigurer.apply(
          command,
          agent,
          fixture.request(agent.id),
          lease.launchConfiguration,
        )
        assertEquals(command.stdinText, configured.stdinText)
        assertEquals(command.timeout, configured.timeout)
        assertEquals(command.workingDirectory, configured.workingDirectory)
        assertEquals("kept", configured.environment["SENTINEL"])
        assertEquals(original, Files.readString(previous))
        when (agent) {
          InstallAgent.CLAUDE -> {
            val path = Path.of(configured.command[configured.command.indexOf("--mcp-config") + 1])
            assertEquals(lease.launchConfiguration.mcpConfigPath, path)
            assertEquals("Read,mcp__existing__read,mcp__codegraph__codegraph_explore", configured.command.last())
            val servers = CodeGraphMcpProtocol.mapper.readTree(Files.readString(path)).path(K.MCP_SERVERS)
            assertTrue(servers.has("existing"))
            assertEquals(lease.launchConfiguration.endpoint, servers.path(K.CODEGRAPH).path(K.URL).asText())
          }
          InstallAgent.CODEX -> {
            assertContains(configured.command, "--config")
            assertContains(configured.command.last(), lease.launchConfiguration.endpoint)
            assertContains(configured.command.last(), "codegraph_explore")
            assertFalse(configured.command.last().contains("command="))
          }
          InstallAgent.CURSOR, InstallAgent.JUNIE -> {
            assertEquals(argv, configured.command)
            val config = CodeGraphMcpProtocol.mapper.readTree(Files.readString(lease.launchConfiguration.mcpConfigPath))
            assertEquals(
              lease.launchConfiguration.endpoint,
              config.path(K.MCP_SERVERS).path(K.CODEGRAPH).path(K.URL).asText(),
            )
          }
        }
        assertEquals(1, fixture.starts)
      } finally {
        lease.close()
      }
      assertEquals(1, fixture.process.closes)
    }
  }
}
