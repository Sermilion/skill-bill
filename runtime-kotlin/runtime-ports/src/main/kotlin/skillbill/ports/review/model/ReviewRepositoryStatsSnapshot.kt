package skillbill.ports.review.model

import skillbill.review.model.ReviewFindingStats
import skillbill.review.model.ReviewHealthStats
import skillbill.review.model.ReviewLaneEffectivenessRow
import skillbill.review.model.ReviewStageMetrics

data class ReviewRepositoryStatsSnapshot(
  val reviewRunId: String?,
  val stats: ReviewFindingStats,
  val health: ReviewHealthStats,
  val laneEffectiveness: List<ReviewLaneEffectivenessRow> = emptyList(),
  val stageMetrics: ReviewStageMetrics? = null,
  val stageMetricsByTier: Map<String, ReviewStageMetrics> = emptyMap(),
)
