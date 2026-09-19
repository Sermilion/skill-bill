package skillbill.infrastructure.workflow.review.specialists.review
import skillbill.error.shellcontent.InvalidReviewContextSchemaError
import skillbill.infrastructure.host.process.BoundedExternalProcessRequest
import skillbill.infrastructure.host.process.BoundedExternalProcessRunner
import skillbill.infrastructure.workflow.decomposition.root
import skillbill.infrastructure.workflow.filesystem.head
import skillbill.infrastructure.workflow.filesystem.matches
import skillbill.infrastructure.workflow.filesystem.path
import skillbill.infrastructure.workflow.filesystem.root
import skillbill.infrastructure.workflow.git.checkpoint.head
import skillbill.infrastructure.workflow.git.goal.base
import skillbill.infrastructure.workflow.git.goal.exitCode
import skillbill.infrastructure.workflow.git.goal.head
import skillbill.infrastructure.workflow.git.goal.root
import skillbill.infrastructure.workflow.git.goal.value
import skillbill.infrastructure.workflow.git.repository.exitCode
import skillbill.infrastructure.workflow.git.standard.args
import skillbill.infrastructure.workflow.git.standard.base
import skillbill.infrastructure.workflow.git.standard.path
import skillbill.infrastructure.workflow.git.suppression.path
import skillbill.infrastructure.workflow.git.workflow.exitCode
import skillbill.infrastructure.workflow.git.workflow.head
import skillbill.infrastructure.workflow.git.workflow.path
import skillbill.infrastructure.workflow.git.workflow.root
import skillbill.infrastructure.workflow.git.workflow.value
import skillbill.infrastructure.workflow.process.GIT_TIMEOUT_SECONDS
import skillbill.infrastructure.workflow.process.runGitCommand
import skillbill.infrastructure.workflow.review.broker.base
import skillbill.infrastructure.workflow.review.broker.path
import skillbill.infrastructure.workflow.review.broker.root
import skillbill.infrastructure.workflow.review.broker.validateRepositoryMapping
import skillbill.infrastructure.workflow.review.specialists.coordinate.revision
import skillbill.infrastructure.workflow.review.specialists.system.base
import skillbill.infrastructure.workflow.review.specialists.system.path
import skillbill.infrastructure.workflow.review.specialists.system.root
import skillbill.infrastructure.workflow.review.specialists.system.specialists
import skillbill.infrastructure.workflow.runtime.root
import java.nio.file.Files
import java.nio.file.Path

internal fun readImmutableReviewFile(root: Path, revision: String, path: String, maxBytes: Long): ByteArray? {
  if (!immutableReviewFileExists(root, revision, path)) return null
  return readImmutableReviewCommand(root, listOf("cat-file", "blob", "$revision:$path"), maxBytes)
}

internal fun immutableReviewFileExists(root: Path, revision: String, path: String): Boolean {
  validateRepositoryMapping(root, path)
  val entry = runGitCommand(root, "--literal-pathspecs", "ls-tree", revision, "--", path)
  if (!entry.ok) throw InvalidReviewContextSchemaError("review-expansion", "Immutable revision is unavailable.")
  val row = entry.value.orEmpty()
  if (row.isBlank()) return false
  if (!row.startsWith("100644 blob ") && !row.startsWith("100755 blob ")) {
    throw InvalidReviewContextSchemaError("review-expansion", "Immutable evidence is not a regular file.")
  }
  return true
}

internal fun readImmutableReviewDelta(
  root: Path,
  base: String,
  head: String,
  path: String,
  maxBytes: Long,
): ByteArray? {
  if (!base.matches(Regex("[a-f0-9]{40,64}")) || !head.matches(Regex("[a-f0-9]{40,64}"))) return null
  validateRepositoryMapping(root, path)
  return readImmutableReviewCommand(
    root,
    listOf("diff", "--no-ext-diff", "--no-textconv", base, head, "--", ":(literal)$path"),
    maxBytes,
  )
}

internal fun readImmutableReviewCommand(root: Path, args: List<String>, maxBytes: Long): ByteArray {
  val output = Files.createTempFile("skill-bill-evidence", ".blob")
  try {
    val result = BoundedExternalProcessRunner.run(
      BoundedExternalProcessRequest(
        argv = listOf("git", "-C", root.toString()) + args,
        redirectOutputFile = output,
        deadlineSeconds = GIT_TIMEOUT_SECONDS,
        outputCapBytes = null,
      ),
    )
    if (result.timedOut) {
      throw InvalidReviewContextSchemaError("review-expansion", "Immutable evidence read timed out.")
    }
    if (result.exitCode != 0) {
      throw InvalidReviewContextSchemaError(
        "review-expansion",
        "Immutable evidence read failed.",
      )
    }
    return Files.newInputStream(output).use { it.readNBytes(minOf(maxBytes + 1, Int.MAX_VALUE.toLong()).toInt()) }
  } finally {
    Files.deleteIfExists(output)
  }
}
