package skillbill.ports.experiment.publication.model

data class ExperimentPublicationResult(
  val published: Boolean,
  val alreadyPublished: Boolean = false,
  val reason: String? = null,
)
