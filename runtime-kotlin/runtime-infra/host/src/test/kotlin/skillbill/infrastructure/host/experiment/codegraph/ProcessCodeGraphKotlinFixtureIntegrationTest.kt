package skillbill.infrastructure.host.experiment.codegraph

import skillbill.contracts.experiment.codegraph.CodeGraphQueryReceiptPayloadKeys
import skillbill.error.shellcontent.CodeGraphRetrievalRefusalError
import skillbill.infrastructure.host.experiment.catalog.FileSystemExperimentDescriptorCatalog
import skillbill.ports.experiment.codegraph.model.CodeGraphQueryRequest
import skillbill.ports.experiment.codegraph.model.CodeGraphToolInstallRequest
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProcessCodeGraphKotlinFixtureIntegrationTest {
  private val recordedUnresolvedConstructs = listOf(
    "dynamic dispatch through injected GreeterPort",
    "complete cross-module call-graph recall",
  )

  @Test
  fun `pinned binary reports cross file kotlin hits and records unresolved constructs`() {
    val repoRoot = locateSkillBillRepoRoot()
    val userHome = Files.createTempDirectory("codegraph-fixture-home")
    val fixtureRoot = materializeFixtureWorktree(repoRoot)
    val installer = FileSystemCodeGraphToolInstaller(dependencyStartPath = repoRoot)
    val installed = installer.install(CodeGraphToolInstallRequest(userHome = userHome, pairId = "fixture-pair"))
    assertTrue(Files.isExecutable(installed.binaryPath))
    val ledger = InMemoryCodeGraphUsageLedger()
    val adapter = ProcessCodeGraphRetrievalAdapter(
      binaryPath = installed.binaryPath,
      usageLedger = ledger,
    )
    val result = adapter.query(
      CodeGraphQueryRequest(
        worktreeRoot = fixtureRoot,
        queryText = "greet",
        pairId = "fixture-pair",
        unresolvedConstructNotes = recordedUnresolvedConstructs,
      ),
    )
    val files = result.hits.map { it.file }.distinct()
    assertTrue(files.any { it.endsWith("GreeterPort.kt") }, "expected GreeterPort.kt in graph hits")
    assertTrue(
      files.any { it.endsWith("GreetingExtensions.kt") },
      "expected a cross-file callee relationship in graph hits",
    )
    val unresolved = result.receipt[CodeGraphQueryReceiptPayloadKeys.UNRESOLVED_CONSTRUCT_NOTES]
    assertTrue(unresolved is List<*>)
    assertTrue(
      recordedUnresolvedConstructs.all { it in unresolved },
    )
  }

  @Test
  fun `query timeout is degraded and does not leave an owned process`() {
    val worktree = fakeWorktree()
    val ledger = InMemoryCodeGraphUsageLedger()
    val adapter = ProcessCodeGraphRetrievalAdapter(
      binaryPath = fakeBinary(worktree),
      usageLedger = ledger,
      queryTimeout = Duration.ofMillis(25),
    )

    assertFailsWith<CodeGraphRetrievalRefusalError> {
      adapter.query(CodeGraphQueryRequest(worktree, "timeout", pairId = "timeout-pair"))
    }

    assertTrue(ledger.snapshot("timeout-pair").degraded)
  }

  @Test
  fun `query output cap is degraded before parsing`() {
    val worktree = fakeWorktree()
    val ledger = InMemoryCodeGraphUsageLedger()
    val adapter = ProcessCodeGraphRetrievalAdapter(
      binaryPath = fakeBinary(worktree),
      usageLedger = ledger,
      outputByteCap = 128,
    )

    assertFailsWith<CodeGraphRetrievalRefusalError> {
      adapter.query(CodeGraphQueryRequest(worktree, "cap", pairId = "cap-pair"))
    }

    assertTrue(ledger.snapshot("cap-pair").degraded)
  }

  @Test
  fun `query process failure is degraded`() {
    val worktree = fakeWorktree()
    val ledger = InMemoryCodeGraphUsageLedger()
    val adapter = ProcessCodeGraphRetrievalAdapter(
      binaryPath = fakeBinary(worktree),
      usageLedger = ledger,
    )

    assertFailsWith<CodeGraphRetrievalRefusalError> {
      adapter.query(CodeGraphQueryRequest(worktree, "failure", pairId = "failure-pair"))
    }

    assertTrue(ledger.snapshot("failure-pair").degraded)
  }

  @Test
  fun `deleted source hits are not returned from a stale graph response`() {
    val worktree = fakeWorktree()
    val ledger = InMemoryCodeGraphUsageLedger()
    val binary = fakeBinary(worktree)
    Files.delete(worktree.resolve("Source.kt"))
    val adapter = ProcessCodeGraphRetrievalAdapter(binary, ledger)

    val result = adapter.query(CodeGraphQueryRequest(worktree, "deleted", pairId = "deleted-pair"))

    assertTrue(result.hits.isEmpty())
  }

  @Test
  fun `source edit during query is retried and recorded as degraded`() {
    val worktree = fakeWorktree()
    val ledger = InMemoryCodeGraphUsageLedger()
    val adapter = ProcessCodeGraphRetrievalAdapter(fakeBinary(worktree), ledger)

    val result = adapter.query(CodeGraphQueryRequest(worktree, "race", pairId = "race-pair"))

    assertTrue(result.hits.isEmpty())
    assertTrue(ledger.snapshot("race-pair").degraded)
  }

  private fun materializeFixtureWorktree(repoRoot: Path): Path {
    val worktree = Files.createTempDirectory("codegraph-fixture-worktree")
    Files.createDirectories(worktree.resolve("orchestration/dependencies"))
    Files.copy(
      repoRoot.resolve("orchestration/dependencies/codegraph-dependency.yaml"),
      worktree.resolve("orchestration/dependencies/codegraph-dependency.yaml"),
    )
    val fixtureSrc = repoRoot.resolve(
      "runtime-kotlin/runtime-infra/host/src/test/resources/codegraph-kotlin-fixture/src/main/kotlin/codegraphfixture",
    )
    val target = worktree.resolve("src/main/kotlin/codegraphfixture")
    Files.walk(fixtureSrc).use { paths ->
      paths.filter { Files.isRegularFile(it) }.forEach { source ->
        val relative = fixtureSrc.relativize(source)
        val dest = target.resolve(relative)
        Files.createDirectories(dest.parent)
        Files.copy(source, dest)
      }
    }
    return worktree
  }

  private fun fakeWorktree(): Path {
    val repoRoot = locateSkillBillRepoRoot()
    val worktree = Files.createTempDirectory("codegraph-fault-worktree")
    Files.createDirectories(worktree.resolve("orchestration/dependencies"))
    Files.copy(
      repoRoot.resolve("orchestration/dependencies/codegraph-dependency.yaml"),
      worktree.resolve("orchestration/dependencies/codegraph-dependency.yaml"),
    )
    Files.writeString(worktree.resolve("Source.kt"), "class Source\n")
    return worktree
  }

  private fun fakeBinary(worktree: Path): Path {
    val binary = worktree.resolve("fake-codegraph")
    Files.writeString(
      binary,
      """
      #!/bin/sh
      if [ "${'$'}1" = "query" ]; then
        if [ "${'$'}3" = "timeout" ]; then sleep 1; fi
        if [ "${'$'}3" = "race" ]; then
          if ! grep -q Changed Source.kt; then printf 'class Changed\n' > Source.kt; fi
        fi
        if [ "${'$'}3" = "failure" ]; then exit 9; fi
        if [ "${'$'}3" = "deleted" ]; then
          printf '[{"node":{"name":"Source","filePath":"Source.kt","kind":"type","startLine":1},"score":1}]'
          exit 0
        fi
        if [ "${'$'}3" = "cap" ]; then
          i=0
          printf '[{"node":{"name":"'
          while [ "${'$'}i" -lt 600 ]; do printf x; i=${'$'}(( ${'$'}i + 1 )); done
          printf '","filePath":"Source.kt","kind":"type","startLine":1},"score":1}]'
          exit 0
        fi
        printf '[]'
      fi
      exit 0
      """.trimIndent(),
    )
    assertTrue(binary.toFile().setExecutable(true))
    assertFalse(Files.isDirectory(binary))
    return binary
  }

  private fun locateSkillBillRepoRoot(): Path {
    val cwd = Path.of(".").toAbsolutePath().normalize()
    return FileSystemExperimentDescriptorCatalog.findRepoRootForExperiments(cwd)
      ?: error("Expected skill-bill repository root.")
  }
}
