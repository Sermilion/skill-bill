package skillbill.application.review.service
import skillbill.application.review.parallel.core.code.review.bundled.review
import skillbill.application.review.parallel.core.code.review.bundled.service
import skillbill.application.review.parallel.core.code.review.end.reviewRunId
import skillbill.application.review.parallel.core.code.review.runner.reviewRunId
import skillbill.application.review.parallel.core.review.error
import skillbill.application.review.parallel.core.review.service
import skillbill.application.review.parallel.planning.reviewRunId
import skillbill.application.review.parallel.verification.error
import skillbill.application.review.parallel.verification.reviewRunId
import skillbill.application.review.preparation.error
import skillbill.application.review.preparation.service
import skillbill.application.review.review.diagnostics
import skillbill.application.review.review.resolve
import skillbill.application.review.spec.diagnostics
import skillbill.application.review.spec.error
import skillbill.application.review.spec.resolve
import skillbill.application.review.verification.diagnostics
import skillbill.error.shellcontent.ShellContentContractException
import skillbill.ports.diagnostics.RuntimeDiagnostics
import skillbill.ports.review.preparation.ReviewAttributionPort
import skillbill.review.attribution.canonicalPackSkillNames
import skillbill.review.attribution.canonicalPlatformSlugs
import skillbill.review.attribution.packSlugFromCanonicalPackSkillName
import skillbill.review.model.ImportedReview
import skillbill.review.model.ReviewRunLane
import skillbill.review.parsing.ReviewRunLaneResolver
import skillbill.review.plan.model.ReviewLaunchPlan

internal fun composedRunLanes(
  review: ImportedReview,
  reviewAttributionPort: ReviewAttributionPort,
  diagnostics: RuntimeDiagnostics,
): List<ReviewRunLane> {
  val routedPackSlug = packSlugFromCanonicalPackSkillName(review.routedSkillCanonical)
  if (routedPackSlug == null) {
    diagnostics.warning(
      "review lane composition: routed skill '${review.routedSkillCanonical}' names no platform pack; " +
        "run ${review.reviewRunId} imports with unresolved lanes.",
    )
    return ReviewRunLaneResolver.resolve(
      ReviewLaunchPlan(review.routedSkillCanonical, emptyList()),
      review.specialistReviews,
    )
  }
  val plan = try {
    reviewAttributionPort.composedLaunchPlan(routedPackSlug)
  } catch (error: ShellContentContractException) {
    diagnostics.warning(
      "review lane composition: pack '$routedPackSlug' failed to compose " +
        "(${error::class.simpleName}); run ${review.reviewRunId} imports with unresolved lanes " +
        "instead of the composed launch plan.",
      error,
    )
    ReviewLaunchPlan(routedPackSlug, emptyList())
  }
  return ReviewRunLaneResolver.resolve(plan, review.specialistReviews)
}

internal fun canonicalAttributionPorts(reviewAttributionPort: ReviewAttributionPort) =
  reviewAttributionPort.knownPackSkillNames() + canonicalPackSkillNames to
    reviewAttributionPort.knownPlatformSlugs() + canonicalPlatformSlugs
