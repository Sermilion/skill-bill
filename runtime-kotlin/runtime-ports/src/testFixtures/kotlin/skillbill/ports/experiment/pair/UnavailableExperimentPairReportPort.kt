package skillbill.ports.experiment.pair

import skillbill.ports.experiment.pair.model.ExperimentStatsPayload

class UnavailableExperimentPairReportPort : ExperimentPairReportPort {
  override fun renderReport(
    pairId: String,
    format: String,
  ): String = error("ExperimentPairReportPort is unavailable in this test harness.")

  override fun statsPayload(): ExperimentStatsPayload =
    error("ExperimentPairReportPort is unavailable in this test harness.")
}
