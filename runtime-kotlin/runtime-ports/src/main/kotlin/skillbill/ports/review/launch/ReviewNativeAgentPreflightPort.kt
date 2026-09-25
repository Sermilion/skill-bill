package skillbill.ports.review.launch

import skillbill.ports.review.model.ReviewNativeAgentPreflightRequest

fun interface ReviewNativeAgentPreflightPort {
  fun verify(request: ReviewNativeAgentPreflightRequest)
}
