package skillbill.mcp

import skillbill.mcp.core.McpStdioServer
import skillbill.mcp.shared.McpComponent
import skillbill.mcp.shared.McpRuntimeContext
import skillbill.telemetry.CONFIG_ENVIRONMENT_KEY
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertSame

class McpCompositionLifetimeTest {
  @Test
  fun `sequential tool calls retain the process database session factory`() {
    val root = Files.createTempDirectory("skillbill-mcp-composition")
    val config = root.resolve("config.json")
    Files.writeString(
      config,
      """{"install_id":"test","telemetry":{"level":"off","proxy_url":"","batch_size":50}}""",
    )
    val context = McpRuntimeContext(
      environment = mapOf(
        "SKILL_BILL_REVIEW_DB" to root.resolve("metrics.db").toString(),
        CONFIG_ENVIRONMENT_KEY to config.toString(),
      ),
      userHome = root,
    )
    val component: McpComponent = context.mcpComponent()
    val firstFactory = component.databaseSessionFactory
    val request = """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"version","arguments":{}}}"""

    val firstResponse = McpStdioServer.handleLine(request, component)
    val secondResponse = McpStdioServer.handleLine(request, component)

    kotlin.test.assertNotNull(firstResponse)
    kotlin.test.assertNotNull(secondResponse)
    assertSame(firstFactory, component.databaseSessionFactory)
  }
}
