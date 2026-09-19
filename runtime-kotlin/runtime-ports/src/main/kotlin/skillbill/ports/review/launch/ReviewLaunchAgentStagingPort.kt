package skillbill.ports.review.launch
import skillbill.ports.review.model.ReviewLaunchAgentStagingRequest

fun interface ReviewLaunchAgentStagingPort {
  fun stage(request: ReviewLaunchAgentStagingRequest)

  companion object {
    val NONE = ReviewLaunchAgentStagingPort { }
  }
}
