package skillbill.application.review

import skillbill.review.context.model.ReviewAccountingSummary
import skillbill.review.context.model.toBoundedPayload

fun ReviewAccountingSummary.toReviewAccountingPayload(): Map<String, Any?> = toBoundedPayload()
