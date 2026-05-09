@file:Suppress("MatchingDeclarationName", "ktlint:standard:filename")

package skillbill.scaffold

import skillbill.error.ShellContentContractException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.relativeTo

data class GeneratedArtifactGuardReport(
  val issues: List<String>,
) {
  val passed: Boolean = issues.isEmpty()
}

internal typealias TrackedRepoFilesProvider = (Path) -> Set<String>?

fun validateGeneratedArtifactGuard(repoRoot: Path): GeneratedArtifactGuardReport =
  validateGeneratedArtifactGuard(repoRoot, ::trackedRepoFiles)

internal fun validateGeneratedArtifactGuard(
  repoRoot: Path,
  trackedFilesProvider: TrackedRepoFilesProvider,
): GeneratedArtifactGuardReport {
  val root = repoRoot.toAbsolutePath().normalize()
  val trackedFiles = trackedFilesProvider(root)
  val issues = mutableListOf<String>()
  discoverGovernedSkillOutputs(root).forEach { skillFile ->
    val relative = displayGuardPath(root, skillFile)
    if (!shouldValidateCommittedArtifact(relative, trackedFiles)) {
      return@forEach
    }
    if (relative !in GRANDFATHERED_GOVERNED_SKILL_OUTPUTS) {
      issues += "$relative: newly committed governed SKILL.md output is not allowed; " +
        "author governed skill content in content.md and do not add new generated wrappers"
    }
  }
  discoverDeclaredPointerFiles(root).forEach { pointerFile ->
    val relative = displayGuardPath(root, pointerFile)
    if (!shouldValidateCommittedArtifact(relative, trackedFiles)) {
      return@forEach
    }
    if (relative !in GRANDFATHERED_PLATFORM_POINTER_FILES) {
      issues += "$relative: newly committed platform.yaml pointer file is not allowed; " +
        "existing generated pointer files are grandfathered only"
    }
  }
  return GeneratedArtifactGuardReport(issues.sorted())
}

private fun shouldValidateCommittedArtifact(relativePath: String, trackedFiles: Set<String>?): Boolean =
  trackedFiles == null || relativePath in trackedFiles

private fun trackedRepoFiles(root: Path): Set<String>? {
  val process = runCatching {
    ProcessBuilder("git", "-C", root.toString(), "ls-files")
      .redirectErrorStream(true)
      .start()
  }.getOrNull() ?: return null
  val output = process.inputStream.bufferedReader().use { reader -> reader.readText() }
  val exitCode = process.waitFor()
  return if (exitCode == 0) {
    output.lineSequence()
      .map(String::trim)
      .filter(String::isNotEmpty)
      .toSet()
  } else {
    null
  }
}

private fun discoverGovernedSkillOutputs(root: Path): List<Path> {
  val scanRoots = listOf(root.resolve("skills"), root.resolve("platform-packs")).filter { it.isDirectory() }
  return scanRoots.flatMap { scanRoot ->
    Files.walk(scanRoot).use { stream ->
      stream
        .filter { path -> path.name == "content.md" }
        .map { contentFile -> contentFile.resolveSibling("SKILL.md") }
        .filter { skillFile -> Files.isRegularFile(skillFile, LinkOption.NOFOLLOW_LINKS) }
        .toList()
    }
  }.sorted()
}

private fun discoverDeclaredPointerFiles(root: Path): List<Path> {
  val packsRoot = root.resolve("platform-packs")
  if (!packsRoot.isDirectory()) {
    return emptyList()
  }
  return Files.list(packsRoot).use { stream ->
    stream
      .filter { packRoot -> packRoot.isDirectory() && !packRoot.name.startsWith(".") }
      .flatMap { packRoot ->
        val pack = try {
          loadPlatformManifest(packRoot)
        } catch (_: ShellContentContractException) {
          return@flatMap emptyList<Path>().stream()
        }
        pack.pointers
          .map { spec -> pack.packRoot.resolve(spec.skillRelativeDir).resolve(spec.name).normalize() }
          .filter { pointerFile ->
            Files.isSymbolicLink(pointerFile) ||
              Files.isRegularFile(pointerFile, LinkOption.NOFOLLOW_LINKS)
          }
          .stream()
      }
      .toList()
      .sorted()
  }
}

private fun displayGuardPath(root: Path, path: Path): String {
  val resolvedRoot = root.toAbsolutePath().normalize()
  val resolvedPath = path.toAbsolutePath().normalize()
  return runCatching { resolvedPath.relativeTo(resolvedRoot).toString().replace('\\', '/') }
    .getOrDefault(resolvedPath.toString())
}

