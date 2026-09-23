package skillbill.application.review.parallel.planning

import skillbill.scaffold.model.PlatformManifest
import java.util.UUID

internal const val GENERIC_REVIEW_SKILL_NAME: String = "bill-generic-code-review"

internal const val REVIEW_SESSION_ID_PREFIX: String = "rvs-"

internal fun mintReviewSessionId(): String = REVIEW_SESSION_ID_PREFIX + UUID.randomUUID()

internal fun routedReviewSkillName(routedManifests: List<PlatformManifest>): String =
  routedManifests.firstNotNullOfOrNull(PlatformManifest::routedSkillName) ?: GENERIC_REVIEW_SKILL_NAME
