package skillbill.ports.experiment.publication

class RecordingExperimentDeferredPublicationPort : ExperimentDeferredPublicationPort {
  val attempts = mutableListOf<ExperimentPublicationAttempt>()
  var allowParentPublish = true

  override fun recordDeferredAttempt(attempt: ExperimentPublicationAttempt) {
    attempts.add(attempt)
  }

  override fun parentMayPublish(pairId: String): Boolean = allowParentPublish
}
