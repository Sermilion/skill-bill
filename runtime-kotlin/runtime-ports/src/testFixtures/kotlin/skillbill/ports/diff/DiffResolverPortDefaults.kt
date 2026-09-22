package skillbill.ports.diff

import skillbill.error.shellcontent.InvalidReviewContextSchemaError
import skillbill.ports.review.model.ReviewCheckpointFileIdentity
import java.nio.file.Path

abstract class DiffResolverPortDefaults : DiffResolverPort {
  open override fun reviewWorktreeFileIdentities(
    root: Path,
    paths: List<String>,
  ): Map<String, ReviewCheckpointFileIdentity> =
    throw InvalidReviewContextSchemaError(
      "review-source",
      "This diff resolver cannot capture worktree evidence identities.",
    )

  open override fun readDiff(
    path: Path,
    maxBytes: Long,
  ): String? = null
}
