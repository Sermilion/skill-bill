package skillbill.ports.review.launch
import skillbill.ports.review.model.ReviewLaunchIsolationStrategy

fun interface ReviewLaunchIsolationResolver {
  fun isolationFor(agentId: String): ReviewLaunchIsolationStrategy
}
