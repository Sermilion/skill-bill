package skillbill.infrastructure.workflow.review.specialists.checkpoint
import skillbill.infrastructure.workflow.decomposition.root
import skillbill.infrastructure.workflow.filesystem.path
import skillbill.infrastructure.workflow.filesystem.root
import skillbill.infrastructure.workflow.git.goal.root
import skillbill.infrastructure.workflow.git.standard.path
import skillbill.infrastructure.workflow.git.suppression.path
import skillbill.infrastructure.workflow.git.workflow.path
import skillbill.infrastructure.workflow.git.workflow.root
import skillbill.infrastructure.workflow.review.broker.checkpointDigest
import skillbill.infrastructure.workflow.review.broker.path
import skillbill.infrastructure.workflow.review.broker.root
import skillbill.infrastructure.workflow.review.specialists.system.path
import skillbill.infrastructure.workflow.review.specialists.system.root
import skillbill.infrastructure.workflow.review.specialists.system.specialists
import skillbill.infrastructure.workflow.runtime.root
import skillbill.ports.review.model.ReviewCheckpointFileIdentity
import skillbill.review.context.model.execution.requireRepositoryRelativePath
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

internal fun checkpointFileIdentity(root: Path, path: String): ReviewCheckpointFileIdentity {
  requireRepositoryRelativePath(path)
  val realRoot = root.toRealPath()
  val candidate = realRoot.resolve(path).normalize()
  require(candidate.startsWith(realRoot)) { "Evidence path escapes the repository." }
  var current = realRoot
  for (segment in realRoot.relativize(candidate)) {
    current = current.resolve(segment)
    val attributes = try {
      Files.readAttributes(current, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
    } catch (_: NoSuchFileException) {
      return ReviewCheckpointFileIdentity.Absent
    }
    checkpointUnavailable(attributes, current == candidate)?.let { return it }
  }
  return checkpointDigest(root, path)?.let { ReviewCheckpointFileIdentity.Regular(it) }
    ?: ReviewCheckpointFileIdentity.Absent
}

private fun checkpointUnavailable(
  attributes: BasicFileAttributes,
  finalComponent: Boolean,
): ReviewCheckpointFileIdentity.Unavailable? = when {
  attributes.isSymbolicLink -> ReviewCheckpointFileIdentity.Unavailable.SYMBOLIC_LINK
  finalComponent && attributes.isDirectory -> ReviewCheckpointFileIdentity.Unavailable.DIRECTORY
  finalComponent && !attributes.isRegularFile -> ReviewCheckpointFileIdentity.Unavailable.OTHER
  !finalComponent && !attributes.isDirectory -> ReviewCheckpointFileIdentity.Unavailable.OTHER
  else -> null
}
