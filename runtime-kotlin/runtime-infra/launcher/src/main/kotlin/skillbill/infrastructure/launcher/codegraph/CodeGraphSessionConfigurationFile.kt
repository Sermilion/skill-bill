package skillbill.infrastructure.launcher.codegraph

import com.fasterxml.jackson.databind.JsonNode
import skillbill.infrastructure.skills.install.mcp.mutableStringAnyMap
import skillbill.infrastructure.skills.install.mcp.readJsonObject
import skillbill.infrastructure.skills.install.mcp.writeJson
import skillbill.ports.codegraph.model.CodeGraphMcpLaunchConfiguration
import skillbill.ports.codegraph.model.CodeGraphSessionRequest
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import skillbill.contracts.codegraph.CodeGraphMcpKeys as K

internal class CodeGraphSessionConfigurationFile private constructor(
  val configuration: CodeGraphMcpLaunchConfiguration,
  private val original: ByteArray?,
  private val originalServer: Any?,
  private val temporary: Boolean,
) : AutoCloseable {
  override fun close() {
    val path = configuration.mcpConfigPath
    if (temporary) {
      Files.deleteIfExists(path)
      Files.deleteIfExists(path.parent)
    } else {
      val settings = readJsonObject(path).toMutableMap()
      val servers = mutableStringAnyMap(settings[K.MCP_SERVERS])
      val installed = mutableStringAnyMap(servers[K.CODEGRAPH])
      check(installed[K.URL] == configuration.endpoint) {
        "CodeGraph session configuration ownership changed."
      }
      if (originalServer == null) servers.remove(K.CODEGRAPH) else servers[K.CODEGRAPH] = originalServer
      val originalHadServers = original?.let { CodeGraphMcpProtocol.mapper.readTree(it).has(K.MCP_SERVERS) } == true
      if (servers.isEmpty() && !originalHadServers) {
        settings.remove(K.MCP_SERVERS)
      } else {
        settings[K.MCP_SERVERS] = servers
      }
      if (original == null && settings.isEmpty()) {
        Files.deleteIfExists(path)
      } else if (original != null && CodeGraphMcpProtocol.mapper.readTree(
          original,
        ) == CodeGraphMcpProtocol.mapper.valueToTree<JsonNode>(settings)
      ) {
        Files.write(path, original)
      } else {
        writeJson(path, settings)
      }
    }
  }

  companion object {
    fun create(request: CodeGraphSessionRequest, endpoint: String, userHome: Path): CodeGraphSessionConfigurationFile {
      val project = when (request.agentId) {
        "cursor" -> request.launchDirectory.resolve(".cursor/mcp.json")
        "junie" -> request.launchDirectory.resolve(".junie/mcp/mcp.json")
        else -> null
      }
      if (project != null) requireLocalProjectConfiguration(request.launchDirectory, project, userHome)
      val path = project ?: Files.createTempDirectory("skill-bill-codegraph-").resolve("mcp.json")
      val original = if (Files.isRegularFile(path)) Files.readAllBytes(path) else null
      val settings = if (original == null) mutableMapOf<String, Any?>() else readJsonObject(path).toMutableMap()
      val servers = mutableStringAnyMap(settings[K.MCP_SERVERS])
      val previous = servers[K.CODEGRAPH]
      servers[K.CODEGRAPH] = mapOf(K.TYPE to "http", K.URL to endpoint)
      settings[K.MCP_SERVERS] = servers
      val result = CodeGraphSessionConfigurationFile(
        CodeGraphMcpLaunchConfiguration(path, endpoint, request.configuration.capability.wireValue),
        original,
        previous,
        project == null,
      )
      try {
        Files.createDirectories(path.parent)
        writeJson(path, settings)
      } catch (failure: IOException) {
        if (project == null) Files.deleteIfExists(path.parent)
        throw failure
      }
      return result
    }

    private fun requireLocalProjectConfiguration(directory: Path, target: Path, userHome: Path) {
      val root = directory.toRealPath()
      val home = userHome.toRealPath()
      check(root != home) { "CodeGraph cannot write global agent configuration." }
      var ancestor = target
      while (!Files.exists(ancestor)) ancestor = requireNotNull(ancestor.parent)
      check(ancestor.toRealPath().startsWith(root)) { "CodeGraph configuration escapes its session directory." }
    }
  }
}
