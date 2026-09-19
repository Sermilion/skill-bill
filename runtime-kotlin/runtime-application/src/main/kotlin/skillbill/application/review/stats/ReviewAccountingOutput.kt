package skillbill.application.review.stats
import skillbill.review.context.model.accounting.ReviewAccountingSummary
import skillbill.review.context.model.accounting.toBoundedPayload

fun ReviewAccountingSummary.toReviewAccountingPayload(): Map<String, Any?> = toBoundedPayload()
