package skillbill.engine.experiment.report

import me.tatarka.inject.annotations.Inject
import skillbill.contracts.JsonCodec
import skillbill.error.shellcontent.ExperimentNavigationPairUnavailableError
import skillbill.ports.experiment.pair.ExperimentPairOwnerPort
import skillbill.ports.experiment.pair.ExperimentPairPayload
import skillbill.ports.experiment.pair.ExperimentPairReportPort

@Inject
class ExperimentPairReportService(
  private val pairOwner: ExperimentPairOwnerPort,
) : ExperimentPairReportPort {
  override fun renderReport(pairId: String, format: String): String {
    val state = pairOwner.load(pairId) ?: throw ExperimentNavigationPairUnavailableError(pairId)
    val projection =
      ExperimentReportProjector.project(
        state.pairPayload.toMap(),
        state.executionMode.wireValue,
      ).also { pairOwner.saveReport(pairId, ExperimentPairPayload(it)) }
    return if (format == "json") {
      ExperimentReportProjector.renderJson(projection)
    } else {
      ExperimentReportProjector.renderText(projection)
    }
  }

  override fun statsPayload(): Map<String, Any?> {
    val reports = pairOwner.listReports().map { it.toMap() }
    return ExperimentStatsProjector.project(reports)
  }
}

private fun ExperimentPairPayload.toMap(): Map<String, Any?> =
  JsonCodec.anyToStringAnyMap(
    JsonCodec.jsonElementToValue(requireNotNull(JsonCodec.parseObjectOrNull(toJson()))),
  ) ?: error("Experiment pair payload must decode to an object.")
