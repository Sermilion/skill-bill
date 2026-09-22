package skillbill.ports.diff

import skillbill.ports.review.model.ReviewCheckpointFileIdentity
import java.nio.file.Path

interface DiffResolverPort {
  fun runProcess(
    args: List<String>,
    workDir: Path,
  ): String?

  fun reviewWorktreeFileIdentities(
    root: Path,
    paths: List<String>,
  ): Map<String, ReviewCheckpointFileIdentity>

  fun readDiff(
    path: Path,
    maxBytes: Long,
  ): String?
}
