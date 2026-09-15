package skillbill.ports.review

import skillbill.ports.review.model.GovernedReviewEvidenceEndpointDescriptor
import java.nio.file.Path

fun stubGovernedReviewEvidenceEndpointBinder(root: Path): GovernedReviewEvidenceEndpointBinder =
  object : GovernedReviewEvidenceEndpointBinder {
    override fun bind(
      lane: String,
      protocol: NativeReviewOperationProtocol,
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
