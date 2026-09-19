package skillbill.infrastructure.sqlite.review.stats.platform
import skillbill.infrastructure.sqlite.review.accounting.List
import skillbill.infrastructure.sqlite.review.accounting.Map
import skillbill.infrastructure.sqlite.review.core.Map
import skillbill.infrastructure.sqlite.review.review.review
import skillbill.infrastructure.sqlite.review.stage.Map
import skillbill.infrastructure.sqlite.review.stage.and.review
import skillbill.infrastructure.sqlite.review.stage.finished.normalizedStack
import skillbill.infrastructure.sqlite.review.stage.finished.review
import skillbill.infrastructure.sqlite.review.stage.finished.stats
import skillbill.infrastructure.sqlite.review.stage.review
import skillbill.infrastructure.sqlite.review.stage.runtime.review
import skillbill.infrastructure.sqlite.review.stats.Map
import skillbill.infrastructure.sqlite.review.stats.health.Map
import skillbill.infrastructure.sqlite.review.stats.recorded.review
import skillbill.infrastructure.sqlite.review.stats.recorded.stats
import skillbill.infrastructure.sqlite.review.stats.review
import skillbill.infrastructure.sqlite.review.stats.routedSkillPlatformSlugs
import skillbill.infrastructure.sqlite.review.stats.stats
import skillbill.infrastructure.sqlite.review.stats.task.stats
import skillbill.review.attribution.normalizePlatformSlug
import skillbill.review.attribution.normalizeTelemetrySlug

internal fun platformSlugFromRoutedSkill(
  routedSkill: String?,
  routedSkillPlatformSlugs: Map<String, String> = emptyMap(),
): String {
  val normalized = routedSkill?.let(::normalizeTelemetrySlug).orEmpty()
  if (normalized == "unknown") {
    return "unknown"
  }
  return normalizedRoutedSkillPlatformSlugs(routedSkillPlatformSlugs)
    .firstNotNullOfOrNull { (skillName, platformSlug) ->
      platformSlug.takeIf { normalized == skillName }
    }
    ?: "unknown"
}

internal fun reviewPlatformSlug(
  detectedStack: String?,
  routedSkill: String?,
  routedSkillPlatformSlugs: Map<String, String> = emptyMap(),
): String {
  val normalizedDetectedStack = normalizePlatformSlug(detectedStack)
  val routedPlatformSlug = platformSlugFromRoutedSkill(routedSkill, routedSkillPlatformSlugs)
  return when {
    routedPlatformSlug != "unknown" && detectedStack.isDescriptiveStackLabel(normalizedDetectedStack) ->
      routedPlatformSlug
    normalizedDetectedStack != "unknown" -> normalizedDetectedStack
    else -> routedPlatformSlug
  }
}

private fun normalizedRoutedSkillPlatformSlugs(
  routedSkillPlatformSlugs: Map<String, String>,
): List<Pair<String, String>> = routedSkillPlatformSlugs
  .mapNotNull { (skillName, platformSlug) ->
    val normalizedSkillName = normalizeTelemetrySlug(skillName)
    val normalizedPlatformSlug = normalizePlatformSlug(platformSlug)
    if (normalizedSkillName == "unknown" || normalizedPlatformSlug == "unknown") {
      null
    } else {
      normalizedSkillName to normalizedPlatformSlug
    }
  }
  .sortedWith(compareByDescending<Pair<String, String>> { it.first.length }.thenBy { it.first })

private fun String?.isDescriptiveStackLabel(normalizedStack: String): Boolean {
  if (normalizedStack == "unknown") return false
  val normalizedRaw = this?.let(::normalizeTelemetrySlug).orEmpty()
  return normalizedRaw != normalizedStack
}
