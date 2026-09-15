package skillbill.ports.review.model

import java.nio.file.Path

data class GovernedReviewEvidenceEndpointDescriptor(
  val lane: String,
  val socketPath: Path,
  val mcpConfigPath: Path,
  val token: String,
) {
  init {
    require(lane.isNotBlank()) { "A governed review evidence endpoint must name its lane." }
    require(token.isNotBlank()) { "A governed review evidence endpoint must carry a per-launch token." }
  }
}
