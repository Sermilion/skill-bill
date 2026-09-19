package skillbill.ports.review.empty
import skillbill.ports.review.preparation.ReviewAttributionPort
object EmptyReviewAttributionPort : ReviewAttributionPort {
  override fun routedSkillPlatformSlugs(): Map<String, String> = emptyMap()
}
