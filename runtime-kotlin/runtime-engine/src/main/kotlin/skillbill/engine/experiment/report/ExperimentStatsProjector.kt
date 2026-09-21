package skillbill.engine.experiment.report

import skillbill.contracts.experiment.ExperimentReportPayloadKeys
import skillbill.contracts.experiment.ExperimentStatsPayloadKeys
import skillbill.contracts.telemetry.TelemetryMeasurementAvailability

object ExperimentStatsProjector {
  fun project(reports: List<Map<String, Any?>>): Map<String, Any?> = linkedMapOf(
    ExperimentStatsPayloadKeys.GOAL to cohort(reports, ExperimentReportPayloadKeys.GOAL_COHORT),
    ExperimentStatsPayloadKeys.NAVIGATION to cohort(reports, ExperimentReportPayloadKeys.NAVIGATION_COHORT),
  )

  private fun cohort(reports: List<Map<String, Any?>>, cohort: String): Map<String, Any?> {
    val matching = reports.filter { it[ExperimentReportPayloadKeys.COHORT] == cohort }
    val complete = matching.filter {
      it[ExperimentReportPayloadKeys.COMPLETENESS] == "complete"
    }
    val excluded = matching - complete.toSet()
    val reasons = excluded.flatMap { report ->
      (report[ExperimentReportPayloadKeys.EXCLUSION_REASONS] as? List<*>)
        .orEmpty()
        .map { it.toString() }
    }.distinct().sorted()
    return linkedMapOf(
      ExperimentStatsPayloadKeys.TOTAL_PAIRS to matching.size,
      ExperimentStatsPayloadKeys.COMPLETE_PAIRS to complete.size,
      ExperimentStatsPayloadKeys.EXCLUDED_PAIRS to excluded.size,
      ExperimentStatsPayloadKeys.EXCLUSION_REASONS to reasons,
      ExperimentStatsPayloadKeys.METRIC_COMPARISONS to aggregateMetrics(complete),
      ExperimentStatsPayloadKeys.TOTAL_EXPERIMENT_SPEND to totalSpend(complete),
      ExperimentStatsPayloadKeys.QUALITY to quality(complete),
    )
  }

  private fun aggregateMetrics(reports: List<Map<String, Any?>>): List<Map<String, Any?>> = reports
    .flatMap { report ->
      (report[ExperimentReportPayloadKeys.METRIC_COMPARISONS] as? List<*>)
        .orEmpty()
        .filterIsInstance<Map<*, *>>()
    }
    .groupBy { it[ExperimentReportPayloadKeys.METRIC_ID]?.toString().orEmpty() }
    .filterKeys(String::isNotBlank)
    .map { (metricId, comparisons) ->
      val savings = comparisons.mapNotNull { comparison ->
        (comparison[ExperimentReportPayloadKeys.ABSOLUTE_SAVINGS] as? Map<*, *>)
          ?.get(ExperimentReportPayloadKeys.QUANTITY) as? Number
      }
      linkedMapOf<String, Any?>(
        ExperimentStatsPayloadKeys.METRIC_ID to metricId,
        ExperimentStatsPayloadKeys.PAIR_COUNT to comparisons.size,
        ExperimentStatsPayloadKeys.AVERAGE_ABSOLUTE_SAVINGS to
          savings.takeIf { it.isNotEmpty() }?.map(Number::toDouble)?.average(),
        ExperimentStatsPayloadKeys.AVERAGE_PERCENT_SAVINGS to comparisons.mapNotNull { comparison ->
          (
            (comparison[ExperimentReportPayloadKeys.PERCENT_SAVINGS] as? Map<*, *>)
              ?.get(ExperimentReportPayloadKeys.QUANTITY) as? Number
            )?.toDouble()
        }.takeIf { it.isNotEmpty() }?.average(),
      ).filterValues { it != null }
    }
    .sortedBy { it[ExperimentStatsPayloadKeys.METRIC_ID].toString() }

  private fun totalSpend(reports: List<Map<String, Any?>>): Map<String, Any?> {
    val values = reports.mapNotNull { report ->
      report[ExperimentReportPayloadKeys.TOTAL_EXPERIMENT_SPEND] as? Map<*, *>
    }
    val unavailable = values.firstOrNull { value ->
      value[ExperimentReportPayloadKeys.AVAILABILITY] != TelemetryMeasurementAvailability.MEASURED.wireValue ||
        value[ExperimentReportPayloadKeys.AMOUNT] !is Number
    }
    if (unavailable != null || values.isEmpty()) {
      return linkedMapOf<String, Any?>(
        ExperimentReportPayloadKeys.AVAILABILITY to
          (
            unavailable?.get(ExperimentReportPayloadKeys.AVAILABILITY)?.toString()
              ?: TelemetryMeasurementAvailability.UNAVAILABLE_INCOMPLETE.wireValue
            ),
        ExperimentReportPayloadKeys.REASON to
          (
            unavailable?.get(ExperimentReportPayloadKeys.REASON)?.toString()
              ?: "no comparable spend measurement"
            ),
      )
    }
    return mapOf(
      ExperimentReportPayloadKeys.AVAILABILITY to TelemetryMeasurementAvailability.MEASURED.wireValue,
      ExperimentReportPayloadKeys.AMOUNT to values.sumOf {
        (it[ExperimentReportPayloadKeys.AMOUNT] as Number).toDouble()
      },
    )
  }

  private fun quality(reports: List<Map<String, Any?>>): Map<String, Any?> {
    val arms = reports.flatMap { report ->
      (report[ExperimentReportPayloadKeys.ARM_SUMMARIES] as? List<*>)
        .orEmpty()
        .filterIsInstance<Map<*, *>>()
    }
    return mapOf(
      ExperimentStatsPayloadKeys.LABELLED_CRITERIA to arms.sumOf {
        (it[ExperimentReportPayloadKeys.LABELLED_CRITERIA] as? Number)?.toInt() ?: 0
      },
      ExperimentStatsPayloadKeys.TOTAL_CRITERIA to arms.sumOf {
        (it[ExperimentReportPayloadKeys.TOTAL_CRITERIA] as? Number)?.toInt() ?: 0
      },
      ExperimentStatsPayloadKeys.PRECISION_AVAILABLE_PAIRS to arms.count {
        it[ExperimentReportPayloadKeys.PRECISION_AVAILABLE] == true
      },
      ExperimentStatsPayloadKeys.RESTRICTED_BASELINE_PAIRS to arms.count {
        it[ExperimentReportPayloadKeys.RESTRICTED_BASELINE] == true
      },
    )
  }
}
