package skillbill.ports.experiment.pair

import skillbill.ports.experiment.pair.model.ExperimentStatsPayload

interface ExperimentPairReportPort {
  fun renderReport(pairId: String, format: String): String

  fun statsPayload(): ExperimentStatsPayload
}
