package skillbill.ports.review.preparation
import skillbill.ports.review.model.ReviewLaneSelection
import skillbill.ports.review.model.ReviewScopeFacts
import skillbill.ports.review.model.ReviewStackRoutingFacts
import skillbill.review.context.model.hunk.ReviewBuildTestFact
import skillbill.review.context.model.hunk.ReviewLearningsReference
import skillbill.review.context.model.hunk.ReviewRuleReference

interface ReviewScopeResolverPort {
  fun resolveScope(reviewId: String): ReviewScopeFacts
}

interface ReviewStackRoutingPort {
  fun resolveStackRouting(scope: ReviewScopeFacts): ReviewStackRoutingFacts
}

interface ReviewGuidancePort {
  fun resolveMatchedRules(
    scope: ReviewScopeFacts,
    routing: ReviewStackRoutingFacts,
  ): List<ReviewRuleReference>
}

interface ReviewLearningsPort {
  fun resolveLearnings(
    scope: ReviewScopeFacts,
    routing: ReviewStackRoutingFacts,
  ): List<ReviewLearningsReference>
}

interface ReviewBuildTestFactsPort {
  fun resolveBuildTestFacts(scope: ReviewScopeFacts): List<ReviewBuildTestFact>
}

interface ReviewLaneSelectionPort {
  fun decideLanes(
    scope: ReviewScopeFacts,
    routing: ReviewStackRoutingFacts,
  ): ReviewLaneSelection
}
