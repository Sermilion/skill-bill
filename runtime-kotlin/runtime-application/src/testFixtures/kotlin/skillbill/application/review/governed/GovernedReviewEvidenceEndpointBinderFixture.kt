package skillbill.application.review.governed
import skillbill.application.review.packet.root
import skillbill.application.review.parallel.core.code.review.bundled.governed
import skillbill.application.review.parallel.core.code.review.bundled.review
import skillbill.application.review.parallel.core.code.review.evidence.socketPath
import skillbill.application.review.parallel.core.code.review.runner.governed
import skillbill.application.review.parallel.planning.lane
import skillbill.application.review.parallel.verification.lane
import skillbill.application.review.review.governed
import skillbill.application.review.review.lane
import skillbill.application.review.review.resolve
import skillbill.application.review.service.review
import skillbill.application.review.spec.lane
import skillbill.application.review.spec.resolve
import skillbill.application.review.stats.lane
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
