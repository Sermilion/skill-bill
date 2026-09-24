package skillbill.ports.experiment.pair.model

data class ExperimentStatsPayload(
  val json: String,
) {
  init {
    require(json.isNotBlank()) { "ExperimentStatsPayload.json is required." }
  }
}