private val GRANDFATHERED_GOVERNED_SKILL_OUTPUTS = setOf(
  "platform-packs/kmp/code-review/bill-kmp-code-review-ui/SKILL.md",
  "platform-packs/kmp/code-review/bill-kmp-code-review-ux-accessibility/SKILL.md",
  "platform-packs/kmp/code-review/bill-kmp-code-review/SKILL.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-api-contracts/SKILL.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-architecture/SKILL.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-performance/SKILL.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-persistence/SKILL.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-platform-correctness/SKILL.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-reliability/SKILL.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-security/SKILL.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-testing/SKILL.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review/SKILL.md",
  "platform-packs/kotlin/quality-check/bill-kotlin-quality-check/SKILL.md",
  "skills/bill-boundary-decisions/SKILL.md",
  "skills/bill-boundary-history/SKILL.md",
  "skills/bill-code-review/SKILL.md",
  "skills/bill-create-skill/SKILL.md",
  "skills/bill-feature-guard-cleanup/SKILL.md",
  "skills/bill-feature-guard/SKILL.md",
  "skills/bill-feature-implement/SKILL.md",
  "skills/bill-feature-verify/SKILL.md",
  "skills/bill-grill-plan/SKILL.md",
  "skills/bill-pr-description/SKILL.md",
  "skills/bill-quality-check/SKILL.md",
  "skills/bill-skill-remove/SKILL.md",
  "skills/bill-unit-test-value-check/SKILL.md",
)

private val GRANDFATHERED_PLATFORM_POINTER_FILES = setOf(
  "platform-packs/kmp/code-review/bill-kmp-code-review-ui/android-compose-adaptive-layouts.md",
  "platform-packs/kmp/code-review/bill-kmp-code-review-ui/android-compose-edge-to-edge.md",
  "platform-packs/kmp/code-review/bill-kmp-code-review-ui/android-compose-review.md",
  "platform-packs/kmp/code-review/bill-kmp-code-review-ui/android-design-system-review.md",
  "platform-packs/kmp/code-review/bill-kmp-code-review-ui/android-interop-review.md",
  "platform-packs/kmp/code-review/bill-kmp-code-review-ui/android-navigation-review.md",
  "platform-packs/kmp/code-review/bill-kmp-code-review-ui/review-orchestrator.md",
  "platform-packs/kmp/code-review/bill-kmp-code-review-ui/shell-ceremony.md",
  "platform-packs/kmp/code-review/bill-kmp-code-review-ui/telemetry-contract.md",
  "platform-packs/kmp/code-review/bill-kmp-code-review-ux-accessibility/review-orchestrator.md",
  "platform-packs/kmp/code-review/bill-kmp-code-review-ux-accessibility/shell-ceremony.md",
  "platform-packs/kmp/code-review/bill-kmp-code-review-ux-accessibility/telemetry-contract.md",
  "platform-packs/kmp/code-review/bill-kmp-code-review/android-compose-adaptive-layouts.md",
  "platform-packs/kmp/code-review/bill-kmp-code-review/android-compose-edge-to-edge.md",
  "platform-packs/kmp/code-review/bill-kmp-code-review/android-compose-review.md",
  "platform-packs/kmp/code-review/bill-kmp-code-review/android-design-system-review.md",
  "platform-packs/kmp/code-review/bill-kmp-code-review/android-interop-review.md",
  "platform-packs/kmp/code-review/bill-kmp-code-review/android-navigation-review.md",
  "platform-packs/kmp/code-review/bill-kmp-code-review/android-r8-review.md",
  "platform-packs/kmp/code-review/bill-kmp-code-review/review-delegation.md",
  "platform-packs/kmp/code-review/bill-kmp-code-review/review-orchestrator.md",
  "platform-packs/kmp/code-review/bill-kmp-code-review/review-scope.md",
  "platform-packs/kmp/code-review/bill-kmp-code-review/shell-ceremony.md",
  "platform-packs/kmp/code-review/bill-kmp-code-review/specialist-contract.md",
  "platform-packs/kmp/code-review/bill-kmp-code-review/stack-routing.md",
  "platform-packs/kmp/code-review/bill-kmp-code-review/telemetry-contract.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-api-contracts/review-orchestrator.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-api-contracts/shell-ceremony.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-api-contracts/telemetry-contract.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-architecture/review-orchestrator.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-architecture/shell-ceremony.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-architecture/telemetry-contract.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-performance/review-orchestrator.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-performance/shell-ceremony.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-performance/telemetry-contract.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-persistence/review-orchestrator.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-persistence/shell-ceremony.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-persistence/telemetry-contract.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-platform-correctness/review-orchestrator.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-platform-correctness/shell-ceremony.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-platform-correctness/telemetry-contract.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-reliability/review-orchestrator.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-reliability/shell-ceremony.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-reliability/telemetry-contract.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-security/review-orchestrator.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-security/shell-ceremony.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-security/telemetry-contract.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-testing/review-orchestrator.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-testing/shell-ceremony.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review-testing/telemetry-contract.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review/review-delegation.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review/review-orchestrator.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review/review-scope.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review/shell-ceremony.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review/specialist-contract.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review/stack-routing.md",
  "platform-packs/kotlin/code-review/bill-kotlin-code-review/telemetry-contract.md",
  "platform-packs/kotlin/quality-check/bill-kotlin-quality-check/shell-ceremony.md",
  "platform-packs/kotlin/quality-check/bill-kotlin-quality-check/stack-routing.md",
  "platform-packs/kotlin/quality-check/bill-kotlin-quality-check/telemetry-contract.md",
)
