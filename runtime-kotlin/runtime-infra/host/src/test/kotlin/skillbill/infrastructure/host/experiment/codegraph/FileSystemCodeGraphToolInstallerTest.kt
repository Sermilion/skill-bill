package skillbill.infrastructure.host.experiment.codegraph

import skillbill.error.shellcontent.CodeGraphInstallRefusalError
import skillbill.infrastructure.host.experiment.catalog.FileSystemExperimentDescriptorCatalog
import skillbill.ports.experiment.codegraph.model.CodeGraphToolInstallRequest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FileSystemCodeGraphToolInstallerTest {
  @Test
  fun `checksum mismatch refuses to leave a runnable binary`() {
    val repoRoot = locateSkillBillRepoRoot()
    val userHome = Files.createTempDirectory("codegraph-install-home")
    val installer = FileSystemCodeGraphToolInstaller(
      dependencyStartPath = repoRoot,
      transport = { _ -> byteArrayOf(1, 2, 3) },
    )

    assertFailsWith<CodeGraphInstallRefusalError> {
      installer.install(CodeGraphToolInstallRequest(userHome = userHome, pairId = "pair-1"))
    }
    val toolsRoot = CodeGraphToolLayout.toolsRoot(userHome)
    val binaries = if (Files.exists(toolsRoot)) {
      Files.walk(toolsRoot).use { paths -> paths.filter { Files.isRegularFile(it) }.toList() }
    } else {
      emptyList()
    }
    assertTrue(binaries.none { it.fileName.toString() == "codegraph" })
  }

  @Test
  fun `matching cached asset marker reuses offline binary without downloading`() {
    val repoRoot = locateSkillBillRepoRoot()
    val userHome = Files.createTempDirectory("codegraph-install-cache")
    val dependency = CodeGraphDependencyLoader.load(repoRoot)
    val releaseTag = (dependency["upstream"] as Map<*, *>)["release_tag"].toString()
    val binaryName = (dependency["cli"] as Map<*, *>)["binary_name"].toString()
    val platformId = CodeGraphHostPlatform.currentPlatformId()
      ?: error("test host must be supported")
    val asset = (dependency["platform_assets"] as List<*>)
      .filterIsInstance<Map<*, *>>()
      .first { it["platform_id"] == platformId }
    val digest = asset["sha256"].toString()
    val binaryPath = CodeGraphToolLayout.binaryPath(userHome, releaseTag, binaryName)
    Files.createDirectories(binaryPath.parent)
    Files.write(binaryPath, byteArrayOf(9, 9, 9))
    binaryPath.toFile().setExecutable(true)
    Files.writeString(CodeGraphToolLayout.assetDigestPath(userHome, releaseTag), digest)
    val installer = FileSystemCodeGraphToolInstaller(
      dependencyStartPath = repoRoot,
      transport = { _ -> error("download must not run when cache matches") },
    )
    val installed = installer.install(CodeGraphToolInstallRequest(userHome = userHome, pairId = "pair-2"))

    assertEquals(binaryPath, installed.binaryPath)
    assertEquals(digest, installed.sha256)
  }

  @Test
  fun `explicit executable override requires the pinned version and reports provenance`() {
    val repoRoot = locateSkillBillRepoRoot()
    val userHome = Files.createTempDirectory("codegraph-install-override-home")
    val override = userHome.resolve("codegraph")
    Files.writeString(
      override,
      """
      #!/bin/sh
      printf 'v1.6.0'
      """.trimIndent(),
    )
    override.toFile().setExecutable(true)

    val installed = FileSystemCodeGraphToolInstaller(repoRoot).install(
      CodeGraphToolInstallRequest(
        userHome = userHome,
        pairId = "pair-override",
        localExecutableOverride = override,
      ),
    )

    assertEquals(override, installed.binaryPath)
    assertEquals("local_override", installed.provenance)
  }

  @Test
  fun `owned cleanup refuses a release held by an active pair`() {
    val repoRoot = locateSkillBillRepoRoot()
    val userHome = Files.createTempDirectory("codegraph-cleanup-home")
    val versionRoot = CodeGraphToolLayout.versionRoot(userHome, "v1.6.0")
    Files.createDirectories(versionRoot)
    Files.writeString(versionRoot.resolve("codegraph"), "owned")

    val installer = FileSystemCodeGraphToolInstaller(repoRoot)
    assertFailsWith<CodeGraphInstallRefusalError> {
      installer.removeOwnedVersion(userHome, "v1.6.0", setOf("v1.6.0"))
    }

    assertTrue(Files.exists(versionRoot.resolve("codegraph")))
  }

  private fun locateSkillBillRepoRoot(): Path {
    val cwd = Path.of(".").toAbsolutePath().normalize()
    return FileSystemExperimentDescriptorCatalog.findRepoRootForExperiments(cwd)
      ?: error("Expected skill-bill repository root.")
  }
}
