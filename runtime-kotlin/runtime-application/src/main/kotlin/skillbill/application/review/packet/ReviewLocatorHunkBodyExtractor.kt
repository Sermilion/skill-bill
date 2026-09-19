package skillbill.application.review.packet
import skillbill.application.review.parallel.core.code.review.bundled.review
import skillbill.application.review.parallel.core.code.review.runner.hunk
import skillbill.application.review.parallel.core.code.review.runner.packet
import skillbill.application.review.parallel.core.review.hunk
import skillbill.application.review.parallel.core.review.packet
import skillbill.application.review.preparation.hunk
import skillbill.application.review.preparation.packet
import skillbill.application.review.preparation.payload
import skillbill.application.review.preparation.storePath
import skillbill.application.review.service.review
import skillbill.application.review.spec.hunk
import skillbill.application.review.spec.packet
import skillbill.application.review.spec.payload
import skillbill.application.review.stats.payload
import skillbill.application.review.verification.hunk
import skillbill.application.review.verification.packet
import skillbill.application.review.verification.payload
import skillbill.ports.review.evidence.ReviewStoredHunkBodyExtractor
import skillbill.review.context.model.hunk.ReviewChangedHunk

object ReviewLocatorHunkBodyExtractor : ReviewStoredHunkBodyExtractor {
  override fun extract(payload: String, hunk: ReviewChangedHunk): String =
    ReviewHunkStoreIndexing.extractStoredBody(hunk, payload, hunk.evidenceLocator.storePath)
}
