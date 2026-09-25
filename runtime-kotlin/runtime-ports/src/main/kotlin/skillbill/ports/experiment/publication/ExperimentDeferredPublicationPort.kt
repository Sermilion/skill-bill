package skillbill.ports.experiment.publication

import skillbill.ports.experiment.publication.model.ExperimentParentDeliveryRequest
import skillbill.ports.experiment.publication.model.ExperimentPublicationAttempt
import skillbill.ports.experiment.publication.model.ExperimentPublicationResult

interface ExperimentDeferredPublicationPort {
  fun recordDeferredAttempt(attempt: ExperimentPublicationAttempt)

  fun parentMayPublish(pairId: String): Boolean

  fun publicationRecorded(
    pairId: String,
    commitSha: String,
  ): Boolean
}

fun interface ExperimentParentDeliveryPort {
  fun reconcile(
    pairId: String,
    controlWorkflowId: String,
    controlCommitSha: String?,
    controlCompleted: Boolean,
    request: ExperimentParentDeliveryRequest,
  ): ExperimentPublicationResult
}

fun interface ExperimentPublicationGateway {
  fun publish(attempt: ExperimentPublicationAttempt): ExperimentPublicationResult
}
