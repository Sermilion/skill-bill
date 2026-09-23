package skillbill.ports.experiment.pair

interface ExperimentPairReportPort {
  fun renderReport(pairId: String, format: String): String

  fun statsPayload(): Map<String, Any?>
}
