package skillbill.cli

import skillbill.cli.core.CliRuntime
import skillbill.cli.model.CliRuntimeContext
import skillbill.ports.system.HostPlatformPort
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CliRunInputsRuntimeTest {
  @Test
  fun `omitted repository root resolves from the embedding invocation`() {
    val repoRoot = Files.createTempDirectory("skillbill-cli-config-injected-root")
    Files.createDirectories(repoRoot.resolve(".skill-bill"))
    Files.writeString(repoRoot.resolve(".skill-bill/config.yaml"), "spec_type: linear\n")

    val result = CliRuntime.run(
      listOf("config", "resolve-spec-type"),
      CliRuntimeContext(
        repositoryRoot = repoRoot,
        userHome = repoRoot,
        environment = emptyMap(),
      ),
    )

    assertEquals(0, result.exitCode, result.stdout)
    assertEquals("linear", result.stdout.trim())
  }

  @Test
  fun `relative explicit repository root resolves against the process directory`() {
    val embeddingRoot = Files.createTempDirectory("skillbill-cli-relative-embedding")
    val processRoot = Path.of("").toAbsolutePath().normalize()
    val relativeRoot = processRoot.resolve("build/skillbill-cli-relative-root-${System.nanoTime()}")
    Files.createDirectories(relativeRoot.resolve(".skill-bill"))
    Files.writeString(relativeRoot.resolve(".skill-bill/config.yaml"), "spec_type: linear\n")

    try {
      val relativePath = processRoot.relativize(relativeRoot).toString()
      val result = CliRuntime.run(
        listOf("config", "resolve-spec-type", "--repo-root", relativePath),
        CliRuntimeContext(
          repositoryRoot = embeddingRoot,
          userHome = embeddingRoot,
          environment = emptyMap(),
        ),
      )

      assertEquals(0, result.exitCode, result.stdout)
      assertEquals("linear", result.stdout.trim())
    } finally {
      relativeRoot.toFile().deleteRecursively()
    }
  }

  @Test
  fun `--home beats the embedding context for an adapter-resolved path in both option forms`() {
    val contextHome = Files.createTempDirectory("skillbill-cli-context-home")
    val selectedHome = Files.createTempDirectory("skillbill-cli-selected-home")
    val expected = selectedHome.resolve(".skill-bill/review-metrics.db").toAbsolutePath().normalize().toString()

    val spaced = goalStats(listOf("--home", selectedHome.toString()), contextHome)
    val joined = goalStats(listOf("--home=$selectedHome"), contextHome)

    assertEquals(0, spaced.exitCode, spaced.stdout)
    assertEquals(expected, decodeJsonObject(spaced.stdout)["db_path"])
    assertEquals(0, joined.exitCode, joined.stdout)
    assertEquals(expected, decodeJsonObject(joined.stdout)["db_path"])
    assertFalse(Files.exists(contextHome.resolve(".skill-bill")))
  }

  @Test
  fun `commands resolve the injected repository root, not the process directory`() {
    val repoRoot = Files.createTempDirectory("skillbill-cli-injected-root")
    Files.createDirectories(repoRoot.resolve(".git"))
    val realRoot = repoRoot.toRealPath()
    val specsRoot = realRoot.resolve(".feature-specs").toString()
    val featureTask = listOf("feature-task", "SKILL-901")

    val injectedRoot = CliRuntime.run(featureTask, injectedRootContext(repoRoot))
    val explicitRoot = CliRuntime.run(
      featureTask + listOf("--repo-root", realRoot.toString()),
      injectedRootContext(repoRoot),
    )
    val processRoot = CliRuntime.run(
      featureTask,
      CliRuntimeContext(environment = emptyMap(), userHome = repoRoot),
    )

    assertTrue(injectedRoot.stdout.contains(specsRoot), injectedRoot.stdout)
    assertTrue(explicitRoot.stdout.contains(specsRoot), explicitRoot.stdout)
    assertEquals(injectedRoot.exitCode, processRoot.exitCode, processRoot.stdout)
    assertTrue(processRoot.stdout.contains("SKILL-901"), processRoot.stdout)
    assertFalse(processRoot.stdout.contains(specsRoot), processRoot.stdout)

    val scaffold = CliRuntime.run(
      listOf("new", "--dry-run", "--format", "json"),
      injectedRootContext(repoRoot).copy(
        stdinText = "horizontal\nbill-injected-root-skill\nInjected root skill.\n",
      ),
    )

    assertEquals(0, scaffold.exitCode, scaffold.stdout)
    assertEquals(
      realRoot.resolve("skills/bill-injected-root-skill").toString(),
      decodeJsonObject(scaffold.stdout)["skill_path"],
    )
  }

  @Test
  fun `db path override reaches the session factory from the context alone and from the flag alone`() {
    val tempDir = Files.createTempDirectory("skillbill-cli-run-inputs-db")
    val dbPath = tempDir.resolve("metrics.db")
    val expected = dbPath.toAbsolutePath().normalize().toString()

    val fromContext = CliRuntime.run(
      listOf("goal-stats", "--format", "json"),
      CliRuntimeContext(dbPathOverride = dbPath.toString(), environment = emptyMap()),
    )
    val fromFlag = CliRuntime.run(
      listOf("--db", dbPath.toString(), "goal-stats", "--format", "json"),
      CliRuntimeContext(environment = emptyMap()),
    )

    assertEquals(0, fromContext.exitCode, fromContext.stdout)
    assertEquals(expected, decodeJsonObject(fromContext.stdout)["db_path"])
    assertEquals(0, fromFlag.exitCode, fromFlag.stdout)
    assertEquals(expected, decodeJsonObject(fromFlag.stdout)["db_path"])
  }

  @Test
  fun `uninstall previews the desktop layout reported by the host platform port`() {
    val home = Files.createTempDirectory("skillbill-cli-host-platform")

    val result = CliRuntime.run(
      listOf("--home", home.toString(), "uninstall", "--dry-run", "--format", "json"),
      CliRuntimeContext(
        environment = emptyMap(),
        userHome = home,
        hostPlatformPort = StubHostPlatformPort("Windows 11"),
      ),
    )

    assertEquals(0, result.exitCode, result.stdout)
    val desktop = decodeJsonObject(result.stdout)["desktop"] as Map<*, *>
    assertEquals(null, desktop["launcher"])
    assertEquals(listOf(home.resolve(".local/bin/skillbill-desktop.cmd").toString()), desktop["files"])
    assertEquals(
      listOf(home.resolve("AppData/Local/SkillBill/Desktop/SkillBill").toString()),
      desktop["directories"],
    )
  }

  private fun injectedRootContext(repoRoot: Path) = CliRuntimeContext(
    environment = emptyMap(),
    userHome = repoRoot,
    repositoryRoot = repoRoot,
  )

  private fun goalStats(rootFlags: List<String>, contextHome: Path) = CliRuntime.run(
    rootFlags + listOf("goal-stats", "--format", "json"),
    CliRuntimeContext(environment = emptyMap(), userHome = contextHome),
  )
}

private class StubHostPlatformPort(override val osName: String) : HostPlatformPort {
  override fun resolveUserHome(): java.nio.file.Path = java.nio.file.Path.of("/tmp")
  override fun resolveEnvironment(): Map<String, String> = emptyMap()
  override fun resolveJavaHome(): java.nio.file.Path = java.nio.file.Path.of("/java")
  override fun resolveWorkingDirectory(): java.nio.file.Path = java.nio.file.Path.of("/workspace")
  override fun resolveTemporaryDirectory(): java.nio.file.Path = java.nio.file.Path.of("/tmp")
  override val jvmClassPath: String = ""
  override val pathSeparator: String = ":"
}
