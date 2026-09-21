package skillbill.infrastructure.workflow.git.workflow

import skillbill.codegraph.isCodeGraphGeneratedPath
import skillbill.infrastructure.host.jvm.requirePathContainedIn
import skillbill.infrastructure.workflow.process.runGitCommand
import skillbill.ports.workflow.gitops.model.ReadinessTreeIdentity
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import skillbill.ports.workflow.gitops.readiness.ReadinessTreeIdentityGitOperations
import skillbill.ports.workflow.gitops.readiness.ReadinessTreeIdentityPayloadCodec
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

internal object GitReadinessTreeIdentityOperations : ReadinessTreeIdentityGitOperations {
  override fun resolveReadinessTreeIdentity(
    repoRoot: Path,
    baseBranch: String,
    workflowId: String,
  ): WorkflowGitOperationResult = identityInputFailure(baseBranch, workflowId)
    ?: resolveIdentity(repoRoot, baseBranch, workflowId)

  private fun resolveIdentity(repoRoot: Path, baseBranch: String, workflowId: String): WorkflowGitOperationResult {
    val head = resolveRequiredSha(repoRoot, "HEAD", "Readiness identity could not resolve HEAD.")
    if (head !is WorkflowGitOperationResult.Ok) return head
    val baseRef = resolveRequiredSha(
      repoRoot,
      "origin/$baseBranch",
      "Readiness identity could not resolve origin/$baseBranch.",
    )
    if (baseRef !is WorkflowGitOperationResult.Ok) return baseRef
    val tree = computeSourceTreeSha(repoRoot, workflowId)
    if (tree !is WorkflowGitOperationResult.Ok) return tree
    return WorkflowGitOperationResult.Ok(
      value = ReadinessTreeIdentityPayloadCodec.encode(
        ReadinessTreeIdentity(
          sourceTreeSha = tree.value.orEmpty(),
          baseRefSha = baseRef.value.orEmpty(),
          headSha = head.value.orEmpty(),
        ),
      ),
    )
  }

  override fun changedPathsAgainstBase(repoRoot: Path, baseBranch: String): WorkflowGitOperationResult =
    if (baseBranch.isBlank()) {
      WorkflowGitOperationResult.Failed(
        error = "Readiness changed-path discovery requires a non-blank base branch.",
      )
    } else {
      discoverChangedPaths(repoRoot, baseBranch)
    }

  private fun discoverChangedPaths(repoRoot: Path, baseBranch: String): WorkflowGitOperationResult {
    val base = "origin/$baseBranch"
    val baseResolution = runGitCommand(repoRoot, "rev-parse", base)
    if (baseResolution !is WorkflowGitOperationResult.Ok) {
      return WorkflowGitOperationResult.Failed(
        error = "Readiness changed-path discovery could not resolve $base: ${baseResolution.error}",
      )
    }
    val tracked = runGitCommand(repoRoot, "diff", "--name-only", "-z", base, "--")
    if (tracked !is WorkflowGitOperationResult.Ok) return tracked
    val untracked = runGitCommand(repoRoot, "ls-files", "--others", "--exclude-standard", "-z")
    if (untracked !is WorkflowGitOperationResult.Ok) return untracked
    val trackedPaths = tracked.value.orEmpty().split('\u0000').filter(String::isNotBlank)
    val untrackedPaths = untracked.value.orEmpty().split('\u0000').filter(String::isNotBlank)
    return WorkflowGitOperationResult.Ok(
      value = (trackedPaths + untrackedPaths)
        .distinct()
        .joinToString("\u0000"),
    )
  }

