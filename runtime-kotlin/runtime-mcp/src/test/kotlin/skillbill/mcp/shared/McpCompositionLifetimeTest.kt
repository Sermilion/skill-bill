package skillbill.mcp.shared

import me.tatarka.inject.annotations.Component
import skillbill.di.core.RuntimeComponent
import skillbill.mcp.core.McpStdioServer
import skillbill.ports.db.DatabaseSessionFactory
import skillbill.telemetry.CONFIG_ENVIRONMENT_KEY
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame

@Component
internal abstract class DatabaseSessionFactoryProbe(
  @Component val runtimeComponent: RuntimeComponent,
) {
  abstract val databaseSessionFactory: DatabaseSessionFactory
}

class McpCompositionLifetimeTest {
  @Test
  fun `sequential tool calls retain the process database session factory`() {
    val root = Files.createTempDirectory("skillbill-mcp-composition")
    val config = root.resolve("config.json")
    Files.writeString(
      config,
      """{"install_id":"test","telemetry":{"level":"off","proxy_url":"","batch_size":50}}""",
    )
    val context =
      McpRuntimeContext(
        environment =
          mapOf(
            "SKILL_BILL_REVIEW_DB" to root.resolve("metrics.db").toString(),
            CONFIG_ENVIRONMENT_KEY to config.toString(),
          ),
        userHome = root,
      )
    val component = context.mcpComponent()
    val probe = DatabaseSessionFactoryProbe::class.create(context.runtimeComponent())
    val firstFactory = probe.databaseSessionFactory
    val request = toolCallRequest(1, "doctor", emptyMap())

    val firstResponse = McpStdioServer.handleLine(request, component)
    val secondResponse = McpStdioServer.handleLine(request, component)

    assertNotNull(firstResponse)
    assertNotNull(secondResponse)
    assertSame(firstFactory, probe.databaseSessionFactory)
  }
}
