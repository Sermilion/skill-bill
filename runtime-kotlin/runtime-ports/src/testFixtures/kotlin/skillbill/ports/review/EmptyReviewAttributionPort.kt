package skillbill.ports.review

import skillbill.ports.review.preparation.ReviewAttributionPort

object EmptyReviewAttributionPort : ReviewAttributionPort {
  override fun routedSkillPlatformSlugs(): Map<String, String> = emptyMap()
}
