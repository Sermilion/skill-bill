package skillbill.infrastructure.launcher.codegraph

import org.junit.jupiter.api.io.TempDir
import skillbill.contracts.codegraph.CodeGraphDegradationReason
import skillbill.infrastructure.host.resolveTelemetryConfigPath
import skillbill.ports.codegraph.model.CodeGraphCommandResult
import skillbill.ports.codegraph.model.CodeGraphSessionPreparation
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import skillbill.contracts.codegraph.CodeGraphMcpKeys as K

class FileSystemCodeGraphSessionTest {
  @TempDir lateinit var root: Path

  @Test
  fun `absent executable records fallback without setup or server`() {
    val fixture = CodeGraphBoundaryFixture(root).apply { available = false }
    val fallback = assertIs<CodeGraphSessionPreparation.Fallback>(fixture.session.prepare(fixture.request()))
    assertEquals(CodeGraphDegradationReason.MISSING_CLI, fallback.observation.degradation?.reason)
    assertEquals(0, fixture.starts)
    assertTrue(fixture.commands.isEmpty())
    assertContains(fixture.persisted(), "missing_cli")
  }

  @Test
  fun `unprepared graph initializes once and starts one scoped telemetry disabled process`() {
    val fixture = CodeGraphBoundaryFixture(root)
    val lease = assertIs<CodeGraphSessionPreparation.Ready>(fixture.session.prepare(fixture.request())).lease
    try {
      assertEquals(listOf("init", "status", "serve"), fixture.commands.map { it[1] })
      assertEquals(listOf("codegraph", "init", root.toString(), "--yes"), fixture.commands.first())
      assertEquals(listOf("codegraph", "serve", "--mcp"), fixture.commands.last())
      assertEquals(1, fixture.starts)
      assertTrue(fixture.scopes.all { it == root })
      assertTrue(fixture.environments.all { it == FileSystemCodeGraphSession.PROCESS_ENVIRONMENT })
      assertEquals("0", fixture.environments.last()["CODEGRAPH_TELEMETRY"])
      val config = Files.readString(lease.launchConfiguration.mcpConfigPath)
      assertContains(config, lease.launchConfiguration.endpoint)
      assertFalse(config.contains("command"))
      val payload = CodeGraphMcpProtocol.mapper.readTree(config)
      assertEquals(listOf(K.MCP_SERVERS), payload.fieldNames().asSequence().toList())
      val servers = payload.path(K.MCP_SERVERS)
      assertEquals(listOf(K.CODEGRAPH), servers.fieldNames().asSequence().toList())
      assertEquals(setOf(K.TYPE, K.URL), servers.path(K.CODEGRAPH).fieldNames().asSequence().toSet())
      lease.activate()
    } finally {
      lease.close()
    }
    assertEquals(1, fixture.process.closes)
    assertFalse(Files.exists(lease.launchConfiguration.mcpConfigPath))
    assertContains(fixture.persisted(), "exited")
  }

  @Test
  fun `setup failures distinguish missing graph initialization unsupported command and pending sync`() {
    val cases = listOf(
      CodeGraphDegradationReason.MISSING_GRAPH,
      CodeGraphDegradationReason.INITIALIZATION_FAILURE,
      CodeGraphDegradationReason.UNSUPPORTED_COMMAND,
      CodeGraphDegradationReason.PENDING_SYNCHRONIZATION,
      CodeGraphDegradationReason.UNAVAILABLE_CAPABILITY,
    )
    cases.forEach { reason ->
      val directory = Files.createDirectory(root.resolve(reason.wireValue))
      val fixture = CodeGraphBoundaryFixture(directory).apply {
        when (reason) {
          CodeGraphDegradationReason.MISSING_GRAPH -> initializeCreatesGraph = false
          CodeGraphDegradationReason.INITIALIZATION_FAILURE -> initialization = CodeGraphCommandResult(
            1,
            "private source",
            "secret credential",
          )
          CodeGraphDegradationReason.UNSUPPORTED_COMMAND -> initialization = CodeGraphCommandResult(
            2,
            "",
            "unknown option --yes secret",
          )
          CodeGraphDegradationReason.PENDING_SYNCHRONIZATION -> status = CodeGraphCommandResult(
            0,
            "Nodes: 1\n### Pending sync: private.kt",
            "",
          )
          else -> status = CodeGraphCommandResult(0, "unexpected status, secret", "")
        }
      }
      val fallback = assertIs<CodeGraphSessionPreparation.Fallback>(fixture.session.prepare(fixture.request()))
      assertEquals(reason, fallback.observation.degradation?.reason)
      assertEquals(0, fixture.starts)
      assertFalse(fixture.persisted().contains("secret"))
      assertFalse(fixture.persisted().contains("private"))
      assertContains(fixture.persisted(), reason.wireValue)
    }
  }

