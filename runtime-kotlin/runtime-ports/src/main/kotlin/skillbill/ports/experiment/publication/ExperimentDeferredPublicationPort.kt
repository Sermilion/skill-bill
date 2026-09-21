package skillbill.ports.experiment.publication

import skillbill.ports.experiment.publication.model.ExperimentParentDeliveryRequest as ExperimentParentDeliveryRequestModel
import skillbill.ports.experiment.publication.model.ExperimentPublicationAttempt as ExperimentPublicationAttemptModel
import skillbill.ports.experiment.publication.model.ExperimentPublicationResult as ExperimentPublicationResultModel

typealias ExperimentParentDeliveryRequest = ExperimentParentDeliveryRequestModel
typealias ExperimentPublicationAttempt = ExperimentPublicationAttemptModel
typealias ExperimentPublicationResult = ExperimentPublicationResultModel

interface ExperimentDeferredPublicationPort {
  fun recordDeferredAttempt(attempt: ExperimentPublicationAttempt)
  fun parentMayPublish(pairId: String): Boolean
  fun publicationRecorded(pairId: String, commitSha: String): Boolean = false
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
