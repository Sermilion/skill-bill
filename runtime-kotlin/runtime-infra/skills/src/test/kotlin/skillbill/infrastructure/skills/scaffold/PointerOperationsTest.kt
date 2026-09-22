package skillbill.infrastructure.skills.scaffold

import org.junit.jupiter.api.Assumptions
import skillbill.infrastructure.skills.scaffold.pointer.PointerOperations
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PointerOperationsTest {
  private val temp: Path = Files.createTempDirectory("skillbill-pointer-ops-")

  @AfterTest
  fun cleanup() {
    if (Files.exists(temp)) {
      Files.walk(temp).use { stream ->
        stream.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
      }
    }
  }

  @Test
  fun `regenerate is a no-op when platform-packs directory is absent`() {
    val repoRoot = temp.resolve("repo-with-no-packs")
    Files.createDirectories(repoRoot)
    val result = PointerOperations.regenerate(repoRoot)
    assertTrue(result.regeneratedFiles.isEmpty(), "expected empty list, got ${result.regeneratedFiles}")
  }

  @Test
  fun `second regenerate writes nothing when on-disk pointers already match`() {
    val repoRoot = setupRepoWithTwoPointers()
    val first = PointerOperations.regenerate(repoRoot)
    assertTrue(first.regeneratedFiles.isNotEmpty(), "first regenerate should have written pointers")

    val second = PointerOperations.regenerate(repoRoot)
    assertTrue(
      second.regeneratedFiles.isEmpty(),
      "expected idempotent re-run; got ${second.regeneratedFiles}",
    )
  }

  @Test
  fun `regenerate returns regeneratedFiles sorted by absolute path`() {
    val repoRoot = setupRepoWithTwoPointers()
    val result = PointerOperations.regenerate(repoRoot)
    val rendered = result.regeneratedFiles.map { it.toString() }
    assertEquals(rendered.sorted(), rendered, "expected regeneratedFiles to be sorted")
  }

  @Test
  fun `createdPaths contains only files that did not previously exist`() {
    val repoRoot = setupRepoWithTwoPointers()
    val pointerB =
      repoRoot.resolve(
        "platform-packs/fixturepack/code-review/skill/b.md",
      )
    Files.createDirectories(pointerB.parent)
    Files.writeString(pointerB, "../../../shared/b.md")
    val original = Files.readAllBytes(pointerB)

    val originalBytes = mutableMapOf<Path, ByteArray>()
    val createdPaths = mutableListOf<Path>()
    val result =
      PointerOperations.regenerate(
        repoRoot,
        originalBytes = originalBytes,
        createdPaths = createdPaths,
      )

    assertTrue(result.regeneratedFiles.isNotEmpty(), "expected pointers to be written")
    val createdNames = createdPaths.map { it.fileName.toString() }
    assertTrue("a.md" in createdNames, "expected newly-created a.md to be tracked: $createdNames")
    assertFalse("b.md" in createdNames, "expected pre-existing b.md to NOT be in createdPaths: $createdNames")

    assertContentEquals(
      original,
      originalBytes[pointerB.normalize()] ?: error("expected originalBytes entry for $pointerB"),
    )
  }

  @Test
  fun `originalBytes is sticky across multiple regenerate calls and not clobbered by overwrites`() {
    val repoRoot = setupRepoWithTwoPointers()
    val pointerA =
      repoRoot.resolve(
        "platform-packs/fixturepack/code-review/skill/a.md",
      ).normalize()
    Files.createDirectories(pointerA.parent)
    val veryFirstBytes = "ORIGINAL CONTENT".toByteArray(Charsets.UTF_8)
    Files.write(pointerA, veryFirstBytes)

    val originalBytes = mutableMapOf<Path, ByteArray>()
    val createdPaths = mutableListOf<Path>()

    PointerOperations.regenerate(repoRoot, originalBytes, createdPaths)
    assertContentEquals(
      veryFirstBytes,
      originalBytes[pointerA] ?: error("expected originalBytes entry after first regenerate"),
    )

    Files.write(pointerA, "DRIFTED".toByteArray(Charsets.UTF_8))
    PointerOperations.regenerate(repoRoot, originalBytes, createdPaths)

    assertContentEquals(
      veryFirstBytes,
      originalBytes[pointerA] ?: error("expected originalBytes still set after second regenerate"),
      "originalBytes must hold the FIRST pre-existing bytes, not the drifted bytes",
    )
  }

  @Test
  fun `regenerate replaces a stale symlink with one pointing at the correct target`() {
    val repoRoot = setupRepoWithTwoPointers()
    val pointerA =
      repoRoot.resolve(
        "platform-packs/fixturepack/code-review/skill/a.md",
      ).normalize()
    Files.createDirectories(pointerA.parent)

    val symlinksSupported =
      runCatching {
        Files.createSymbolicLink(pointerA, Path.of("../../../wrong/target.md"))
      }.fold(onSuccess = { true }, onFailure = { it !is FileSystemException && it !is UnsupportedOperationException })
    Assumptions.assumeTrue(symlinksSupported, "symlinks unsupported on this filesystem")

    val result = PointerOperations.regenerate(repoRoot)

    assertTrue(
      pointerA in result.regeneratedFiles,
      "expected stale symlink to be regenerated; got ${result.regeneratedFiles}",
    )
    assertTrue(
      Files.isSymbolicLink(pointerA),
      "expected pointer to remain a symlink after regenerate",
    )
    val target = Files.readSymbolicLink(pointerA).toString().replace('\\', '/')
    assertEquals("../../../../shared/a.md", target, "symlink should point at the rendered target")
  }

  @Test
  fun `regenerate replaces a regular text file with a symlink when supported`() {
    val repoRoot = setupRepoWithTwoPointers()
    val pointerA =
      repoRoot.resolve(
        "platform-packs/fixturepack/code-review/skill/a.md",
      ).normalize()
    Files.createDirectories(pointerA.parent)
    Files.writeString(pointerA, "../../../stale/a.md")

    val originalBytes = mutableMapOf<Path, ByteArray>()
    val createdPaths = mutableListOf<Path>()
    val result = PointerOperations.regenerate(repoRoot, originalBytes, createdPaths)

    assertTrue(
      pointerA in result.regeneratedFiles,
      "expected stale text-file pointer to be regenerated; got ${result.regeneratedFiles}",
    )
    assertFalse(pointerA in createdPaths, "pre-existing pointer must NOT be tracked as created")
    val recovered = originalBytes[pointerA] ?: error("expected originalBytes for pointerA")
    assertEquals(
      "../../../stale/a.md",
      String(recovered, Charsets.UTF_8),
      "originalBytes must hold the pre-existing text-file bytes",
    )

    if (Files.isSymbolicLink(pointerA)) {
      val target = Files.readSymbolicLink(pointerA).toString().replace('\\', '/')
      assertEquals("../../../../shared/a.md", target)
    } else {
      assertEquals("../../../../shared/a.md", Files.readString(pointerA).trimEnd('\n', '\r'))
    }
  }

  @Test
  fun `regenerate is a no-op when an existing symlink already points at the correct target`() {
    val repoRoot = setupRepoWithTwoPointers()
    val pointerA =
      repoRoot.resolve(
        "platform-packs/fixturepack/code-review/skill/a.md",
      ).normalize()
    Files.createDirectories(pointerA.parent)
    val symlinksSupported =
      runCatching {
        Files.createSymbolicLink(pointerA, Path.of("../../../../shared/a.md"))
      }.fold(onSuccess = { true }, onFailure = { it !is FileSystemException && it !is UnsupportedOperationException })
    Assumptions.assumeTrue(symlinksSupported, "symlinks unsupported on this filesystem")

    val result = PointerOperations.regenerate(repoRoot)

    assertFalse(
      pointerA in result.regeneratedFiles,
      "matching symlink must not be rewritten; got regenerated=${result.regeneratedFiles}",
    )
    assertTrue(Files.isSymbolicLink(pointerA), "matching symlink must be left untouched as a symlink")
  }

  @Test
  fun `regenerate does NOT write through an existing symlink to corrupt its target`() {
    val repoRoot = setupRepoWithTwoPointers()
    val pointerA =
      repoRoot.resolve(
        "platform-packs/fixturepack/code-review/skill/a.md",
      ).normalize()
    val sharedA = repoRoot.resolve("shared/a.md").normalize()
    Files.createDirectories(pointerA.parent)
    val symlinksSupported =
      runCatching {
        Files.createSymbolicLink(pointerA, Path.of("../../../wrong/target.md"))
      }.fold(onSuccess = { true }, onFailure = { it !is FileSystemException && it !is UnsupportedOperationException })
    Assumptions.assumeTrue(symlinksSupported, "symlinks unsupported on this filesystem")
    val sharedABefore = Files.readAllBytes(sharedA)

    PointerOperations.regenerate(repoRoot)

    val sharedAAfter = Files.readAllBytes(sharedA)
    assertContentEquals(
      sharedABefore,
      sharedAAfter,
      "regenerate must never write through an existing symlink onto its target file",
    )
  }

  private fun setupRepoWithTwoPointers(): Path {
    val repoRoot = temp.resolve("repo-${System.nanoTime()}")
    Files.createDirectories(repoRoot.resolve("shared"))
    Files.writeString(repoRoot.resolve("shared/a.md"), "# a")
    Files.writeString(repoRoot.resolve("shared/b.md"), "# b")
    val packRoot = repoRoot.resolve("platform-packs/fixturepack")
    Files.createDirectories(packRoot.resolve("code-review/skill"))
    Files.writeString(
      packRoot.resolve("platform.yaml"),
      """
      platform: fixturepack
      contract_version: "1.8"

      routing_signals:
        strong:
          - ".fixture"

      declared_code_review_areas: []

      declared_files:
        baseline: code-review/skill/content.md

      area_metadata: {}

      pointers:
        code-review/skill:
          - name: a.md
            target: shared/a.md
          - name: b.md
            target: shared/b.md
      """.trimIndent(),
    )
    return repoRoot
  }
}
