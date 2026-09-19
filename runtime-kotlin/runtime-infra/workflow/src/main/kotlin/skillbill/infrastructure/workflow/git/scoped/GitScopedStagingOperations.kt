package skillbill.infrastructure.workflow.git.scoped
import skillbill.infrastructure.workflow.process.GitProcessResult
import skillbill.infrastructure.workflow.process.gitTimedOutError
import skillbill.infrastructure.workflow.process.runGitCommand
import skillbill.infrastructure.workflow.process.runGitCommandWithStdin
import skillbill.infrastructure.workflow.process.runGitProcess
import skillbill.ports.workflow.gitops.ScopedStagingGitOperations
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import java.nio.file.Files
import java.nio.file.Path

internal const val GIT_NUL: Char = '\u0000'

private const val INDEX_REMOVAL_MODE = "0"
private const val INDEX_REMOVAL_OBJECT = "0000000000000000000000000000000000000000"

private const val PATHSPEC_BATCH_SIZE = 200

internal object GitScopedStagingOperations : ScopedStagingGitOperations {
  override fun stagePaths(repoRoot: Path, paths: List<String>): WorkflowGitOperationResult {
    val normalized = paths.filter(String::isNotBlank).distinct()
    if (normalized.isEmpty()) return WorkflowGitOperationResult.Ok(value = "")
    val resolved = resolveStageablePaths(repoRoot, normalized)
    if (resolved !is WorkflowGitOperationResult.Ok) return resolved
    val stageable = resolved.value.orEmpty().split(GIT_NUL).filter(String::isNotBlank)
    stageable.chunked(PATHSPEC_BATCH_SIZE).forEach { batch ->

      val staged = runGitCommand(repoRoot, listOf("add", "--all", "--") + batch)
      if (staged !is WorkflowGitOperationResult.Ok) return staged
    }
    return WorkflowGitOperationResult.Ok(value = "")
  }

  override fun captureIndexState(repoRoot: Path, paths: List<String>): WorkflowGitOperationResult {
    val normalized = paths.filter(String::isNotBlank).distinct()
    if (normalized.isEmpty()) return WorkflowGitOperationResult.Ok(value = "")
    val entries = mutableListOf<String>()
    normalized.chunked(PATHSPEC_BATCH_SIZE).forEach { batch ->
      val listed = runGitCommand(repoRoot, listOf("ls-files", "--stage", "-z", "--") + batch)
      if (listed !is WorkflowGitOperationResult.Ok) return listed
      entries += listed.value.orEmpty().split(GIT_NUL).filter(String::isNotBlank)
    }
    return WorkflowGitOperationResult.Ok(value = entries.joinToString(GIT_NUL.toString()))
  }

  override fun restoreIndexState(repoRoot: Path, paths: List<String>, snapshot: String): WorkflowGitOperationResult {
    val normalized = paths.filter(String::isNotBlank).distinct()
    if (normalized.isEmpty()) return WorkflowGitOperationResult.Ok(value = "")
    val entries = snapshot.split(GIT_NUL).filter(String::isNotBlank)
    val snapshotPaths = entries.mapNotNull(::indexEntryPath).toSet()

    val removals = normalized.filterNot { it in snapshotPaths }
      .map { "$INDEX_REMOVAL_MODE $INDEX_REMOVAL_OBJECT\t$it" }
    val records = entries + removals
    if (records.isEmpty()) return WorkflowGitOperationResult.Ok(value = "")
    val stdin = records.joinToString(GIT_NUL.toString(), postfix = GIT_NUL.toString()).toByteArray()
    return runGitCommandWithStdin(repoRoot, listOf("update-index", "-z", "--index-info"), stdin)
  }

  override fun stagedPaths(repoRoot: Path): WorkflowGitOperationResult =
    runGitCommand(repoRoot, "diff", "--cached", "--name-only", "-z")

