package skillbill.engine.experiment.codegraph

import skillbill.ports.experiment.codegraph.model.CodeGraphCandidateHit
import skillbill.ports.review.evidence.ReviewEvidenceBrokerDefaults
import skillbill.ports.review.model.ReviewEvidenceBatchRequest
import skillbill.ports.review.model.ReviewEvidenceBatchResult
import skillbill.ports.review.model.ReviewEvidenceResult
import skillbill.ports.review.model.ReviewExpansionAuthorizationRequest
import skillbill.review.context.model.packet.ReviewExpansionRecord
import kotlin.test.Test
import kotlin.test.assertEquals

class CodeGraphReviewEvidenceCandidatesTest {
  @Test
  fun `unauthorized graph path is dropped before review reads`() {
    val broker = object : ReviewEvidenceBrokerDefaults() {
      override fun authorizeExpansion(request: ReviewExpansionAuthorizationRequest): ReviewExpansionRecord =
        ReviewExpansionRecord(
          expansionId = "exp-1",
          assignmentDigest = "a".repeat(64),
          requestedPath = request.path,
          reachabilityReason = request.reachabilityReason,
          authorized = request.path == "allowed/Path.kt",
          sequence = 0,
        )

      override fun readBatch(request: ReviewEvidenceBatchRequest): ReviewEvidenceBatchResult =
        ReviewEvidenceBatchResult(
          results = request.requests.map {
            ReviewEvidenceResult(content = "evidence", bytes = 8, cumulativeBytes = 8, expansionCount = 1)
          },
          cumulativeBytes = request.requests.size * 8L,
          expansions = emptyList(),
        )
    }
    val hits = listOf(
      CodeGraphCandidateHit(name = "a", file = "allowed/Path.kt", kind = "type"),
      CodeGraphCandidateHit(name = "b", file = "denied/Path.kt", kind = "type"),
    )
    val authorized = CodeGraphReviewEvidenceCandidates.authorizedExcerpts(broker, "lane-1", hits)
    assertEquals(listOf("allowed/Path.kt"), authorized.map { (hit, _) -> hit.file })
  }
}
