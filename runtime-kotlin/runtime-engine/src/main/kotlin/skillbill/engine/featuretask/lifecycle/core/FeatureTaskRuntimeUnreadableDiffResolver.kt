package skillbill.engine.featuretask.lifecycle.core
import skillbill.error.shellcontent.InvalidReviewContextSchemaError
import skillbill.ports.diff.DiffResolverPort
import skillbill.ports.review.model.ReviewCheckpointFileIdentity
import java.nio.file.Path
object FeatureTaskRuntimeUnreadableDiffResolver : DiffResolverPort {
  override fun runProcess(args: List<String>, workDir: Path): String? = null

  override fun reviewWorktreeFileIdentities(
    root: Path,
    paths: List<String>,
  ): Map<String, ReviewCheckpointFileIdentity> = throw InvalidReviewContextSchemaError(
    "review-source",
    "This diff resolver cannot capture worktree evidence identities.",
  )

  override fun readDiff(path: Path, maxBytes: Long): String? = null
}
