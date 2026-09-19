package skillbill.engine.featuretask.validation

import me.tatarka.inject.annotations.Inject
import skillbill.engine.featuretask.validation.model.ValidationGateResolution
import skillbill.error.shellcontent.ShellContentContractException
import skillbill.ports.scaffold.install.InstalledPlatformPackCatalogPort
import skillbill.review.plan.ReviewStackRouting
import skillbill.review.plan.model.ReviewRoutingChangedFile
import skillbill.review.plan.model.ReviewStackRoutingResult
import skillbill.scaffold.model.PlatformManifest
@Inject
class ValidationGateResolver(
  private val installedCatalog: InstalledPlatformPackCatalogPort,
) {
  fun resolve(changedPaths: List<String>): ValidationGateResolution {
    val manifests = try {
      installedCatalog.manifests()
    } catch (e: ShellContentContractException) {
      return ValidationGateResolution.Incompatible(
        "Installed platform pack discovery failed: ${e.message ?: e.javaClass.simpleName}. " +
          "Repair the installed platform packs before running validation.",
      )
    }
    if (manifests.isEmpty()) {
      return ValidationGateResolution.Absent(null)
    }
    val routing = ReviewStackRouting.route(
      manifests,
      changedPaths.map { ReviewRoutingChangedFile(it, "") },
    )
    val dominant = selectDominantPack(manifests, routing)
    val declaration = dominant?.validationGate
    return if (dominant != null && declaration != null) {
      ValidationGateResolution.Declared(dominant.slug, declaration)
    } else {
      ValidationGateResolution.Absent(dominant?.slug)
    }
  }

  private fun selectDominantPack(
    manifests: List<PlatformManifest>,
    routing: ReviewStackRoutingResult,
  ): PlatformManifest? {
    val bySlug = manifests.associateBy { it.slug }
    val routed = routing.routedSlugs.mapNotNull(bySlug::get)
    if (routed.isEmpty()) {
      return manifests.firstOrNull { it.validationGate != null } ?: manifests.firstOrNull()
    }
    val gated = routed.filter { it.validationGate != null }
    if (gated.isEmpty()) {
      return manifests.firstOrNull { it.validationGate != null } ?: routed.firstOrNull()
    }
    return gated.maxByOrNull { pack -> routing.ownedPathsBySlug[pack.slug]?.size ?: 0 }
  }
}
