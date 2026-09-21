package skillbill.ports.experiment.publication.model

data class ExperimentPublicationAttempt(
  val pairId: String,
  val armId: String,
  val workflowId: String,
  val commitSha: String,
)
