package skillbill.ports.experiment.pair.model

data class ExperimentObservationImport(
  val observationId: String,
  val pairId: String,
  val armId: String,
  val eventIdentityJson: String,
  val payloadJson: String,
  val recordedAt: String,
)
