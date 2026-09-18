package skillbill.infrastructure.sqlite.review

import skillbill.review.context.model.ReviewAccountingSummary

fun ReviewAccountingSummary.toBoundedPayload(): Map<String, Any?> = encodeReviewAccountingBoundedPayload(this)
