package skillbill.application.review.governed
import skillbill.ports.review.evidence.GovernedReviewEvidenceEndpointBinder
import skillbill.ports.review.evidence.GovernedReviewEvidenceEndpointHandle
import skillbill.ports.review.evidence.ReviewEvidenceBroker
import skillbill.ports.review.model.GovernedReviewEvidenceEndpointDescriptor
import java.nio.file.Path

fun stubGovernedReviewEvidenceEndpointBinder(root: Path): GovernedReviewEvidenceEndpointBinder =
  object : GovernedReviewEvidenceEndpointBinder {
    override fun bind(
      lane: String,
      broker: ReviewEvidenceBroker,
      onEvidenceRead: (() -> Unit)?,
    ): GovernedReviewEvidenceEndpointHandle = object : GovernedReviewEvidenceEndpointHandle {
      override val descriptor = GovernedReviewEvidenceEndpointDescriptor(
        lane = lane,
        socketPath = root.resolve("evidence.sock"),
        mcpConfigPath = root.resolve("mcp.json"),
        token = "stub-token",
      )

      override fun close() = Unit
    }
  }
