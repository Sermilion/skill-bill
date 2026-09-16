package skillbill.review.model

import skillbill.error.InvalidReviewContextSchemaError
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ReviewStageStateDurableDecodeTest {
  @Test
  fun `unknown review stage wire token fails with review typed error`() {
    assertFailsWith<InvalidReviewContextSchemaError> {
      ReviewStage.fromWire("not-a-stage")
    }
  }

  @Test
  fun `unknown durable review tokens fail with review typed errors`() {
    assertFailsWith<InvalidReviewContextSchemaError> { ReviewClaimVerdict.fromWire("not-a-verdict") }
    assertFailsWith<InvalidReviewContextSchemaError> { ReviewScopeDisposition.fromWire("not-a-disposition") }
    assertFailsWith<InvalidReviewContextSchemaError> {
      ReviewSeverityAdjustmentDirection.fromWire("not-a-direction")
    }
    assertFailsWith<InvalidReviewContextSchemaError> { ReviewStageReached.fromWire("not-a-reached-state") }
  }

  @Test
  fun `citation decodeList maps non-numeric line to review typed error`() {
    assertFailsWith<InvalidReviewContextSchemaError> {
      ReviewFindingCitation.decodeList("src/A.kt\tnot-a-line")
    }
  }
}
