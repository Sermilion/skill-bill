package skillbill.infrastructure.workflow.review.specialists

import me.tatarka.inject.annotations.Inject
import skillbill.ports.review.launch.DeclaredReviewSpecialistsPort
import skillbill.ports.scaffold.install.InstalledPlatformPackCatalogPort
import skillbill.review.plan.ReviewLaneInclusionPolicy
import skillbill.review.plan.ReviewLaunchPlanPolicy
import skillbill.review.plan.ReviewStackRouting
import skillbill.review.plan.model.ReviewRoutingChangedFile
import skillbill.review.plan.model.ReviewStackRoutingFallback
import java.util.logging.Logger

@Inject
class FileSystemDeclaredReviewSpecialists(
  private val installedCatalog: InstalledPlatformPackCatalogPort,
) : DeclaredReviewSpecialistsPort {
  override fun routedSpecialists(changedFiles: List<ReviewRoutingChangedFile>): List<String> {
    if (changedFiles.isEmpty()) return emptyList()
    val manifests = installedCatalog.manifests()
    if (manifests.isEmpty()) return emptyList()
    val routing = ReviewStackRouting.route(manifests, changedFiles)
    routing.missingPackFallbacks.forEach(::recordMissingPackFallback)
    return routing.routedSlugs.flatMap { slug ->
      val owned = changedFiles.filter { it.path in routing.ownedPathsBySlug[slug].orEmpty() }
      val selectedAreas = ReviewLaunchPlanPolicy.composedAreas(slug, manifests)
      ReviewLaunchPlanPolicy.flatten(slug, manifests, selectedAreas).lanes
        .filter { lane ->
          owned.any { ReviewLaneInclusionPolicy.ownsChangedFile(lane, it.path, it.changedContent) }
        }
        .map { it.skillName }
    }.distinct()
  }

  private fun recordMissingPackFallback(fallback: ReviewStackRoutingFallback) {
    routingLog.warning(
      "code-review routing fell back because a required platform pack is absent: " +
        "seam=ReviewStackRouting.route pack=${fallback.routedSlug} used=${fallback.fallbackSlug} " +
        "expected=${fallback.missingPlatforms.joinToString(",")} " +
        "cause=the platform pack is not in the effective catalog as an external or bundled pack",
    )
  }

  private companion object {
    private val routingLog: Logger = Logger.getLogger("skillbill.review.plan.ReviewStackRouting")
  }
}
