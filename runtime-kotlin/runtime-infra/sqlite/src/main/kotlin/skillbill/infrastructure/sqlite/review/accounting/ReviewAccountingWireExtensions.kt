package skillbill.infrastructure.sqlite.review.accounting
import skillbill.review.context.model.accounting.ReviewAccountingSummary

fun ReviewAccountingSummary.toBoundedPayload(): Map<String, Any?> = encodeReviewAccountingBoundedPayload(this)
