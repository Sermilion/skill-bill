package skillbill.engine.experiment.report

import skillbill.contracts.experiment.ExperimentReportPayloadKeys
import skillbill.contracts.experiment.ExperimentStatsPayloadKeys
import kotlin.test.Test
import kotlin.test.assertEquals

class ExperimentStatsProjectorTest {
  @Test
  fun `stats keep goal and navigation cohorts separate and retain exclusions`() {
    val stats = ExperimentStatsProjector.project(
      listOf(
        report("goal", "complete", metricComparison = metricComparison(20.0)),
        report("goal", "degraded", listOf("treatment failed")),
        report("navigation", "complete"),
      ),
    )

    val goal = stats[ExperimentStatsPayloadKeys.GOAL] as Map<*, *>
    val navigation = stats[ExperimentStatsPayloadKeys.NAVIGATION] as Map<*, *>
    assertEquals(2, goal[ExperimentStatsPayloadKeys.TOTAL_PAIRS])
    assertEquals(1, goal[ExperimentStatsPayloadKeys.COMPLETE_PAIRS])
    assertEquals(1, goal[ExperimentStatsPayloadKeys.EXCLUDED_PAIRS])
    assertEquals(listOf("treatment failed"), goal[ExperimentStatsPayloadKeys.EXCLUSION_REASONS])
    val metrics = goal[ExperimentStatsPayloadKeys.METRIC_COMPARISONS] as List<*>
    assertEquals(20.0, (metrics.single() as Map<*, *>)[ExperimentStatsPayloadKeys.AVERAGE_ABSOLUTE_SAVINGS])
    assertEquals(1, navigation[ExperimentStatsPayloadKeys.COMPLETE_PAIRS])
    assertEquals(0, navigation[ExperimentStatsPayloadKeys.EXCLUDED_PAIRS])
  }

  @Test
  fun `stats aggregate measured spend percent savings and quality only from complete pairs`() {
    val stats = ExperimentStatsProjector.project(
      listOf(
        mapOf(
          ExperimentReportPayloadKeys.COHORT to "goal",
          ExperimentReportPayloadKeys.COMPLETENESS to "complete",
          ExperimentReportPayloadKeys.TOTAL_EXPERIMENT_SPEND to mapOf(
            ExperimentReportPayloadKeys.AVAILABILITY to "measured",
            ExperimentReportPayloadKeys.AMOUNT to 30.0,
          ),
          ExperimentReportPayloadKeys.METRIC_COMPARISONS to listOf(
            mapOf(
              ExperimentReportPayloadKeys.METRIC_ID to "cost",
              ExperimentReportPayloadKeys.ABSOLUTE_SAVINGS to mapOf(
                ExperimentReportPayloadKeys.QUANTITY to 20.0,
              ),
              ExperimentReportPayloadKeys.PERCENT_SAVINGS to mapOf(
                ExperimentReportPayloadKeys.QUANTITY to 20.0,
              ),
            ),
          ),
          ExperimentReportPayloadKeys.ARM_SUMMARIES to listOf(
            mapOf(
              ExperimentReportPayloadKeys.LABELLED_CRITERIA to 2,
              ExperimentReportPayloadKeys.TOTAL_CRITERIA to 3,
              ExperimentReportPayloadKeys.PRECISION_AVAILABLE to true,
              ExperimentReportPayloadKeys.RESTRICTED_BASELINE to true,
            ),
          ),
        ),
      ),
    )

    val goal = stats[ExperimentStatsPayloadKeys.GOAL] as Map<*, *>
    assertEquals(
      30.0,
      (goal[ExperimentStatsPayloadKeys.TOTAL_EXPERIMENT_SPEND] as Map<*, *>)[
        ExperimentReportPayloadKeys.AMOUNT,
      ],
    )
    val metric = (goal[ExperimentStatsPayloadKeys.METRIC_COMPARISONS] as List<*>).single() as Map<*, *>
    assertEquals(20.0, metric[ExperimentStatsPayloadKeys.AVERAGE_PERCENT_SAVINGS])
    val quality = goal[ExperimentStatsPayloadKeys.QUALITY] as Map<*, *>
    assertEquals(2, quality[ExperimentStatsPayloadKeys.LABELLED_CRITERIA])
    assertEquals(1, quality[ExperimentStatsPayloadKeys.PRECISION_AVAILABLE_PAIRS])
    assertEquals(1, quality[ExperimentStatsPayloadKeys.RESTRICTED_BASELINE_PAIRS])
  }

  private fun report(
    cohort: String,
    completeness: String,
    exclusionReasons: List<String> = emptyList(),
    metricComparison: Map<String, Any?>? = null,
  ): Map<String, Any?> = mapOf(
    ExperimentReportPayloadKeys.COHORT to cohort,
    ExperimentReportPayloadKeys.COMPLETENESS to completeness,
    ExperimentReportPayloadKeys.EXCLUSION_REASONS to exclusionReasons,
  ).plus(
    metricComparison?.let {
      mapOf(ExperimentReportPayloadKeys.METRIC_COMPARISONS to listOf(it))
    } ?: emptyMap(),
  )

  private fun metricComparison(savings: Double): Map<String, Any?> = mapOf(
    ExperimentReportPayloadKeys.METRIC_ID to "cost",
    ExperimentReportPayloadKeys.ABSOLUTE_SAVINGS to mapOf(
      ExperimentReportPayloadKeys.QUANTITY to savings,
    ),
  )
}
