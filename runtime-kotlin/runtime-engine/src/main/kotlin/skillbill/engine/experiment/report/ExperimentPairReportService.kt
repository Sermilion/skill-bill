package skillbill.engine.experiment.report

import me.tatarka.inject.annotations.Inject
import skillbill.contracts.JsonCodec
import skillbill.contracts.experiment.ExperimentStatsPayloadKeys
import skillbill.ports.experiment.pair.ExperimentPairOwnerPort
import skillbill.ports.experiment.pair.ExperimentPairPayload
import skillbill.ports.experiment.pair.ExperimentPairReportPort

@Inject
class ExperimentPairReportService(
  private val pairOwner: ExperimentPairOwnerPort,
) : ExperimentPairReportPort {
  override fun renderReport(pairId: String, format: String): String {
    val state = pairOwner.load(pairId) ?: error("unknown pair $pairId")
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

  override fun renderStatsLine(): String {
    val reports = pairOwner.listReports().map { it.toMap() }
    val stats = ExperimentStatsProjector.project(reports)
    val goal = stats[ExperimentStatsPayloadKeys.GOAL]
    val navigation = stats[ExperimentStatsPayloadKeys.NAVIGATION]
    return "experiment stats: goal=$goal navigation=$navigation"
  }
}

private fun ExperimentPairPayload.toMap(): Map<String, Any?> =
  JsonCodec.anyToStringAnyMap(
    JsonCodec.jsonElementToValue(requireNotNull(JsonCodec.parseObjectOrNull(toJson()))),
  ) ?: error("Experiment pair payload must decode to an object.")
