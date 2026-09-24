package skillbill.mcp.system

import skillbill.cli.core.CliRuntime
import skillbill.cli.model.CliRuntimeContext
import skillbill.di.core.SkillBillVersion
import skillbill.mcp.shared.McpRuntimeContext
import skillbill.mcp.shared.assertGoldenPayload
import skillbill.mcp.shared.callToolPayload
import skillbill.mcp.shared.decodeJsonObject
import skillbill.mcp.shared.disabledTelemetryEnvironment
import skillbill.ports.telemetry.model.RemoteTransportResponse
import skillbill.ports.telemetry.transport.RemoteTransportPort
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class McpSystemToolsTest {
  @Test
  fun `doctor returns version and db info`() {
    val tempDir = Files.createTempDirectory("skillbill-mcp-doctor")
    val env = disabledTelemetryEnvironment(tempDir)

    val result = McpRuntimeContext(environment = env, userHome = tempDir).callToolPayload("doctor")

    assertGoldenPayload(
      "mcp-doctor.json",
      result,
      "<DB_PATH>" to tempDir.resolve("metrics.db").toAbsolutePath().normalize().toString(),
      "<VERSION>" to SkillBillVersion.VALUE,
    )
    assertEquals(SkillBillVersion.VALUE, result["version"])
    assertEquals(tempDir.resolve("metrics.db").toAbsolutePath().normalize().toString(), result["db_path"])
    assertFalse(result["db_exists"] as Boolean)
    assertEquals(false, result["telemetry_enabled"])
    assertEquals("off", result["telemetry_level"])
  }

  @Test
  fun `doctor matches cli shared system service payload`() {
    val tempDir = Files.createTempDirectory("skillbill-mcp-cli-shared-doctor")
    val env = disabledTelemetryEnvironment(tempDir)
    val dbPath = tempDir.resolve("metrics.db")
    val cliResult =
      CliRuntime.run(
        listOf("--db", dbPath.toString(), "doctor", "--format", "json"),
        CliRuntimeContext(environment = env, userHome = tempDir),
      )

    assertEquals(0, cliResult.exitCode, cliResult.stdout)
    assertEquals(
      decodeJsonObject(cliResult.stdout),
      McpRuntimeContext(environment = env, userHome = tempDir).callToolPayload("doctor"),
    )
  }

  @Test
  fun `update check cli and mcp expose the same contract fields`() {
    val tempDir = Files.createTempDirectory("skillbill-mcp-update-check")
    val environment = disabledTelemetryEnvironment(tempDir)
    val requester =
      RemoteTransportPort { _, _, _, _ ->
        RemoteTransportResponse(
          200,
          """[{"tag_name":"v99.0.0","prerelease":false,"draft":false,"html_url":"https://example.test/v99.0.0"}]""",
        )
      }
    val cli =
      CliRuntime.run(
        listOf("update-check", "--format", "json"),
        CliRuntimeContext(environment = environment, userHome = tempDir, requester = requester),
      )
    val mcp =
      McpRuntimeContext(environment = environment, userHome = tempDir, requester = requester)
        .callToolPayload("update_check")

    assertEquals(0, cli.exitCode, cli.stdout)
    assertEquals(cli.payload, mcp)
    assertEquals("https://example.test/v99.0.0", mcp["release_url"])
    assertEquals(
      setOf(
        "status",
        "installed_version",
        "latest_version",
        "release_url",
        "recommended_install_command",
        "reason",
        "release_notes",
      ),
      mcp.keys,
    )
  }
}