  @Test
  fun `spawn handshake and capability failures close acquired processes and persist fallback`() {
    listOf("spawn", "handshake", "capability").forEach { failure ->
      val fixture = CodeGraphBoundaryFixture(Files.createDirectory(root.resolve(failure))).apply {
        prepared()
        when (failure) {
          "spawn" -> startFailure = IllegalStateException("private secret")
          "handshake" -> process.failHandshake = true
          else -> process.tool = "undeclared_tool"
        }
      }
      val fallback = assertIs<CodeGraphSessionPreparation.Fallback>(fixture.session.prepare(fixture.request()))
      assertEquals(
        if (failure == "capability") {
          CodeGraphDegradationReason.UNAVAILABLE_CAPABILITY
        } else {
          CodeGraphDegradationReason.MCP_STARTUP_FAILURE
        },
        fallback.observation.degradation?.reason,
      )
      assertEquals(if (failure == "spawn") 0 else 1, fixture.process.closes)
      assertTrue(fallback.observation.cleanupAttempted)
      assertFalse(fixture.persisted().contains("private"))
    }
  }

  @Test
  fun `project configuration symlink cannot redirect writes to global settings`() {
    val outside = Files.createDirectory(root.resolve("global"))
    val global = Files.writeString(outside.resolve("mcp.json"), "{\"mcpServers\":{}}")
    val repo = Files.createDirectory(root.resolve("repo"))
    Files.createSymbolicLink(repo.resolve(".cursor"), outside)
    val fixture = CodeGraphBoundaryFixture(repo).apply { prepared() }
    val fallback = assertIs<CodeGraphSessionPreparation.Fallback>(fixture.session.prepare(fixture.request("cursor")))
    assertEquals(CodeGraphDegradationReason.MCP_STARTUP_FAILURE, fallback.observation.degradation?.reason)
    assertEquals("{\"mcpServers\":{}}", Files.readString(global))
    assertEquals(1, fixture.process.closes)
  }

  @Test
  fun `project and global settings and Skill Bill telemetry preference survive a session`() {
    val home = Path.of(System.getProperty("user.home"))
    val globals = listOf(
      home.resolve(".codex/config.toml"),
      home.resolve(".claude.json"),
      home.resolve(".cursor/mcp.json"),
      home.resolve(".junie/mcp/mcp.json"),
      resolveTelemetryConfigPath(System.getenv(), home),
    )
    val before = globals.associateWith { if (Files.isRegularFile(it)) Files.readAllBytes(it) else null }
    listOf("codex", "claude", "cursor", "junie").forEach { agent ->
      val repo = Files.createDirectory(root.resolve(agent))
      val fixture = CodeGraphBoundaryFixture(repo).apply { prepared() }
      val project = when (agent) {
        "cursor" -> repo.resolve(".cursor/mcp.json")
        "junie" -> repo.resolve(".junie/mcp/mcp.json")
        else -> repo.resolve("existing.json")
      }
      Files.createDirectories(project.parent)
      val original = "{\"mcpServers\":{\"other\":{\"url\":\"http://existing\"}}}\n"
      Files.writeString(project, original)
      val lease = assertIs<CodeGraphSessionPreparation.Ready>(fixture.session.prepare(fixture.request(agent))).lease
      lease.close()
      assertEquals(original, Files.readString(project))
    }
    before.forEach { (path, bytes) ->
      if (bytes == null) assertFalse(Files.exists(path)) else assertTrue(bytes.contentEquals(Files.readAllBytes(path)))
    }
  }
}
