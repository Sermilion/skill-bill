package skillbill.application.review

import skillbill.review.context.model.SpecIntentProjection

fun SpecIntentProjection.toProjectionPayload(): SpecIntentProjectionWireMap = SpecIntentProjectionWireMap.from(
  linkedMapOf(
    "intended_outcome" to intendedOutcome,
    "acceptance_criteria" to acceptanceCriteria,
    "constraints" to constraints,
    "non_goals" to nonGoals,
    "deferred_items" to deferredItems,
    "provenance" to linkedMapOf(
      "spec_path" to provenance.specPath,
      "content_digest" to provenance.contentDigest,
    ),
    "declared_byte_budget" to declaredByteBudget,
  ),
)
