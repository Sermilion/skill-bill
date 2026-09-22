package skillbill.engine.goalrunner.experiment

import skillbill.experiment.model.ExperimentArmId
import skillbill.ports.experiment.publication.ExperimentDeferredPublicationPort
import skillbill.ports.experiment.publication.ExperimentParentDeliveryPort
import skillbill.ports.experiment.publication.ExperimentParentDeliveryRequest
import skillbill.ports.experiment.publication.ExperimentPublicationAttempt
import skillbill.ports.experiment.publication.ExperimentPublicationGateway
import skillbill.ports.experiment.publication.ExperimentPublicationResult

class ExperimentParentDeliveryReconciler(
  private val deferredPublication: ExperimentDeferredPublicationPort,
  private val publicationGateway: ExperimentPublicationGateway,
) : ExperimentParentDeliveryPort {
  override fun reconcile(
    pairId: String,
    controlWorkflowId: String,
    controlCommitSha: String?,
    controlCompleted: Boolean,
    request: ExperimentParentDeliveryRequest,
  ): ExperimentPublicationResult {
    if (!controlCompleted || controlCommitSha.isNullOrBlank()) {
      return ExperimentPublicationResult(published = false)
    }
    if (!deferredPublication.parentMayPublish(pairId)) {
      return ExperimentPublicationResult(published = false)
    }
    if (deferredPublication.publicationRecorded(pairId, controlCommitSha)) {
      return ExperimentPublicationResult(published = true, alreadyPublished = true)
    }
    val attempt =
      ExperimentPublicationAttempt(
        pairId = pairId,
        armId = ExperimentArmId.CONTROL.wireValue,
        workflowId = controlWorkflowId,
        commitSha = controlCommitSha,
      )
    deferredPublication.recordDeferredAttempt(attempt)
    return publicationGateway.publish(attempt)
  }
}
