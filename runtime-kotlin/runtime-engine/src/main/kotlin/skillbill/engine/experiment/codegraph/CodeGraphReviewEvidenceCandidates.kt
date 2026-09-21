package skillbill.engine.experiment.codegraph

import skillbill.ports.experiment.codegraph.model.CodeGraphCandidateHit
import skillbill.ports.review.evidence.ReviewEvidenceBroker
import skillbill.ports.review.model.ReviewEvidenceBatchRequest
import skillbill.ports.review.model.ReviewEvidenceRequest
import skillbill.ports.review.model.ReviewEvidenceResult
import skillbill.ports.review.model.ReviewExpansionAuthorizationRequest

object CodeGraphReviewEvidenceCandidates {
  fun authorizedExcerpts(
    broker: ReviewEvidenceBroker,
    lane: String,
    hits: List<CodeGraphCandidateHit>,
    assignedPaths: Set<String> = emptySet(),
  ): List<Pair<CodeGraphCandidateHit, ReviewEvidenceResult>> {
    val requests = hits.mapNotNull { hit ->
      val path = hit.file.trim()
      if (path.isBlank()) return@mapNotNull null
      if (path in assignedPaths) {
        return@mapNotNull hit to ReviewEvidenceRequest(
          lane = lane,
          path = path,
          reachabilityReason = "codegraph candidate",
        )
      }
      val record = runCatching {
        broker.authorizeExpansion(
          ReviewExpansionAuthorizationRequest(
            lane = lane,
            path = path,
            reachabilityReason = "codegraph candidate",
          ),
        )
      }.getOrNull()
        ?: return@mapNotNull null
      if (!record.authorized) return@mapNotNull null
      hit to ReviewEvidenceRequest(
        lane = lane,
        path = path,
        reachabilityReason = "codegraph candidate",
        authorizedExpansion = record,
      )
    }
    if (requests.isEmpty()) return emptyList()
    val batch = broker.readBatch(
      ReviewEvidenceBatchRequest(lane, requests.map { it.second }),
    )
    return requests.zip(batch.results)
      .filter { (_, result) -> result.forbidden == null && result.budgetExceeded == null }
      .map { (request, result) -> request.first to result }
  }
}