  override fun pathContentIdentities(repoRoot: Path, paths: List<String>): WorkflowGitOperationResult {
    val present = paths.filter(String::isNotBlank).distinct().sorted()
      .filter { Files.isRegularFile(repoRoot.resolve(it)) }
    if (present.isEmpty()) return WorkflowGitOperationResult.Ok(value = "")
    val records = mutableListOf<String>()
    present.chunked(PATHSPEC_BATCH_SIZE).forEach { batch ->
      val hashed = runGitCommand(repoRoot, listOf("hash-object", "--") + batch)
      if (hashed !is WorkflowGitOperationResult.Ok) return hashed
      val hashes = hashed.value.orEmpty().lineSequence().map(String::trim).filter(String::isNotBlank).toList()

      if (hashes.size != batch.size) {
        return WorkflowGitOperationResult.Failed(
          error = "git hash-object returned ${hashes.size} identities for ${batch.size} paths.",
        )
      }
      records += batch.indices.map { index -> "${hashes[index]}\t${batch[index]}" }
    }
    return WorkflowGitOperationResult.Ok(value = records.joinToString(GIT_NUL.toString()))
  }

  private fun indexEntryPath(entry: String): String? =
    entry.substringAfter('\t', missingDelimiterValue = "").takeIf(String::isNotBlank)

  private fun resolveStageablePaths(repoRoot: Path, normalized: List<String>): WorkflowGitOperationResult {
    val indexed = mutableSetOf<String>()
    for (batch in normalized.chunked(PATHSPEC_BATCH_SIZE)) {
      val listed = runGitCommand(repoRoot, listOf("ls-files", "--stage", "-z", "--") + batch)
      if (listed !is WorkflowGitOperationResult.Ok) return listed
      listed.value.orEmpty().split(GIT_NUL).filter(String::isNotBlank).forEach { entry ->
        indexEntryPath(entry)?.let { indexed += it }
      }
    }
    val materialized = materializePathspecs(repoRoot, normalized)
    val presentOrIndexed = materialized.filter { Files.isRegularFile(repoRoot.resolve(it)) || it in indexed }
    val ignored = ignoredUntrackedPaths(repoRoot, presentOrIndexed)
    if (ignored !is WorkflowGitOperationResult.Ok) return ignored
    val ignoredSet = ignored.value.orEmpty().split(GIT_NUL).filter(String::isNotBlank).toSet()
    val stageable = presentOrIndexed.filterNot { it in ignoredSet }
    return WorkflowGitOperationResult.Ok(value = stageable.joinToString(GIT_NUL.toString()))
  }

  private fun materializePathspecs(repoRoot: Path, paths: List<String>): List<String> {
    val materialized = LinkedHashSet<String>()
    for (raw in paths) {
      val relative = raw.trim().removeSuffix("/")
      if (relative.isEmpty()) continue
      val resolved = repoRoot.resolve(relative)
      if (Files.isDirectory(resolved)) {
        Files.walk(resolved).use { walk ->
          walk.filter { Files.isRegularFile(it) }.forEach { file ->
            materialized += repoRoot.relativize(file).toString().replace('\\', '/')
          }
        }
      } else {
        materialized += relative
      }
    }
    return materialized.toList()
  }

  private fun ignoredUntrackedPaths(repoRoot: Path, paths: List<String>): WorkflowGitOperationResult {
    if (paths.isEmpty()) return WorkflowGitOperationResult.Ok(value = "")
    val ignored = mutableListOf<String>()
    for (batch in paths.chunked(PATHSPEC_BATCH_SIZE)) {
      val stdin = batch.joinToString(separator = GIT_NUL.toString(), postfix = GIT_NUL.toString()).toByteArray()
      val parsed = parseCheckIgnore(runGitProcess(repoRoot, listOf("check-ignore", "-z", "--stdin"), stdin))
      if (parsed !is WorkflowGitOperationResult.Ok) return parsed
      ignored += parsed.value.orEmpty().split(GIT_NUL).filter(String::isNotBlank)
    }
    return WorkflowGitOperationResult.Ok(value = ignored.joinToString(GIT_NUL.toString()))
  }

  private fun parseCheckIgnore(result: GitProcessResult): WorkflowGitOperationResult = when {
    result.timedOut -> WorkflowGitOperationResult.Failed(
      error = gitTimedOutError(listOf("check-ignore", "-z", "--stdin")),
    )
    result.readFailure != null -> WorkflowGitOperationResult.Failed(
      error = result.readFailure.message.orEmpty(),
    )
    result.exitCode == 0 -> WorkflowGitOperationResult.Ok(value = result.output)
    result.exitCode == 1 -> WorkflowGitOperationResult.Ok(value = "")
    else -> WorkflowGitOperationResult.Failed(
      error = "git check-ignore failed with exit code ${result.exitCode}: ${result.output}",
    )
  }
}
