package skillbill.architecture

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TelemetryTestIsolationArchitectureTest {
  @Test
  fun `tests that run the real runtime with telemetry on use the reserved identity or a custom proxy`() {
    val runtimeEntryTests = runtimeTestSourceFiles().filter(::buildsRealRuntimeContext)
    assertTrue(runtimeEntryTests.isNotEmpty(), "The scan must see the CLI and MCP runtime tests to mean anything.")

    val leaking = runtimeEntryTests.filter(::canUploadToHostedRelay).map(SourceFile::relativePath).sorted()

    assertEquals(
      emptyList(),
      leaking,
      "These tests enable telemetry against the hosted relay under a non-reserved install id, so a run on a " +
        "networked machine uploads fixtures to production. Use RESERVED_TEST_INSTALL_ID or a custom proxy URL.",
    )
  }

  @Test
  fun `the scan flags either runtime entry point under an ordinary install id and clears a custom proxy`() {
    val leaking =
      syntheticSourceFile(
        "test-fixture/LeakingRuntimeTest.kt",
        """
        val context = McpRuntimeContext(environment = env, userHome = dir)
        val config = ${"\"\"\""}{"install_id": "doctor-install-id", "telemetry": {"level": "anonymous", "proxy_url": ""}}${"\"\"\""}
        """.trimIndent(),
      )
    val leakingCli =
      syntheticSourceFile(
        "test-fixture/LeakingCliRuntimeTest.kt",
        """
        val context = CliRuntimeContext(environment = env, userHome = dir)
        val config = ${"\"\"\""}{"install_id": "doctor-install-id", "telemetry": {"level": "full", "proxy_url": ""}}${"\"\"\""}
        """.trimIndent(),
      )
    val isolated =
      syntheticSourceFile(
        "test-fixture/IsolatedRuntimeTest.kt",
        """
        val context = McpRuntimeContext(environment = env, userHome = dir)
        val config = ${"\"\"\""}{"install_id": "test-install-id", "telemetry": {"level": "anonymous", "proxy_url": ""}}${"\"\"\""}
        """.trimIndent(),
      )
    val isolatedByCustomProxy =
      syntheticSourceFile(
        "test-fixture/CustomProxyRuntimeTest.kt",
        """
        val context = CliRuntimeContext(environment = env, userHome = dir)
        val config = ${"\"\"\""}{"install_id": "doctor-install-id", "telemetry": {"level": "anonymous", "proxy_url": "http://127.0.0.1/x"}}${"\"\"\""}
        """.trimIndent(),
      )

    assertTrue(buildsRealRuntimeContext(leaking) && canUploadToHostedRelay(leaking))
    assertTrue(buildsRealRuntimeContext(leakingCli) && canUploadToHostedRelay(leakingCli))
    assertTrue(!canUploadToHostedRelay(isolated))
    assertTrue(buildsRealRuntimeContext(isolatedByCustomProxy) && !canUploadToHostedRelay(isolatedByCustomProxy))
  }
}

private val runtimeContextConstructor = Regex("""\b(CliRuntimeContext|McpRuntimeContext)\(""")

private val telemetryEnabledLevel =
  Regex(
    """"level"\s*(:|to)\s*"(anonymous|full)"|""" +
      """TELEMETRY_LEVEL_ENVIRONMENT_KEY\s+to\s+"(anonymous|full)"""",
  )

private val isolatedTarget =
  Regex(
    """test-install-id|RESERVED_TEST_INSTALL_ID|""" +
      """"proxy_url"\s*(:|to)\s*"https?://(?!skill-bill-telemetry-proxy)|""" +
      """TELEMETRY_PROXY_URL_ENVIRONMENT_KEY\s+to\s+"https?://""",
  )

private fun buildsRealRuntimeContext(file: SourceFile): Boolean = runtimeContextConstructor.containsMatchIn(file.source)

private fun canUploadToHostedRelay(file: SourceFile): Boolean =
  telemetryEnabledLevel.containsMatchIn(file.source) && !isolatedTarget.containsMatchIn(file.source)

private fun runtimeTestSourceFiles(): List<SourceFile> =
  RuntimeModuleCatalog.declaredGradleModules
    .map(RuntimeModuleCatalog::runtimeKotlinModuleDirectory)
    .map(runtimeArchitectureRoot::resolve)
    .map { moduleRoot -> moduleRoot.resolve("src/test/kotlin") }
    .filter(Files::isDirectory)
    .flatMap(::sourceFilesIn)
