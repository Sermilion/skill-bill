package skillbill.mcp.scaffold

import skillbill.mcp.shared.McpRuntimeContext
import skillbill.mcp.shared.assertGoldenPayload
import skillbill.mcp.shared.assertMatchesPattern
import skillbill.mcp.shared.callToolPayload
import skillbill.mcp.shared.disabledTelemetryEnvironment
import skillbill.mcp.shared.enabledTelemetryEnvironment
import skillbill.mcp.shared.pathIsUnderRoot
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class McpScaffoldToolTest {
  @Test
  fun `new skill scaffold preserves standalone and orchestrated payload envelopes`() {
    val tempDir = Files.createTempDirectory("skillbill-mcp-scaffold")
    val context = McpRuntimeContext(environment = enabledTelemetryEnvironment(tempDir), userHome = tempDir)
    val payload =
      mapOf(
        "scaffold_payload_version" to "1.0",
        "kind" to "horizontal",
        "name" to "bill-horizontal-mcp",
      )

    val standalone =
      context.callToolPayload(
        "new_skill_scaffold",
        mapOf("payload" to payload, "dry_run" to true, "orchestrated" to false),
      )
    val orchestrated =
      context.callToolPayload(
        "new_skill_scaffold",
        mapOf("payload" to payload, "dry_run" to true, "orchestrated" to true),
      )
    val standaloneSessionId = standalone["session_id"] as String
    val standaloneSkillPath = standalone["skill_path"] as String

    assertMatchesPattern(Regex("""^nss-\d{8}-[0-9a-f]{4}$"""), standaloneSessionId, "session_id")
    assertTrue(Path.of(standaloneSkillPath).isAbsolute, "Expected absolute skill_path but got $standaloneSkillPath")
    assertTrue(
      standaloneSkillPath.endsWith("/skills/bill-horizontal-mcp"),
      "Expected skill_path to target skills/bill-horizontal-mcp but got $standaloneSkillPath",
    )
    assertGoldenPayload(
      "mcp-new-skill-scaffold.json",
      standalone,
      "<SESSION_ID>" to standaloneSessionId,
      "<SKILL_PATH>" to standaloneSkillPath,
    )
    assertEquals("ok", standalone["status"])
    assertTrue("session_id" in standalone)
    assertTrue("skill_path" in standalone)
    assertTrue("notes" in standalone)
    assertTrue("mode" !in standalone)
    assertTrue("telemetry_payload" !in standalone)

    assertEquals("orchestrated", orchestrated["mode"])
    assertTrue("telemetry_payload" in orchestrated)
    val telemetryPayload = orchestrated["telemetry_payload"] as Map<*, *>
    assertEquals("skill-bill-scaffold", telemetryPayload["skill"])
    assertEquals("dry-run", telemetryPayload["result"])
    assertEquals("horizontal", telemetryPayload["kind"])
    assertEquals("bill-horizontal-mcp", telemetryPayload["skill_name"])
    assertEquals(standaloneSkillPath, orchestrated["skill_path"])
    assertTrue("skill_path" in orchestrated)
    assertTrue("notes" in orchestrated)
  }

  @Test
  fun `mcp scaffold uses explicit repo root and invocation root as the shared defaults`() {
    val invocationRoot = Files.createTempDirectory("skillbill-mcp-scaffold-invocation-root")
    val explicitRoot = Files.createTempDirectory("skillbill-mcp-scaffold-explicit-root")
    val context =
      McpRuntimeContext(
        environment = disabledTelemetryEnvironment(invocationRoot),
        userHome = invocationRoot,
        repositoryRoot = invocationRoot,
      )
    val basePayload =
      mapOf(
        "scaffold_payload_version" to "1.0",
        "kind" to "horizontal",
        "name" to "bill-mcp-repo-root-parity",
      )

    val defaultResult =
      context.callToolPayload("new_skill_scaffold", mapOf("payload" to basePayload, "dry_run" to true))
    val explicitResult =
      context.callToolPayload(
        "new_skill_scaffold",
        mapOf("payload" to basePayload + ("repo_root" to explicitRoot.toString()), "dry_run" to true),
      )

    val defaultSkillPath = Path.of(defaultResult["skill_path"] as String)
    val explicitSkillPath = Path.of(explicitResult["skill_path"] as String)
    assertTrue(
      pathIsUnderRoot(defaultSkillPath, invocationRoot),
      "default skill path $defaultSkillPath was not under $invocationRoot",
    )
    assertTrue(
      pathIsUnderRoot(explicitSkillPath, explicitRoot),
      "explicit skill path $explicitSkillPath was not under $explicitRoot",
    )
  }
}
