package skillbill.ports.experiment.pair

class UnavailableExperimentPairReportPort : ExperimentPairReportPort {
  override fun renderReport(pairId: String, format: String): String =
    error("ExperimentPairReportPort is unavailable in this test harness.")

  override fun renderStatsLine(): String =
    error("ExperimentPairReportPort is unavailable in this test harness.")
}
