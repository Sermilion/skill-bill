package skillbill.mcp.shared

import skillbill.application.review.model.FeatureVerifyStatsResult
import skillbill.application.review.model.GoalStatsResult
import skillbill.application.review.model.ImportedReviewResult
import skillbill.application.review.model.ReviewStatsResult
import skillbill.application.review.model.TriageResult
import skillbill.application.review.service.toImportedReviewContract
import skillbill.application.review.service.toTriagePayload
import skillbill.application.review.stats.toFeatureVerifyStatsPayload
import skillbill.application.review.stats.toGoalStatsPayload
import skillbill.application.review.stats.toReviewStatsPayload
internal fun ImportedReviewResult.toMcpMap(): Map<String, Any?> = toImportedReviewContract().toPayload()

internal fun TriageResult.toMcpMap(): Map<String, Any?> = toTriagePayload().toPayload()

internal fun ReviewStatsResult.toMcpMap(): Map<String, Any?> = toReviewStatsPayload().toPayload()

internal fun FeatureVerifyStatsResult.toMcpMap(): Map<String, Any?> = toFeatureVerifyStatsPayload().toPayload()

internal fun GoalStatsResult.toMcpMap(): Map<String, Any?> = toGoalStatsPayload().toPayload()