  internal fun computeSourceTreeSha(repoRoot: Path, workflowId: String): WorkflowGitOperationResult {
    val indexPath = runGitCommand(repoRoot, "rev-parse", "--git-path", "index")
    if (indexPath !is WorkflowGitOperationResult.Ok) return indexPath
    val resolvedIndex = repoRoot.resolve(indexPath.value.orEmpty().trim()).normalize()
    val root = repoRoot.normalize()
    requirePathContainedIn(resolvedIndex, root) { "Git index path escapes repository root." }
    if (!Files.isRegularFile(resolvedIndex)) {
      return WorkflowGitOperationResult.Failed(error = "Git index is missing at '$resolvedIndex'.")
    }
    val tempDir = createTempDirectory("skillbill-readiness-index")
    return try {
      writeSourceTree(repoRoot, resolvedIndex, tempDir, workflowId)
    } finally {
      runCatching { Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    }
  }

  private fun writeSourceTree(
    repoRoot: Path,
    resolvedIndex: Path,
    tempDir: Path,
    workflowId: String,
  ): WorkflowGitOperationResult {
    val tempIndex = tempDir.resolve("index")
    Files.copy(resolvedIndex, tempIndex)
    val environment = mapOf("GIT_INDEX_FILE" to tempIndex.toString())
    val stagedWorktree = runGitCommand(
      repoRoot,
      environment,
      listOf(
        "add",
        "--all",
        "--",
        ".",
        ":!.codegraph",
        ":!.codegraph/**",
        ":!.skill-bill/runtime/codegraph-sessions",
        ":!.skill-bill/runtime/codegraph-sessions/**",
      ),
    )
    if (stagedWorktree !is WorkflowGitOperationResult.Ok) return stagedWorktree
    val listed = runGitCommand(repoRoot, environment, "ls-files", "-s", "-z")
    if (listed !is WorkflowGitOperationResult.Ok) return listed
    removeExcludedPaths(repoRoot, environment, listed.value.orEmpty(), workflowId).let { failure ->
      if (failure != null) return failure
    }
    return runGitCommand(repoRoot, environment, "write-tree")
  }

  private fun removeExcludedPaths(
    repoRoot: Path,
    environment: Map<String, String>,
    listed: String,
    workflowId: String,
  ): WorkflowGitOperationResult? {
    val runEvidencePrefix = ".skill-bill/run-evidence/$workflowId/"
    var failure: WorkflowGitOperationResult? = null
    listed.split('\u0000').filter(String::isNotBlank).forEach { entry ->
      val path = entry.substringAfter('\t', missingDelimiterValue = "").trim()
      if (failure == null && path.isNotBlank() && isExcludedFromSourceTree(path, runEvidencePrefix)) {
        val removed = runGitCommand(
          repoRoot,
          environment,
          "update-index",
          "--force-remove",
          path,
        )
        if (removed !is WorkflowGitOperationResult.Ok) failure = removed
      }
    }
    return failure
  }

  private fun identityInputFailure(baseBranch: String, workflowId: String): WorkflowGitOperationResult? = when {
    baseBranch.isBlank() ->
      WorkflowGitOperationResult.Failed(error = "Readiness identity requires a non-blank base branch.")
    workflowId.isBlank() ->
      WorkflowGitOperationResult.Failed(error = "Readiness identity requires a non-blank workflow id.")
    else -> null
  }

  private fun resolveRequiredSha(repoRoot: Path, reference: String, blankError: String): WorkflowGitOperationResult {
    val result = runGitCommand(repoRoot, "rev-parse", reference)
    if (result !is WorkflowGitOperationResult.Ok) {
      return WorkflowGitOperationResult.Failed(
        error = "$blankError ${result.error.orEmpty()}".trim(),
      )
    }
    val value = result.value.orEmpty().trim()
    return if (value.isBlank()) {
      WorkflowGitOperationResult.Failed(error = blankError)
    } else {
      WorkflowGitOperationResult.Ok(value = value)
    }
  }

  internal fun isExcludedFromSourceTree(path: String, runEvidencePrefix: String): Boolean =
    isCodeGraphGeneratedPath(path) || path.endsWith("agent/history.md") || path.startsWith(runEvidencePrefix)
}
