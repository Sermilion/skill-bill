package skillbill.infrastructure.sqlite.review.stats.health
import skillbill.contracts.JsonCodec
import skillbill.infrastructure.sqlite.review.review.review
import skillbill.infrastructure.sqlite.review.stage.and.review
import skillbill.infrastructure.sqlite.review.stage.finished.review
import skillbill.infrastructure.sqlite.review.stage.finished.stats
import skillbill.infrastructure.sqlite.review.stage.review
import skillbill.infrastructure.sqlite.review.stage.runtime.findings
import skillbill.infrastructure.sqlite.review.stage.runtime.review
import skillbill.infrastructure.sqlite.review.stats.connection
import skillbill.infrastructure.sqlite.review.stats.health
import skillbill.infrastructure.sqlite.review.stats.recorded.review
import skillbill.infrastructure.sqlite.review.stats.recorded.stats
import skillbill.infrastructure.sqlite.review.stats.review
import skillbill.infrastructure.sqlite.review.stats.reviewRunId
import skillbill.infrastructure.sqlite.review.stats.stats
import skillbill.infrastructure.sqlite.review.stats.task.stats
import skillbill.infrastructure.sqlite.telemetry.outbox.TelemetryOutboxStore
import skillbill.tempDbConnection
import java.sql.Connection
import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewHealthDeliveryGrainTest {
  @Test
  fun `a retried delivery and a re-emitted review each stay at their own grain`() {
    val (_, connection) = tempDbConnection("review-health-delivery-grain")
    connection.use {
      val store = TelemetryOutboxStore(connection)
      val retried = store.enqueue("skillbill_review_finished", reviewPayload("rvw-1", findings = 2))
      connection.setDeliveryAttempts(retried, attempts = 3)
      connection.setDeliveryAttempts(
        store.enqueue("skillbill_review_finished", reviewPayload("rvw-2", findings = 1)),
        attempts = 1,
      )
      connection.setDeliveryAttempts(
        store.enqueue("skillbill_review_finished", reviewPayload("rvw-2", findings = 1)),
        attempts = 1,
      )

      val grain = buildReviewHealthStats(connection, reviewRunId = null).reviewDeliveryGrain

      assertEquals(3, grain.queuedDeliveryRows)
      assertEquals(
        3,
        grain.logicalEvents,
        "three enqueues are three events; a retry of one of them is not a fourth",
      )
      assertEquals(
        5,
        grain.deliveryAttempts,
        "a health denominator built on delivery attempts counts the retried event three times",
      )
      assertEquals(
        2,
        grain.logicalReviews,
        "one review emitted twice after a correction is one review, not two",
      )
      assertEquals(0, grain.rowsWithUnknownDeliveryIdentity)
      assertEquals(0, grain.recordsWithUnknownReview)
    }
  }

  @Test
  fun `a row that predates the delivery identity column is reported unattributable, not as its own event`() {
    val (_, connection) = tempDbConnection("review-health-unknown-identity")
    connection.use {
      val store = TelemetryOutboxStore(connection)
      store.enqueue("skillbill_review_finished", reviewPayload("rvw-1", findings = 1))
      val legacy = store.enqueue("skillbill_review_finished", reviewPayload(reviewRunId = "", findings = 1))
      connection.clearDeliveryIdentity(legacy)

      val grain = buildReviewHealthStats(connection, reviewRunId = null).reviewDeliveryGrain

      assertEquals(2, grain.queuedDeliveryRows)
      assertEquals(1, grain.logicalEvents, "a row with no minted identity cannot be counted as a distinct event")
      assertEquals(1, grain.rowsWithUnknownDeliveryIdentity)
      assertEquals(1, grain.logicalReviews)
      assertEquals(
        1,
        grain.recordsWithUnknownReview,
        "a payload carrying no review_run_id is unattributable, never silently its own review",
      )
    }
  }

  private fun reviewPayload(reviewRunId: String, findings: Int): String = JsonCodec.mapToJsonString(
    buildMap {
      if (reviewRunId.isNotBlank()) put("review_run_id", reviewRunId)
      put("platform_slug", "kotlin")
      put("scope_type", "branch_diff")
      put("total_findings", findings)
      put("accepted_findings", findings)
      put("rejected_findings", 0)
      put("unresolved_findings", 0)
    },
  )

  private fun Connection.setDeliveryAttempts(outboxId: Long, attempts: Int) = createStatement().use { statement ->
    statement.executeUpdate("UPDATE telemetry_outbox SET delivery_attempts = $attempts WHERE id = $outboxId")
  }

  private fun Connection.clearDeliveryIdentity(outboxId: Long) = createStatement().use { statement ->
    statement.executeUpdate("UPDATE telemetry_outbox SET event_uuid = NULL WHERE id = $outboxId")
  }
}
