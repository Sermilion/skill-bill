package skillbill.ports.review.model

import java.nio.file.Path

data class ReviewSnapshot(
  val path: Path,
  val label: String,
  val sizeBytes: Long,
  val lastModified: String,
)
