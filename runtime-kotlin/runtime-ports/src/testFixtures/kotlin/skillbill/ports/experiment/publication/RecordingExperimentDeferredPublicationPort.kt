package skillbill.ports.experiment.publication

import skillbill.ports.experiment.publication.model.ExperimentPublicationAttempt

class RecordingExperimentDeferredPublicationPort : ExperimentDeferredPublicationPort {
  val attempts = mutableListOf<ExperimentPublicationAttempt>()
  var allowParentPublish = true

  override fun recordDeferredAttempt(attempt: ExperimentPublicationAttempt) {
    attempts.add(attempt)
  }

  override fun parentMayPublish(pairId: String): Boolean = allowParentPublish

  override fun publicationRecorded(
    pairId: String,
    commitSha: String,
  ): Boolean = false
}
