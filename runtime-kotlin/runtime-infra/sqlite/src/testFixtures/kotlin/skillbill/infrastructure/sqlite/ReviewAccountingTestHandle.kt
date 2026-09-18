package skillbill.infrastructure.sqlite

import skillbill.infrastructure.sqlite.review.loadReviewAccounting
import skillbill.infrastructure.sqlite.review.upsertReviewAccounting
import skillbill.ports.review.model.ReviewAccountingRecord
import java.sql.Connection

class ReviewAccountingTestHandle internal constructor(
  private val connection: Connection,
) {
  fun load(reviewId: String) = loadReviewAccounting(connection, reviewId)

  fun upsert(record: ReviewAccountingRecord) {
    upsertReviewAccounting(connection, record)
  }
}

fun reviewAccountingOnConnection(connection: Connection): ReviewAccountingTestHandle =
  ReviewAccountingTestHandle(connection)
