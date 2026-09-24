package skillbill.ports.review.evidence

import skillbill.ports.review.model.GovernedReviewEvidenceEndpointDescriptor

/** A bound endpoint. Closing it unbinds the listener and removes the per-launch directory. */
interface GovernedReviewEvidenceEndpointHandle : AutoCloseable {
  val descriptor: GovernedReviewEvidenceEndpointDescriptor

  fun unbindListener() = Unit
}

/**
 * Binds a per-launch endpoint over an already-constructed operation protocol. Implementations
 * loud-fail rather than degrading to an ungoverned path.
 */
interface GovernedReviewEvidenceEndpointBinder {
  fun bind(
    lane: String,
    broker: ReviewEvidenceBroker,
    onEvidenceRead: (() -> Unit)? = null,
  ): GovernedReviewEvidenceEndpointHandle
}
