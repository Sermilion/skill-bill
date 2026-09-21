package skillbill.infrastructure.launcher.codegraph

import skillbill.infrastructure.launcher.agentrun.AgentRunCommand
import skillbill.infrastructure.skills.install.mcp.mutableStringAnyMap
import skillbill.infrastructure.skills.install.mcp.readJsonObject
import skillbill.infrastructure.skills.install.mcp.writeJson
import skillbill.install.model.InstallAgent
import skillbill.ports.codegraph.model.CodeGraphMcpLaunchConfiguration
import skillbill.ports.codegraph.model.CodeGraphSessionRequest
import java.nio.file.Files
import java.nio.file.Path
import skillbill.contracts.codegraph.CodeGraphMcpKeys as K

internal object CodeGraphAgentLaunchConfigurer {
  fun apply(
    command: AgentRunCommand,
    agent: InstallAgent,
    request: CodeGraphSessionRequest,
    configuration: CodeGraphMcpLaunchConfiguration,
  ): AgentRunCommand = when (agent) {
    InstallAgent.CLAUDE -> applyClaude(command, configuration)
    InstallAgent.CODEX -> applyCodex(command, configuration)
    InstallAgent.CURSOR,
    InstallAgent.JUNIE,
    -> command.copy(
      environment = command.environment + mapOf(
        "CODEGRAPH_MCP_CONFIG" to configuration.mcpConfigPath.toString(),
        "CODEGRAPH_REPOSITORY" to request.configuration.repository,
      ),
    )
  }

  private fun applyClaude(command: AgentRunCommand, configuration: CodeGraphMcpLaunchConfiguration): AgentRunCommand {
    val existingIndex = command.command.indexOf("--mcp-config")
    if (existingIndex < 0) {
      return command.copy(
        command = command.command + listOf(
          "--mcp-config",
          configuration.mcpConfigPath.toString(),
          "--strict-mcp-config",
        ),
      )
    }
    val existingPath = command.command.getOrNull(existingIndex + 1)
    check(!existingPath.isNullOrBlank()) { "Missing provider MCP configuration path." }
    val existingConfigPath = Path.of(existingPath)
    check(Files.isRegularFile(existingConfigPath)) { "Provider MCP configuration is unavailable." }
    val existing = readJsonObject(existingConfigPath).toMutableMap()
    val servers = mutableStringAnyMap(existing[K.MCP_SERVERS])
    val codeGraphConfig = mutableStringAnyMap(readJsonObject(configuration.mcpConfigPath)[K.MCP_SERVERS])
    servers[K.CODEGRAPH] = mutableStringAnyMap(codeGraphConfig[K.CODEGRAPH])
    existing[K.MCP_SERVERS] = servers
    writeJson(configuration.mcpConfigPath, existing)
    return command.copy(
      command = command.command.toMutableList().also { values ->
        values[existingIndex + 1] = configuration.mcpConfigPath.toString()
        val toolIndex = values.indexOf("--tools")
        if (toolIndex >= 0 && toolIndex + 1 < values.size) {
          val tools = values[toolIndex + 1].split(',').filter(String::isNotBlank)
          values[toolIndex + 1] = (tools + "mcp__codegraph__codegraph_${configuration.declaredCapability}")
            .distinct().joinToString(",")
        }
      },
    )
  }

  private fun applyCodex(command: AgentRunCommand, configuration: CodeGraphMcpLaunchConfiguration): AgentRunCommand {
    val endpoint = CodeGraphMcpProtocol.mapper.writeValueAsString(configuration.endpoint)
    val tool = CodeGraphMcpProtocol.mapper.writeValueAsString("codegraph_${configuration.declaredCapability}")
    val values = listOf("mcp_servers.codegraph={url=$endpoint, enabled_tools=[$tool]}")
    return command.copy(command = command.command + values.flatMap { value -> listOf("--config", value) })
  }
}
