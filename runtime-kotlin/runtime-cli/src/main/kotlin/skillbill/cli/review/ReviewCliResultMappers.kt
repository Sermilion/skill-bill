package skillbill.cli.review

import skillbill.application.review.model.FeatureTaskRuntimeStatsResult
import skillbill.application.review.model.FeatureVerifyStatsResult
import skillbill.application.review.model.GoalStatsResult
import skillbill.application.review.model.ImportedReviewResult
import skillbill.application.review.model.ReviewFeedbackResult
import skillbill.application.review.model.ReviewPreviewResult
import skillbill.application.review.model.ReviewStatsResult
import skillbill.application.review.model.TriageResult
import skillbill.application.review.service.toImportedReviewContract
import skillbill.application.review.service.toReviewFeedbackPayload
import skillbill.application.review.service.toReviewPreviewContract
import skillbill.application.review.service.toTriagePayload
import skillbill.application.review.stats.toFeatureTaskRuntimeStatsPayload
import skillbill.application.review.stats.toFeatureVerifyStatsPayload
import skillbill.application.review.stats.toGoalStatsPayload
import skillbill.application.review.stats.toReviewStatsPayload

internal fun ReviewPreviewResult.toCliMap(): Map<String, Any?> = toReviewPreviewContract().toPayload()

internal fun ImportedReviewResult.toCliMap(): Map<String, Any?> = toImportedReviewContract().toPayload()

internal fun ReviewFeedbackResult.toCliMap(): Map<String, Any?> = toReviewFeedbackPayload().toPayload()

internal fun TriageResult.toCliMap(): Map<String, Any?> = toTriagePayload().toPayload()

internal fun ReviewStatsResult.toCliMap(): Map<String, Any?> = toReviewStatsPayload().toPayload()

internal fun FeatureVerifyStatsResult.toCliMap(): Map<String, Any?> = toFeatureVerifyStatsPayload().toPayload()

internal fun FeatureTaskRuntimeStatsResult.toCliMap(): Map<String, Any?> =
  toFeatureTaskRuntimeStatsPayload().toPayload()

internal fun GoalStatsResult.toCliMap(): Map<String, Any?> = toGoalStatsPayload().toPayload()
